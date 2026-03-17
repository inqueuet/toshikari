package com.valoser.toshikari

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.valoser.toshikari.cache.DetailCacheManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.net.MalformedURLException
import java.io.IOException
import java.net.URL
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import com.valoser.toshikari.worker.ThreadMonitorWorker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import com.valoser.toshikari.ui.detail.SearchState
import androidx.collection.LruCache
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * ダウンロード進捗を表すデータクラス
 */
data class DownloadProgress(
    val current: Int,
    val total: Int,
    val currentFileName: String? = null,
    val isActive: Boolean = true
) {
    val percentage: Int get() = if (total > 0) (current * 100 / total) else 0
}

data class DownloadConflictFile(
    val url: String,
    val fileName: String
)

data class DownloadConflictRequest(
    val requestId: Long,
    val totalCount: Int,
    val newCount: Int,
    val existingFiles: List<DownloadConflictFile>
)

/**
 * スレ詳細表示に関する状態と非同期処理を束ねる ViewModel。
 *
 * 機能概要:
 * - HTML を `DetailContent` 列（Text / Image / Video / ThreadEndTime）へパースし、NG 適用後のリストを `detailContent` で公開。
 * - キャッシュ戦略: `DetailCacheManager` と連携して生データをディスクへ保存しつつ、表示用は NG 適用済みを保持。スナップショットやアーカイブ再構成も活用。
 * - プロンプト永続化: 画像メタデータを段階的に抽出してキャッシュ/スナップショットへ反映し、EventStore 経由で UI へ伝搬する。
 * - 差分マージ: 再取得やバックグラウンド更新時に既存のプロンプトやレス状態をマージし、空値での上書きを避ける。
 * - 履歴・フォールバック: キャッシュ→スナップショット→アーカイブ復元の順でフォールバック読込を行い、404 検出時は履歴をアーカイブ扱いにして監視を停止する。
 * - レス番号抽出と「そうだね」: OP/返信双方のレス番号を正規化し、送信・取得結果をフローで UI に反映。
 * - 画像一括ダウンロード: 既存ファイル検出、競合解決ダイアログ、進捗共有を備えたダウンロードロジックを提供。
 * - メモリ保護: 利用状況に応じて NG フィルタキャッシュの縮小や Coil キャッシュのクリーンアップを行い、検索状態も Compose へ共有する。
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val networkClient: NetworkClient,
    private val metadataCache: MetadataCache,
    private val cacheManager: DetailCacheManager,
) : ViewModel() {

    // Event-Sourcing アーキテクチャの中央イベントストア
    private val eventStore = DetailEventStore()
    val detailScreenState: StateFlow<DetailScreenState> = eventStore.currentState

    // 後方互換性のための表示用フロー（新アーキテクチャから算出）
    val detailContent: StateFlow<List<DetailContent>> = detailScreenState
        .map { state -> state.computeDisplayContent() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _displayContent = MutableStateFlow<List<DetailContent>>(emptyList())
    val displayContent: StateFlow<List<DetailContent>> = _displayContent.asStateFlow()

    // TTS音声読み上げマネージャー
    private val ttsManager = TtsManager(appContext)
    val ttsState: StateFlow<TtsManager.TtsState> = ttsManager.state
    val ttsCurrentResNum: StateFlow<String?> = ttsManager.currentResNum

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    val isLoading: StateFlow<Boolean> = detailScreenState
        .map { state -> state.uiState.isLoading }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** ダウンロード進捗を表すフロー。 */
    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    private val _downloadConflictRequests = MutableSharedFlow<DownloadConflictRequest>(extraBufferCapacity = 1)
    val downloadConflictRequests = _downloadConflictRequests.asSharedFlow()

    private val _promptLoadingIds = MutableStateFlow<Set<String>>(emptySet())
    val promptLoadingIds: StateFlow<Set<String>> = _promptLoadingIds.asStateFlow()

    /** スレッドアーカイブ進捗を表すフロー */
    private val _archiveProgress = MutableStateFlow<ThreadArchiveProgress?>(null)
    val archiveProgress: StateFlow<ThreadArchiveProgress?> = _archiveProgress.asStateFlow()

    /** スレッドアーカイバー */
    private val threadArchiver by lazy { ThreadArchiver(appContext, networkClient) }

    /** ダウンロード/アーカイブのキャンセル用Job */
    private var downloadJob: kotlinx.coroutines.Job? = null
    private var archiveJob: kotlinx.coroutines.Job? = null

    private val downloadRequestIdGenerator = AtomicLong(0)
    private val pendingDownloadMutex = Mutex()
    private val pendingDownloadRequests = mutableMapOf<Long, DetailPendingDownloadRequest<MediaSaver.ExistingMedia>>()

    // ダウンロード進捗の更新専用ロック（ViewModelインスタンス全体のロックを避ける）
    private val downloadProgressMutex = Mutex()

    // そうだねの更新通知用（resNum -> サーバ応答カウント）。UI側ではこれを受け取り表示を楽観上書き。
    private val _sodaneUpdate = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 1)
    val sodaneUpdate = _sodaneUpdate.asSharedFlow()

    // 「そうだね」送信（UIからはレス番号のみ渡す）。
    // 参照（Referer）は現在のスレURL（currentUrl）を使用し、成功時は更新通知でUIを反映。
    // resNum は parse 時に DetailContent.Text.resNum として保持しており、UI 側での行内パースが難しい場合のフォールバックに利用可能。

    private var currentUrl: String? = null
    // NG フィルタ適用前の生コンテンツを保持
    private var rawContent: List<DetailContent> = emptyList()
    private val rawContentMutex = Mutex()
    private suspend fun setRawContent(list: List<DetailContent>) {
        rawContentMutex.withLock {
            rawContent = list
        }
    }

    private suspend inline fun updateRawContent(transform: (List<DetailContent>) -> List<DetailContent>): List<DetailContent> {
        rawContentMutex.withLock {
            val updated = transform(rawContent)
            rawContent = updated
            return updated
        }
    }
    private val ngStore by lazy { NgStore(appContext) }

    // NGフィルタ結果のキャッシュ（動的サイズ調整）
    private val ngFilterCache = LruCache<Pair<List<DetailContent>, List<NgRule>>, List<DetailContent>>(
        calculateOptimalCacheSize()
    )

    // 適応的メモリ監視
    private var lastMemoryCheck = 0L
    private var memoryCheckIntervalMs = 30000L // 初期値30秒、使用率に応じて調整
    private var consecutiveHighMemoryCount = 0

    // データ整合性管理用のアトミックカウンタ
    private val contentUpdateCounter = AtomicLong(0)
    private val metadataUpdateCounter = AtomicInteger(0)

    init {
        viewModelScope.launch {
            detailScreenState.collect { state ->
                val errorMessage = state.uiState.error
                if (_error.value != errorMessage) {
                    _error.value = errorMessage
                }
            }
        }
    }

    /** デバイスメモリに基づいた最適なキャッシュサイズを計算 */
    private fun calculateOptimalCacheSize(): Int {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        // 最大メモリの1%をキャッシュに割り当て、最小20、最大200
        return ((maxMemory / 1024 / 1024 / 100).toInt()).coerceIn(20, 200)
    }

    /** NGフィルタ結果キャッシュをクリアする（NGルール変更やメモリ圧などのトリガーで使用）。 */
    private fun clearNgFilterCache() {
        ngFilterCache.evictAll()
    }

    /**
     * 適応的メモリ使用量監視の改善
     * - メモリ使用率に応じて監視間隔を動的調整
     * - 高負荷時はキャッシュサイズも縮小
     * - 段階的なクリーンアップ処理
     */
    private fun checkMemoryUsage() {
        val now = System.currentTimeMillis()
        if (now - lastMemoryCheck < memoryCheckIntervalMs) return
        lastMemoryCheck = now

        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsageRatio = usedMemory.toFloat() / maxMemory.toFloat()

        val memoryUsagePercent = (memoryUsageRatio * 100).toInt()
        Log.d("DetailViewModel", "Memory usage: $memoryUsagePercent% (${usedMemory / 1024 / 1024}MB / ${maxMemory / 1024 / 1024}MB)")

        // 段階的メモリ管理（統合版）
        when {
            memoryUsageRatio > 0.90f -> {
                // 極度の高負荷：即座にアグレッシブクリーンアップ
                Log.w("DetailViewModel", "Extreme memory usage ($memoryUsagePercent%), performing aggressive cleanup")
                consecutiveHighMemoryCount++

                clearNgFilterCache()
                _plainTextCache.value = emptyMap()
                MyApplication.clearCoilImageCache(appContext)
                memoryCheckIntervalMs = 5000L

                // カウントが際限なく増加しないよう上限でリセット
                if (consecutiveHighMemoryCount >= 3) {
                    Log.d("DetailViewModel", "Resetting high memory counter after aggressive cleanup")
                    consecutiveHighMemoryCount = 0
                }
            }
            memoryUsageRatio > 0.85f -> {
                // 高負荷：アグレッシブクリーンアップ（GCは実行しない）
                Log.w("DetailViewModel", "Critical memory usage ($memoryUsagePercent%), performing aggressive cleanup")
                consecutiveHighMemoryCount++

                clearNgFilterCache()
                _plainTextCache.value = emptyMap()
                MyApplication.clearCoilImageCache(appContext)
                memoryCheckIntervalMs = 10000L

                // カウントが際限なく増加しないよう上限でリセット
                if (consecutiveHighMemoryCount >= 5) {
                    Log.d("DetailViewModel", "Resetting high memory counter to prevent overflow")
                    consecutiveHighMemoryCount = 0
                }
            }
            memoryUsageRatio > 0.75f -> {
                // 中高負荷：選択的クリーンアップ
                Log.w("DetailViewModel", "High memory usage ($memoryUsagePercent%), performing selective cleanup")
                consecutiveHighMemoryCount++

                clearNgFilterCache()
                val currentPlainCache = _plainTextCache.value
                if (currentPlainCache.size > 20) {
                    val reducedCache = currentPlainCache.toList().takeLast(10).toMap()
                    _plainTextCache.value = reducedCache
                }
                MyApplication.clearCoilImageCache(appContext)
                memoryCheckIntervalMs = 15000L

                // カウントが際限なく増加しないよう上限でリセット
                if (consecutiveHighMemoryCount >= 5) {
                    consecutiveHighMemoryCount = 0
                }
            }
            memoryUsageRatio > 0.70f -> {
                // 中程度の負荷：軽度のクリーンアップ
                consecutiveHighMemoryCount++
                memoryCheckIntervalMs = 20000L
                if (consecutiveHighMemoryCount >= 3) {
                    Log.w("DetailViewModel", "Sustained memory pressure, clearing image cache")
                    MyApplication.clearCoilImageCache(appContext)
                    clearNgFilterCache()
                    consecutiveHighMemoryCount = 0
                }
            }
            memoryUsageRatio > 0.60f -> {
                memoryCheckIntervalMs = 25000L
                consecutiveHighMemoryCount = 0
            }
            else -> {
                memoryCheckIntervalMs = 30000L
                consecutiveHighMemoryCount = 0
            }
        }
    }

    /**
     * メモリ使用量を即時に測定し、デバッグ表示用の概要文字列を返す。
     */
    fun forceMemoryCheck(): String {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsageRatio = usedMemory.toFloat() / maxMemory.toFloat()

        val coilInfo = MyApplication.getCoilCacheInfo(appContext)

        return "Memory: ${(memoryUsageRatio * 100).toInt()}% (${usedMemory / 1024 / 1024}MB / ${maxMemory / 1024 / 1024}MB)\nCoil: $coilInfo"
    }

    // ---- Search state (single source of truth) ----
    private var currentSearchQuery: String? = null
    private val _currentQueryFlow = MutableStateFlow<String?>(null)
    val currentQuery: StateFlow<String?> = _currentQueryFlow.asStateFlow()
    private val searchResultPositions = mutableListOf<Int>()
    private var currentSearchHitIndex = -1
    private val _searchState = MutableStateFlow(SearchState(active = false, currentIndexDisplay = 0, total = 0))
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    // そうだねの状態を保持するマップ (resNum -> そうだねが押されたかどうか)
    // メモリリーク防止のため、最大サイズを制限
    // synchronized ブロックでスレッドセーフを保証
    private val sodaNeStates = object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > 300 // 最大300件まで保持（メモリ使用量削減）
        }
    }

    // メタデータ抽出の並列数は実行時にユーザー設定を参照

    /**
     * 詳細を取得して表示を更新。
     *
     * ポリシー:
     * - まずキャッシュ/スナップショットがあれば即時表示（NG適用後）。その後、画像プロンプトを再抽出して段階反映。
     * - ネット再取得時は既存表示のプロンプトを新規リストへマージし、空で潰さないようにしてから表示更新。
     * - 例外時はキャッシュ → スナップショット → アーカイブ再構成の順で復元し、いずれの経路でも再抽出を走らせる。
     * - `forceRefresh=true` の場合は常に再取得。
     */
    fun fetchDetails(url: String, forceRefresh: Boolean = false) {
        Log.d("DetailViewModel", "fetchDetails: Called with forceRefresh: $forceRefresh for URL: $url")
        contentUpdateCounter.incrementAndGet()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                fetchDetailsWithEventStore(url, forceRefresh)
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error in fetchDetails: ${e.message}", e)
                eventStore.setError("詳細の取得に失敗しました: ${e.message}")
            }
        }
    }

    private suspend fun fetchDetailsWithEventStore(url: String, forceRefresh: Boolean) {
        eventStore.applyEvent(DetailEvent.LoadingStateChanged(true))
        currentUrl = url

        try {
            if (!forceRefresh) {
                val cachedDetails = withContext(Dispatchers.IO) { cacheManager.loadDetails(url) }
                if (cachedDetails != null) {
                    Log.d("DetailViewModel", "Loading from cache with new architecture: ${cachedDetails.size} items")
                    val sanitized = setRawContentSanitized(cachedDetails)
                    val merged = mergeWithExistingPrompts(sanitized)
                    setRawContent(merged)
                    eventStore.loadFromOldContent(merged, url)
                    applyNgAndPostAsync()
                    updateMetadataWithEventStore(merged, url)
                    return
                }

                val archived = runCatching {
                    HistoryManager.getAll(appContext).any { it.url == url && it.isArchived }
                }.getOrDefault(false)

                if (archived) {
                    val snapshot = withContext(Dispatchers.IO) { cacheManager.loadArchiveSnapshot(url) }
                    if (!snapshot.isNullOrEmpty()) {
                        Log.d("DetailViewModel", "Loading from archive snapshot with new architecture: ${snapshot.size} items")
                        val sanitized = setRawContentSanitized(snapshot)
                        val merged = mergeWithExistingPrompts(sanitized)
                        setRawContent(merged)
                        eventStore.loadFromOldContent(merged, url)
                        applyNgAndPostAsync()
                        updateMetadataWithEventStore(merged, url)
                        return
                    }
                }
            }

            val document = withContext(Dispatchers.IO) {
                networkClient.fetchDocument(url).apply {
                    outputSettings().prettyPrint(false)
                }
            }

            val parsedContent = parseContentFromDocument(document, url)
            val sanitized = setRawContentSanitized(parsedContent)
            val merged = mergeWithExistingPrompts(sanitized)
            setRawContent(merged)

            eventStore.loadFromOldContent(merged, url)

            withContext(Dispatchers.IO) { cacheManager.saveDetails(url, merged) }

            applyNgAndPostAsync()
            updateMetadataWithEventStore(merged, url)

        } catch (e: Exception) {
            Log.e("DetailViewModel", "Error fetching details for $url", e)
            eventStore.setError("詳細の取得に失敗しました: ${e.message}")

            val cached = withContext(Dispatchers.IO) { cacheManager.loadDetails(url) }
            if (cached != null) {
                if (e is IOException && (e.message?.contains("404") == true)) {
                    try {
                        HistoryManager.markArchived(appContext, url, autoExpireIfStale = true)
                        ThreadMonitorWorker.cancelByUrl(appContext, url)
                    } catch (markError: Exception) {
                        Log.w("DetailViewModel", "Failed to mark archived for $url", markError)
                    }
                }
                val sanitized = setRawContentSanitized(cached)
                val merged = mergeWithExistingPrompts(sanitized)
                setRawContent(merged)
                eventStore.loadFromOldContent(merged, url)
                applyNgAndPostAsync()
                updateMetadataWithEventStore(merged, url)
                eventStore.applyEvent(DetailEvent.ErrorSet(null))
            } else {
                val reconstructed = withContext(Dispatchers.IO) {
                    cacheManager.reconstructFromArchive(url)
                }
                if (!reconstructed.isNullOrEmpty()) {
                    val sanitized = setRawContentSanitized(reconstructed)
                    val merged = mergeWithExistingPrompts(sanitized)
                    setRawContent(merged)
                    eventStore.loadFromOldContent(merged, url)
                    applyNgAndPostAsync()
                    updateMetadataWithEventStore(merged, url)
                    eventStore.applyEvent(DetailEvent.ErrorSet(null))
                }
            }
        } finally {
            eventStore.applyEvent(DetailEvent.LoadingStateChanged(false))
        }
    }

    /** スレッドの差分更新をチェックし、新規アイテムがあれば追加して反映する。*/
    fun checkForUpdates(url: String, currentItemCount: Int, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                // 現在のHTMLを取得
                val document = withContext(Dispatchers.IO) {
                    networkClient.fetchDocument(url).apply {
                        outputSettings().prettyPrint(false)
                    }
                }

                // 新しいコンテンツをパース
                val newContentList = parseContentFromDocument(document, url)

                val diff = DetailContentDiffer.diff(
                    current = rawContent,
                    parsed = newContentList,
                    textBodyOf = ::extractPlainBodyOfTextContent
                )

                if (diff.duplicateIds.isNotEmpty()) {
                    Log.w("DetailViewModel", "checkForUpdates: Duplicate IDs found: ${diff.duplicateIds}")
                }
                if (diff.duplicateContentHashes.isNotEmpty()) {
                    Log.w(
                        "DetailViewModel",
                        "checkForUpdates: Duplicate content found: ${diff.duplicateContentHashes.size} groups"
                    )
                }

                Log.d(
                    "DetailViewModel",
                    "checkForUpdates: Current items=${rawContent.size}, New parsed=${newContentList.size}, New items=${diff.newItems.size}"
                )

                if (diff.newItems.isNotEmpty()) {
                    // 生データを更新してキャッシュ保存、表示はNG適用後
                    val sanitizedNewItems = sanitizePrompts(diff.newItems)
                    val updatedRaw = updateRawContent { it + sanitizedNewItems }
                    withContext(Dispatchers.IO) { cacheManager.saveDetails(url, updatedRaw) }

                    // 新しいアーキテクチャへ反映
                    eventStore.loadFromOldContent(updatedRaw, url)
                    applyNgAndPostAsync()
                    updateMetadataWithEventStore(sanitizedNewItems, url)
                    callback(true)
                } else {
                    callback(false)
                }

            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error checking for updates", e)
                callback(false)
            }
        }
    }

    private suspend fun parseContentFromDocument(document: Document, url: String): List<DetailContent> =
        withContext(Dispatchers.Default) {
        val progressivelyLoadedContent = mutableListOf<DetailContent>()
        val threadId = DetailHtmlParsingSupport.extractThreadId(url)
        // URL末尾を基準にスレIDを決定し、欠損時はURLハッシュで安定化

        val threadContainer = document.selectFirst("div.thre")

        if (threadContainer == null) {
            _error.postValue("スレッドのコンテナが見つかりませんでした。")
            Log.e("DetailViewModel", "div.thre container not found in document for URL: $url")
            return@withContext emptyList<DetailContent>()
        }

        // 処理対象となる全ての投稿（OP + 返信）をリストアップ
        val postBlocks = mutableListOf<Element>()
        postBlocks.add(threadContainer) // 最初の投稿(OP)としてコンテナ自体を追加

        // OPコンテナ内の返信テーブルを全て追加
        threadContainer.select("td.rtd")
            .mapNotNull { it.closest("table") }
            .distinct()
            .let { postBlocks.addAll(it) }

        // 全ての投稿をループ処理
        postBlocks.forEachIndexed { index, block ->
            val isOp = (index == 0) // 最初の要素がOP

            // --- 1. テキストコンテンツの解析 ---
            val html: String
            if (isOp) {
                // OPの場合、子要素の返信テーブルを除外して処理
                val originalHtml = block.html()
                // テーブルタグを除去（正規表現で効率的に処理）
                val withoutTables = originalHtml.replace(TABLE_REMOVAL_PATTERN, "")
                // 画像タグをaltテキストに置換
                html = withoutTables.replace(IMG_WITH_ALT_PATTERN) { match ->
                    val alt = match.groupValues[1].ifBlank { "img" }
                    "[$alt]"
                }.replace(IMG_PATTERN, "[img]")
            } else {
                // 返信の場合、.rtdセルからHTMLを取得
                val rtd = block.selectFirst(".rtd")
                if (rtd != null) {
                    val rawHtml = rtd.html()
                    // 画像タグをaltテキストに置換（正規表現で効率的に処理）
                    html = rawHtml.replace(IMG_WITH_ALT_PATTERN) { match ->
                        val alt = match.groupValues[1].ifBlank { "img" }
                        "[$alt]"
                    }.replace(IMG_PATTERN, "[img]")
                } else {
                    html = ""
                }
            }

            val resNum = DetailHtmlParsingSupport.extractResNum(html, isOp, threadId)
            // OPはスレID、返信は本文内の No. からレス番号を抽出

            if (html.isNotBlank()) {
                // レス番号ベースの安定ID（機能互換性を保持）
                val stableId = DetailHtmlParsingSupport.buildTextContentId(isOp, threadId, resNum, index)
                progressivelyLoadedContent.add(
                    DetailContent.Text(
                        id = stableId,
                        htmlContent = html,
                        resNum = resNum
                    )
                )
            }

            // --- 2. メディアコンテンツの解析 ---
            val mediaLinkNode = if (isOp) {
                // OPの場合、返信テーブル内の画像を除外してメディアリンクを検索
                // より厳密に：OPのコンテンツ内に直接含まれる画像のみを取得
                val cloned = block.clone()
                // 返信テーブル（td.rtd を含むtable）を完全に除去
                cloned.select("table:has(td.rtd)").remove()
                cloned.select("table").remove()  // 念のため他のテーブルも除去

                // OPのテキスト部分内で画像リンクを検索（より限定的に）
                val opMediaLinks = cloned.select("a[target=_blank][href]").filter { a ->
                    DetailHtmlParsingSupport.isMediaUrl(a.attr("href"))
                }

                // OPに画像がない場合はnullを返す（次の画像を取得しない）
                if (opMediaLinks.isEmpty()) {
                    Log.d("DetailViewModel", "OP has no images - returning null")
                    null
                } else {
                    Log.d("DetailViewModel", "OP has ${opMediaLinks.size} image(s) - using first one")
                    opMediaLinks.firstOrNull()
                }
            } else {
                // 返信の場合、通常通り検索
                block.select("a[target=_blank][href]").firstOrNull { a ->
                    DetailHtmlParsingSupport.isMediaUrl(a.attr("href"))
                }
            }

            // メディアコンテンツのID生成ではblockResNumを使わない（URL固定のため）

            if (mediaLinkNode != null) {
                val link = mediaLinkNode
                val hrefAttr = link.attr("href")
                try {
                    val absoluteUrl = URL(URL(url), hrefAttr).toString()
                    val thumbnailUrl = DetailHtmlParsingSupport.resolveThumbnailUrl(link, url, absoluteUrl)
                    val mediaContent = DetailHtmlParsingSupport.buildMediaContent(
                        absoluteUrl = absoluteUrl,
                        rawHref = hrefAttr,
                        thumbnailUrl = thumbnailUrl
                    )

                    if (mediaContent != null) {
                        progressivelyLoadedContent.add(mediaContent)
                    }
                } catch (e: MalformedURLException) {
                    Log.e(
                        "DetailViewModel",
                        "Skipping malformed media URL. Base: '$url', Href: '$hrefAttr'",
                        e
                    )
                }
            } else if (isOp) {
                // OPに画像がない場合は「画像なし」プレースホルダーを追加
                progressivelyLoadedContent.add(
                    DetailContent.Image(
                        // スレURLに基づく安定したプレースホルダーID
                        id = "no_image_op_${url.hashCode().toUInt().toString(16)}",
                        imageUrl = "", // 空のURLで「画像なし」を表現
                        prompt = null,
                        fileName = null
                    )
                )
                Log.d("DetailViewModel", "OP has no images - added placeholder")
            }
        }

        DetailHtmlParsingSupport.extractThreadEndTime(document)?.let {
            progressivelyLoadedContent.add(DetailHtmlParsingSupport.buildThreadEndTimeContent(it))
        }

        return@withContext progressivelyLoadedContent.toList()
    }

    /**
     * 画像メタデータ（主にプロンプト/説明）をバックグラウンドで抽出し、EventStore に反映する。
     *
     * 挙動:
     * - 画像ごとにキャッシュを確認し、未取得の場合のみ `MetadataExtractor.extract` を実行。
     * - 結果は EventStore を通じて InProgress → Completed/Failed へ遷移させる。
     * - 取得できたプロンプトはメタデータキャッシュと詳細キャッシュへ書き戻す。
     * - 動画は現在対象外（ログ出力のみ）。
     */
    private suspend fun updateMetadataInBackground(contentList: List<DetailContent>, url: String) {
        updateMetadataWithEventStore(contentList, url)
    }

    /**
     * 指定レス番号に「そうだね」を送信し、成功時は (resNum -> count) をUIへ通知。
     * UI 側ではこの通知を受けて楽観表示（＋/そうだね → そうだねxN）を行い、
     * 行内に No が見つからない場合も自投稿番号(selfResNum)でフォールバックして置換する。
     */
    fun postSodaNe(resNum: String) {
        val url = currentUrl
        if (url == null) {
            val message = "「そうだね」の投稿に失敗しました: 対象のURLが不明です。"
            _error.value = message
            viewModelScope.launch { eventStore.setError(message) }
            return
        }
        viewModelScope.launch {
            try {
                val count = networkClient.postSodaNe(resNum, url)
                if (count != null) {
                    // 成功: 次回以降押下を抑止
                    synchronized(sodaNeStates) {
                        sodaNeStates[resNum] = true
                    }
                    _sodaneUpdate.tryEmit(resNum to count)
                    eventStore.applyEvent(DetailEvent.ErrorSet(null))
                } else {
                    val message = "「そうだね」の投稿に失敗しました。"
                    _error.value = message
                    eventStore.setError(message)
                }
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Error in postSodaNe: ${e.message}", e)
                val message = "「そうだね」の投稿中にエラーが発生しました: ${e.message}"
                _error.value = message
                eventStore.setError(message)
            }
        }
    }

    /**
     * 指定レス番号の「そうだね」押下状態を返す。
     * 重複送信の抑止など、UI 側の制御に用いるフラグ。
     */
    fun getSodaNeState(resNum: String): Boolean {
        return synchronized(sodaNeStates) {
            sodaNeStates[resNum] ?: false
        }
    }

    /**
     * 現在保持している「そうだね」押下状態を全てクリアする。
     * ページ遷移や強制更新時に呼び出し、状態の持ち越しを防ぐ。
     */
    fun resetSodaNeStates() {
        synchronized(sodaNeStates) {
            sodaNeStates.clear()
        }
    }

    /**
     * 通常の削除（画像のみ/本文含む）を実行する。
     * 成功時はスレッドを強制再取得して表示を最新化する。
     */
    fun deletePost(postUrl: String, referer: String, resNum: String, pwd: String, onlyImage: Boolean) {
        viewModelScope.launch {
            try {
                // 新アーキテクチャでローディング状態を設定
                eventStore.applyEvent(DetailEvent.LoadingStateChanged(true))

                // 念のため直前にスレGETしてCookieを埋める（posttime等）
                withContext(Dispatchers.IO) { networkClient.fetchDocument(referer) }

                val ok = withContext(Dispatchers.IO) {
                    networkClient.deletePost(
                        postUrl = postUrl,
                        referer = referer,
                        resNum = resNum,
                        pwd = pwd,
                        onlyImage = onlyImage,
                    )
                }

                if (ok) {
                    // 成功したらスレ再取得（forceRefresh）
                    currentUrl?.let { fetchDetails(it, forceRefresh = true) }
                } else {
                    val errorMsg = "削除に失敗しました。削除キーが違う可能性があります。"
                    eventStore.setError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "削除中にエラーが発生しました: ${e.message}"
                eventStore.setError(errorMsg)
            } finally {
                eventStore.applyEvent(DetailEvent.LoadingStateChanged(false))
            }
        }
    }

    /**
     * del.php 経由の削除を実行する。
     * 成功時はスレッドを強制再取得して表示を最新化する。
     */
    fun deleteViaDelPhp(resNum: String, reason: String = "110") {
        viewModelScope.launch {
            try {
                val url = currentUrl ?: return@launch
                eventStore.applyEvent(DetailEvent.LoadingStateChanged(true))

                // 事前に参照スレをGETしてCookie類を確実に用意
                withContext(Dispatchers.IO) { networkClient.fetchDocument(url) }

                val ok = withContext(Dispatchers.IO) {
                    networkClient.deleteViaDelPhp(
                        threadUrl = url,
                        targetResNum = resNum,
                        reason = reason,
                    )
                }

                if (ok) {
                    // 成功したら最新状態を取得
                    fetchDetails(url, forceRefresh = true)
                } else {
                    val errorMsg = "del の実行に失敗しました。権限やCookieを確認してください。"
                    eventStore.setError(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "del 実行中にエラーが発生しました: ${e.message}"
                eventStore.setError(errorMsg)
            } finally {
                eventStore.applyEvent(DetailEvent.LoadingStateChanged(false))
            }
        }
    }

    // ===== 補助関数と正規表現 =====

    companion object {
        // プリコンパイル済み正規表現
        private val TABLE_REMOVAL_PATTERN = Regex("<table[^>]*>.*?</table>", RegexOption.DOT_MATCHES_ALL)
        private val IMG_WITH_ALT_PATTERN = Regex("<img[^>]*alt=[\"']([^\"']*)[\"'][^>]*>")
        private val IMG_PATTERN = Regex("<img[^>]*>")
    }

    // ===== NG フィルタリング =====

    /** 現在のNGルールでフィルタを再適用し、表示と検索状態を更新する。 */
    fun reapplyNgFilter() {
        clearNgFilterCache() // NGルール変更時はキャッシュをクリア
        viewModelScope.launch {
            applyNgAndPostAsync()
        }
    }

    /**
     * NGルールを適用した結果を `detailContent` に反映し、検索状態も更新する。
     * 併せて生データのキャッシュ保存と、表示状態のアーカイブスナップショット保存を行う。
     */
    private suspend fun applyNgAndPostAsync() {
        val rules = ngStore.cleanupAndGetRules()
        val source = rawContent

        val filtered = if (rules.isEmpty()) {
            source
        } else {
            withContext(Dispatchers.Default) { filterByNgRulesOptimized(source, rules) }
        }

        val hiddenIds = if (rules.isEmpty()) {
            emptySet()
        } else {
            val sourceIds = source.asSequence().map { it.id }.toSet()
            val filteredIds = filtered.asSequence().map { it.id }.toSet()
            sourceIds - filteredIds
        }

        val plainTextMap = computePlainTextMap(filtered)
        val displayPreferences = withContext(Dispatchers.Default) { loadDisplayFilterConfig() }
        val displayFiltered = withContext(Dispatchers.Default) {
            DetailDisplayFilter.apply(
                items = filtered,
                plainTextCache = plainTextMap,
                config = displayPreferences,
                plainTextOf = ::toPlainText
            )
        }
        val displayPlainText = withContext(Dispatchers.Default) {
            displayFiltered.asSequence()
                .filterIsInstance<DetailContent.Text>()
                .associate { text -> text.id to (plainTextMap[text.id] ?: toPlainText(text)) }
        }

        withContext(Dispatchers.Main) {
            _plainTextCache.value = displayPlainText
            _displayContent.value = displayFiltered
        }

        eventStore.applyEvent(DetailEvent.NgFilterApplied(rules, hiddenIds))

        recomputeSearchState(displayFiltered)

        currentUrl?.let { url ->
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { cacheManager.saveDetails(url, source) }
            }
        }
    }

    /**
     * 既存表示（prior）に含まれるプロンプト等を新規取得（base）へ引き継ぐ。
     * - Image/Video のプロンプト情報を積極的にマージ（既存のプロンプトも保持）。
     * - 照合キーは `fileName` 優先、無い場合は URL 末尾（ファイル名相当）、最後に完全URL。
     */
    private suspend fun mergeWithExistingPrompts(base: List<DetailContent>): List<DetailContent> {
        val current = detailContent.value
        if (current.isEmpty()) return base
        return mergePrompts(base, current)
    }

    private suspend fun mergePrompts(base: List<DetailContent>, prior: List<DetailContent>): List<DetailContent> {
        if (base.isEmpty() || prior.isEmpty()) return base
        return withContext(Dispatchers.Default) { DetailPromptMerger.merge(base, prior) }
    }

    /** NGルールに基づきテキストと直後のメディア列を順次評価して返す（skipping 状態を全体で共有するため並列化しない）。 */
    private suspend fun filterByNgRulesOptimized(src: List<DetailContent>, rules: List<NgRule>): List<DetailContent> {
        val cacheKey = src to rules
        ngFilterCache.get(cacheKey)?.let { return it }

        val result = DetailNgFilter.filter(
            items = src,
            rules = rules,
            idOf = ::extractIdFromTextContent,
            bodyOf = ::extractPlainBodyOfTextContent
        )

        ngFilterCache.put(cacheKey, result)
        return result
    }

    /** HTMLから ID: xxx を抽出。タグ境界とテキスト両方を考慮して安定化。 */
    private fun extractIdFromHtml(html: String): String? {
        return DetailTextParser.extractIdFromHtml(html) { rawHtml ->
            DetailPlainTextFormatter.fromHtml(rawHtml)
        }
    }

    private fun extractIdFromTextContent(text: DetailContent.Text): String? {
        return extractIdFromHtml(text.htmlContent)
    }

    private fun extractPlainBodyOfTextContent(text: DetailContent.Text): String {
        return DetailTextParser.extractPlainBodyFromPlain(plainTextOf(text))
    }

    private fun markPromptLoading(ids: Collection<String>, loading: Boolean) {
        if (ids.isEmpty()) return
        _promptLoadingIds.update { current ->
            if (loading) current + ids else current - ids
        }
    }

    private fun markPromptLoading(id: String, loading: Boolean) {
        markPromptLoading(listOf(id), loading)
    }

    private suspend fun sanitizePrompts(list: List<DetailContent>): List<DetailContent> {
        if (list.isEmpty()) return list
        return withContext(Dispatchers.Default) { DetailPromptSanitizer.sanitizeContents(list) }
    }

    private suspend fun setRawContentSanitized(list: List<DetailContent>): List<DetailContent> {
        val sanitized = sanitizePrompts(list)
        setRawContent(sanitized)
        return sanitized
    }

    private fun normalizePrompt(raw: String?): String? {
        return DetailPromptSanitizer.normalize(raw)
    }

    // ===== 検索: 公開APIと内部実装 =====
    /** 検索を開始し、最初のヒット位置に移動できるよう状態を更新。 */
    fun performSearch(query: String) {
        currentSearchQuery = query
        _currentQueryFlow.value = query
        searchResultPositions.clear()
        currentSearchHitIndex = -1
        recomputeSearchState()
        if (searchResultPositions.isNotEmpty()) {
            currentSearchHitIndex = 0
            publishSearchState()
        }
    }

    /** 検索状態をクリア。 */
    fun clearSearch() {
        val wasActive = currentSearchQuery != null
        currentSearchQuery = null
        _currentQueryFlow.value = null
        searchResultPositions.clear()
        currentSearchHitIndex = -1
        publishSearchState()
        if (wasActive) {
            // no-op placeholder for legacy callbacks
        }
    }

    /** 検索ヒットの前の項目へ循環移動。 */
    fun navigateToPrevHit() {
        if (searchResultPositions.isEmpty()) return
        currentSearchHitIndex--
        if (currentSearchHitIndex < 0) currentSearchHitIndex = searchResultPositions.size - 1
        publishSearchState()
    }

    /** 検索ヒットの次の項目へ循環移動。 */
    fun navigateToNextHit() {
        if (searchResultPositions.isEmpty()) return
        currentSearchHitIndex++
        if (currentSearchHitIndex >= searchResultPositions.size) currentSearchHitIndex = 0
        publishSearchState()
    }

    /** 現在の表示リストから検索ヒット位置を再計算して公開。 */
    private fun recomputeSearchState(contentOverride: List<DetailContent>? = null) {
        val q = currentSearchQuery?.trim().orEmpty()
        searchResultPositions.clear()
        if (q.isBlank()) {
            publishSearchState()
            return
        }
        val contentList = contentOverride ?: displayContent.value.ifEmpty { detailContent.value }
        viewModelScope.launch(Dispatchers.Default) {
            val hits = DetailSearchEngine.findHitPositions(q, contentList, ::plainTextOf)
            withContext(Dispatchers.Main) {
                searchResultPositions.clear()
                searchResultPositions.addAll(hits)
                if (hits.isNotEmpty() && currentSearchHitIndex !in hits.indices) {
                    currentSearchHitIndex = 0
                }
                publishSearchState()
            }
        }
    }

    // ===== プレーンテキストキャッシュ =====
    private val _plainTextCache = MutableStateFlow<Map<String, String>>(emptyMap())
    val plainTextCache: StateFlow<Map<String, String>> = _plainTextCache.asStateFlow()

    private fun toPlainText(t: DetailContent.Text): String {
        return DetailPlainTextFormatter.fromText(t)
    }

    private suspend fun computePlainTextMap(list: List<DetailContent>): Map<String, String> {
        return withContext(Dispatchers.Default) {
            list.asSequence()
                .filterIsInstance<DetailContent.Text>()
                .associate { t -> t.id to toPlainText(t) }
        }
    }

    fun ensurePlainTextCachedFor(contents: List<DetailContent>) {
        if (contents.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            val current = _plainTextCache.value
            val missing = contents.asSequence()
                .filterIsInstance<DetailContent.Text>()
                .filter { !current.containsKey(it.id) }
                .map { text -> text.id to toPlainText(text) }
                .toList()
            if (missing.isEmpty()) return@launch
            val updated = DetailPlainTextCachePolicy.addMissing(current, missing)
            if (updated != null) {
                withContext(Dispatchers.Main) { _plainTextCache.value = updated }
            }
        }
    }

    fun plainTextOf(t: DetailContent.Text): String {
        val cached = _plainTextCache.value[t.id]
        if (cached != null) return cached
        val now = toPlainText(t)
        viewModelScope.launch(Dispatchers.Default) {
            val updated = DetailPlainTextCachePolicy.put(_plainTextCache.value, t.id, now)
            if (updated != null) {
                withContext(Dispatchers.Main) { _plainTextCache.value = updated }
            }
        }
        return now
    }

    private fun loadDisplayFilterConfig(): DisplayFilterConfig {
        val hideDeleted = AppPreferences.getHideDeletedRes(appContext)
        val hideDuplicate = AppPreferences.getHideDuplicateRes(appContext)
        val threshold = AppPreferences.getDuplicateResThreshold(appContext)
        return DisplayFilterConfig(hideDeleted, hideDuplicate, threshold)
    }
    // ===== 新アーキテクチャ対応のメタデータ更新処理 =====

    /**
     * EventStore 連携でメタデータを更新する内部処理。
     * 画像は既存プロンプトを尊重しつつ、キャッシュ→抽出→Completed/Failed のイベントを発行し、
     * 成功時にはメタデータキャッシュと保存済み詳細も更新する。動画は現状ログのみ。
     */
    private suspend fun updateMetadataWithEventStore(contentList: List<DetailContent>, url: String) {
        contentList.forEach { content ->
            when (content) {
                is DetailContent.Image -> scheduleImageMetadataUpdate(content, url)
                is DetailContent.Video -> logPendingVideoMetadata(content)
                else -> {}
            }
        }
    }

    private suspend fun scheduleImageMetadataUpdate(content: DetailContent.Image, url: String) {
        val existingPrompt = content.prompt
        if (!existingPrompt.isNullOrBlank()) {
            cacheResolvedPrompt(content.imageUrl, existingPrompt)
            return
        }

        if (!PromptSettings.isPromptFetchEnabled(appContext)) return

        markPromptLoading(content.id, true)
        eventStore.applyEvent(DetailEvent.MetadataExtractionStarted(content.id))

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val normalizedPrompt = resolveNormalizedImagePrompt(content.imageUrl)
                eventStore.updateMetadataProgressively(content.id, normalizedPrompt)
                persistResolvedImagePrompt(content, url, normalizedPrompt)
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Metadata extraction error for ${content.imageUrl}", e)
                eventStore.updateMetadataProgressively(content.id, null)
            } finally {
                markPromptLoading(content.id, false)
            }
        }
    }

    private fun logPendingVideoMetadata(content: DetailContent.Video) {
        if (content.prompt.isNullOrBlank()) {
            Log.d("DetailViewModel", "Video metadata extraction not yet implemented for ${content.videoUrl}")
        }
    }

    private suspend fun resolveNormalizedImagePrompt(imageUrl: String): String? {
        val cachedPrompt = runCatching { metadataCache.get(imageUrl) }.getOrNull()
        val prompt = if (!cachedPrompt.isNullOrBlank()) {
            cachedPrompt
        } else {
            withTimeoutOrNull(15000L) {
                MetadataExtractor.extract(appContext, imageUrl, networkClient)
            }
        }
        return normalizePrompt(prompt)
    }

    private suspend fun persistResolvedImagePrompt(
        content: DetailContent.Image,
        url: String,
        normalizedPrompt: String?
    ) {
        if (normalizedPrompt.isNullOrBlank()) return

        val listForPersistence = updateRawContent { current ->
            DetailContentPromptUpdater.updatePrompt(
                contents = current,
                contentId = content.id,
                prompt = normalizedPrompt
            )
        }
        cacheResolvedPrompt(content.imageUrl, normalizedPrompt)
        runCatching { cacheManager.saveDetails(url, listForPersistence) }
    }

    private fun cacheResolvedPrompt(imageUrl: String, prompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { metadataCache.put(imageUrl, prompt) }
        }
    }

    /** 検索UI表示用の集計（アクティブ/現在位置/総数）をフローに反映。 */
    private fun publishSearchState() {
        val newState = DetailSearchEngine.buildState(
            hasQuery = currentSearchQuery != null,
            hitPositions = searchResultPositions,
            currentHitIndex = currentSearchHitIndex
        )
        _searchState.value = newState
        viewModelScope.launch {
            eventStore.applyEvent(DetailEvent.SearchStateUpdated(if (newState.active) newState else null))
        }
    }

    fun downloadImages(urls: List<String>) {
        if (urls.isEmpty()) return

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _downloadProgress.value = DownloadProgress(0, urls.size, isActive = true)
            var completed = 0

            try {
                val semaphore = kotlinx.coroutines.sync.Semaphore(4)
                coroutineScope {
                    val jobs = urls.map { url ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                val fileName = url.substringAfterLast('/')
                                _downloadProgress.value = DownloadProgress(completed, urls.size, fileName, true)

                                MediaSaver.saveImage(appContext, url, networkClient, referer = currentUrl)

                                downloadProgressMutex.withLock {
                                    completed++
                                    _downloadProgress.value = DownloadProgress(completed, urls.size, fileName, true)
                                }
                            }
                        }
                    }
                    jobs.awaitAll()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // キャンセルされた場合
                throw e
            } finally {
                delay(500) // 完了表示を少し見せる
                _downloadProgress.value = null
                downloadJob = null
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _downloadProgress.value = null
    }

    fun downloadImagesSkipExisting(urls: List<String>) {
        if (urls.isEmpty()) return

        viewModelScope.launch {
            val requestId = downloadRequestIdGenerator.incrementAndGet()
            val existingByUrl = MediaSaver.findExistingImages(appContext, urls)
            val pending = DetailBulkDownloadPlanner.createPendingRequest(requestId, urls, existingByUrl)

            if (!DetailBulkDownloadPlanner.shouldShowConflictDialog(pending)) {
                performBulkDownload(pending, DetailDownloadConflictResolution.SkipExisting)
                return@launch
            }

            pendingDownloadMutex.withLock {
                pendingDownloadRequests[requestId] = pending
            }

            _downloadConflictRequests.emit(
                DetailBulkDownloadPlanner.buildConflictRequest(pending) { it.fileName }
            )
        }
    }

    fun confirmDownloadSkip(requestId: Long) {
        viewModelScope.launch {
            val pending = removePendingRequest(requestId) ?: return@launch
            if (DetailBulkDownloadPlanner.hasNoDownloadTargets(pending, DetailDownloadConflictResolution.SkipExisting)) {
                withContext(Dispatchers.Main) {
                    val message = DetailDownloadMessageBuilder.buildNoTargetMessage(pending.existingCount)
                    android.widget.Toast.makeText(appContext, message, android.widget.Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            performBulkDownload(pending, DetailDownloadConflictResolution.SkipExisting)
        }
    }

    fun confirmDownloadOverwrite(requestId: Long) {
        viewModelScope.launch {
            val pending = removePendingRequest(requestId) ?: return@launch
            performBulkDownload(pending, DetailDownloadConflictResolution.OverwriteExisting)
        }
    }

    fun cancelDownloadRequest(requestId: Long) {
        viewModelScope.launch {
            pendingDownloadMutex.withLock {
                pendingDownloadRequests.remove(requestId)
            }
            // 古いリクエストのクリーンアップ（メモリリーク防止）
            cleanupOldDownloadRequests()
        }
    }

    /**
     * 古いダウンロードリクエストをクリーンアップする（メモリリーク防止）
     */
    private suspend fun cleanupOldDownloadRequests() {
        pendingDownloadMutex.withLock {
            if (pendingDownloadRequests.size > 5) {
                // 最新の3件のみ保持（メモリ効率改善）
                val sorted = pendingDownloadRequests.entries.sortedByDescending { it.key }
                val toRemove = sorted.drop(3).map { it.key }
                toRemove.forEach { pendingDownloadRequests.remove(it) }
                Log.d("DetailViewModel", "Cleaned up ${toRemove.size} old download requests")
            }
        }
    }

    private suspend fun removePendingRequest(requestId: Long): DetailPendingDownloadRequest<MediaSaver.ExistingMedia>? {
        return pendingDownloadMutex.withLock { pendingDownloadRequests.remove(requestId) }
    }

    private suspend fun performBulkDownload(
        pending: DetailPendingDownloadRequest<MediaSaver.ExistingMedia>,
        resolution: DetailDownloadConflictResolution
    ) {
        val urlsToDownload = DetailBulkDownloadPlanner.selectUrlsToDownload(pending, resolution)

        val total = urlsToDownload.size

        if (DetailBulkDownloadPlanner.hasNoDownloadTargets(pending, resolution)) {
            withContext(Dispatchers.Main) {
                val message = DetailDownloadMessageBuilder.buildNoTargetMessage(pending.existingCount)
                android.widget.Toast.makeText(appContext, message, android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (total == 0) return

        _downloadProgress.value = DownloadProgress(0, total, isActive = true)
        var completed = 0
        var stats = DetailBulkDownloadStats.initial(
            resolution = when (resolution) {
                DetailDownloadConflictResolution.SkipExisting -> DetailBulkDownloadStats.Resolution.SkipExisting
                DetailDownloadConflictResolution.OverwriteExisting -> DetailBulkDownloadStats.Resolution.OverwriteExisting
            },
            existingCount = pending.existingCount
        )

        val semaphore = Semaphore(4)

        try {
            coroutineScope {
                val jobs = urlsToDownload.map { url ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val fileName = url.substringAfterLast('/')
                            _downloadProgress.value = DownloadProgress(completed, total, fileName, true)
                            val hasExisting = !pending.existingByUrl[url].isNullOrEmpty()
                            val success = when (resolution) {
                                DetailDownloadConflictResolution.SkipExisting ->
                                    MediaSaver.saveImageIfNotExists(appContext, url, networkClient, referer = currentUrl)
                                DetailDownloadConflictResolution.OverwriteExisting -> {
                                    pending.existingByUrl[url]?.let { entries ->
                                        MediaSaver.deleteMedia(appContext, entries)
                                    }
                                    MediaSaver.saveImageIfNotExists(appContext, url, networkClient, referer = currentUrl)
                                }
                            }

                            downloadProgressMutex.withLock {
                                stats = stats.recordResult(success = success, hadExistingFile = hasExisting)
                                completed++
                                _downloadProgress.value = DownloadProgress(completed, total, fileName, true)
                            }
                        }
                    }
                }

                jobs.awaitAll()
            }

            withContext(Dispatchers.Main) {
                val message = stats.buildMessage()
                android.widget.Toast.makeText(appContext, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        } finally {
            delay(500)
            _downloadProgress.value = null
        }
    }

    // ========== スレッド保存機能 ==========

    /**
     * スレッド全体をアーカイブする
     * 画像・動画・HTMLを一括でダウンロードし、同じディレクトリに保存する
     * @param threadTitle スレッドのタイトル
     * @return 成功メッセージまたはエラー
     */
    fun archiveThread(threadTitle: String): String? {
        if (_archiveProgress.value?.isActive == true) {
            return "既にスレッド保存が実行中です"
        }

        archiveJob?.cancel()
        archiveJob = viewModelScope.launch {
            try {
                _archiveProgress.value = ThreadArchiveProgress(
                    current = 0,
                    total = 1,
                    currentFileName = "準備中...",
                    isActive = true
                )

                val threadUrl = currentUrl ?: ""
                val contents = detailContent.value

                val result = threadArchiver.archiveThread(
                    threadTitle = threadTitle,
                    threadUrl = threadUrl,
                    contents = contents
                ) { progress ->
                    _archiveProgress.value = progress
                }

                // 完了メッセージを表示
                result.onSuccess { message ->
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            appContext,
                            message,
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }.onFailure { error ->
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            appContext,
                            "スレッド保存に失敗しました: ${error.message}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // 進捗表示をクリア
                delay(500)
                _archiveProgress.value = null
                archiveJob = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                // キャンセルされた場合
                _archiveProgress.value = null
                archiveJob = null
                throw e
            } catch (e: Exception) {
                Log.e("DetailViewModel", "Archive failed", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        appContext,
                        "スレッド保存に失敗しました: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                _archiveProgress.value = null
                archiveJob = null
            }
        }
        return null
    }

    fun cancelArchive() {
        archiveJob?.cancel()
        archiveJob = null
        _archiveProgress.value = null
    }

    // ========== TTS音声読み上げ制御 ==========

    /**
     * 全レスを順次読み上げ開始
     */
    fun startTtsReading() {
        val items = detailContent.value.filterIsInstance<DetailContent.Text>()
        ttsManager.playTexts(items) { text ->
            plainTextCache.value[text.id] ?: DetailPlainTextFormatter.fromText(text)
        }
    }

    /**
     * 指定レス番号から読み上げ開始
     */
    fun startTtsFromResNum(resNum: String) {
        val items = detailContent.value.filterIsInstance<DetailContent.Text>()
        ttsManager.playFromResNum(resNum, items) { text ->
            plainTextCache.value[text.id] ?: DetailPlainTextFormatter.fromText(text)
        }
    }

    /**
     * TTS一時停止
     */
    fun pauseTts() {
        ttsManager.pause()
    }

    /**
     * TTS再開
     */
    fun resumeTts() {
        ttsManager.resume()
    }

    /**
     * TTS停止
     */
    fun stopTts() {
        ttsManager.stop()
    }

    /**
     * 次のレスへスキップ
     */
    fun skipNextTts() {
        ttsManager.skipNext()
    }

    /**
     * 前のレスへ戻る
     */
    fun skipPreviousTts() {
        ttsManager.skipPrevious()
    }

    /**
     * 読み上げ速度設定
     */
    fun setTtsSpeechRate(rate: Float) {
        ttsManager.setSpeechRate(rate)
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.release()
    }
}
