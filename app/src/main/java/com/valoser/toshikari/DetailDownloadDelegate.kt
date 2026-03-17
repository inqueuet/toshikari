package com.valoser.toshikari

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * DetailViewModel から一括ダウンロード関連のロジックを切り出した Delegate。
 *
 * 責務:
 * - 単純な一括ダウンロード（downloadImages）
 * - 既存ファイルチェック付きダウンロード（downloadImagesSkipExisting）
 * - 競合解決ダイアログのフロー管理
 * - ダウンロード進捗の公開
 */
internal class DetailDownloadDelegate(
    private val appContext: Context,
    private val networkClient: NetworkClient,
    private val scope: CoroutineScope,
    private val currentUrlProvider: () -> String?,
) {
    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    private val _downloadConflictRequests = MutableSharedFlow<DownloadConflictRequest>(extraBufferCapacity = 1)
    val downloadConflictRequests = _downloadConflictRequests.asSharedFlow()

    private var downloadJob: kotlinx.coroutines.Job? = null
    private val downloadProgressMutex = Mutex()
    private val downloadRequestIdGenerator = AtomicLong(0)
    private val pendingDownloadMutex = Mutex()
    private val pendingDownloadRequests = mutableMapOf<Long, DetailPendingDownloadRequest<MediaSaver.ExistingMedia>>()

    fun downloadImages(urls: List<String>) {
        if (urls.isEmpty()) return
        val currentUrl = currentUrlProvider()

        downloadJob?.cancel()
        downloadJob = scope.launch {
            _downloadProgress.value = DownloadProgress(0, urls.size, isActive = true)
            var completed = 0

            try {
                val semaphore = Semaphore(4)
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
            } catch (e: CancellationException) {
                throw e
            } finally {
                delay(500)
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

        scope.launch {
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
        scope.launch {
            val pending = removePendingRequest(requestId) ?: return@launch
            if (DetailBulkDownloadPlanner.hasNoDownloadTargets(pending, DetailDownloadConflictResolution.SkipExisting)) {
                withContext(Dispatchers.Main) {
                    val message = DetailDownloadMessageBuilder.buildNoTargetMessage(pending.existingCount)
                    Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            performBulkDownload(pending, DetailDownloadConflictResolution.SkipExisting)
        }
    }

    fun confirmDownloadOverwrite(requestId: Long) {
        scope.launch {
            val pending = removePendingRequest(requestId) ?: return@launch
            performBulkDownload(pending, DetailDownloadConflictResolution.OverwriteExisting)
        }
    }

    fun cancelDownloadRequest(requestId: Long) {
        scope.launch {
            pendingDownloadMutex.withLock {
                pendingDownloadRequests.remove(requestId)
            }
            cleanupOldDownloadRequests()
        }
    }

    private suspend fun cleanupOldDownloadRequests() {
        pendingDownloadMutex.withLock {
            if (pendingDownloadRequests.size > 5) {
                val sorted = pendingDownloadRequests.entries.sortedByDescending { it.key }
                val toRemove = sorted.drop(3).map { it.key }
                toRemove.forEach { pendingDownloadRequests.remove(it) }
                Log.d("DetailDownloadDelegate", "Cleaned up ${toRemove.size} old download requests")
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
        val currentUrl = currentUrlProvider()

        if (DetailBulkDownloadPlanner.hasNoDownloadTargets(pending, resolution)) {
            withContext(Dispatchers.Main) {
                val message = DetailDownloadMessageBuilder.buildNoTargetMessage(pending.existingCount)
                Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
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
                Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            }
        } finally {
            delay(500)
            _downloadProgress.value = null
        }
    }
}
