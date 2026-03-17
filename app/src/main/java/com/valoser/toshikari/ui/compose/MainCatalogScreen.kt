/**
 * 画像カタログ（メイン一覧）画面の Compose 実装。
 *
 * 特徴:
 * - 更新: プル更新（PullToRefresh）で一覧を再読み込み。
 * - 体験: 可視範囲＋先読みの軽量プリフェッチでスクロールを滑らかに。
 * - 表示: カード下部に SurfaceVariant のタイトル領域を設け、返信数バッジは右上に配置。
 * - フィルタリング: OP画像なしスレッドは常時非表示に設定。
 * - エラー: フル画像の取得が失敗した場合は可能ならプレビューへフォールバックし、
 *          それも不可の場合は簡易プレースホルダを表示。
 *          404 検知時は `onImageLoadHttp404` を介して ViewModel に通知し、URL 補正を試みる。
 */
package com.valoser.toshikari.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Precision
import coil3.network.HttpException
import coil3.network.httpHeaders
import coil3.network.NetworkHeaders
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import coil3.compose.SubcomposeAsyncImage
import coil3.request.transitionFactory
import coil3.transition.CrossfadeTransition
import com.valoser.toshikari.ImageItem
import com.valoser.toshikari.MatchType
import com.valoser.toshikari.NgRule
import com.valoser.toshikari.RuleType
import com.valoser.toshikari.SafeRegex
import com.valoser.toshikari.ui.theme.LocalSpacing
import com.valoser.toshikari.CatalogPrefetchHint
import com.valoser.toshikari.ui.common.AppBarPosition

/**
 * 画像カタログの一覧画面（メイン画面）。
 *
 * 機能概要:
 * - 更新: プルリフレッシュを提供。
 * - 操作: トップバーは「再読み込み／ブックマーク選択／検索」の主要操作に絞り、
 *         右上メニューに補助操作（並び順・履歴・管理系）を集約。
 *         プロンプト機能が有効な場合のみ「ローカル画像を開く」を追加表示。
 * - トップバー: 通常時はサブタイトル（選択中ブックマーク名）のみを大きく表示。タイトルは非表示。
 *               検索中はタイトル領域を検索ボックスに切り替える。
 * - 絞込: NG タイトルルール、検索クエリ、画像有無（常時適用）で一覧をフィルタし、グリッド表示。
 * - 体験: 可視範囲＋先読み分のみを軽量プリフェッチし、`onPrefetchHint` で呼び出し側へ通知。
 * - 表示: カード下部は SurfaceVariant の帯でタイトルを表示し、返信数バッジは右上に固定。
 * - エラー: フル画像が失敗した場合はプレビューへフォールバックし、プレビューも不可の場合は簡易プレースホルダを表示。
 *
 * パラメータ:
 * - `modifier`: ルートレイアウト用の修飾子。
 * - `title`/`subtitle`: 上部タイトル/サブタイトル（通常はタイトルを表示せず、サブタイトルのみ表示）。
 * - `items`: 表示対象のアイテム一覧（呼び出し側で取得）。
 * - `isLoading`: 読み込み中インジケータの表示制御。
 * - `spanCount`: グリッド列数。
 * - `topBarPosition`: トップバーの配置位置。
 * - `hasAnyBookmarks` / `hasSelectedBookmark`: ブックマークの登録・選択状況に応じて UI を調整するためのフラグ。
 * - `query`/`onQueryChange`: 検索クエリと変更ハンドラ。
 * - `onReload`: 更新アクション（プルリフレッシュから呼び出し）。
 * - `onPrefetchHint`: 可視範囲＋先読み分のプリフェッチ要求を通知するコールバック。
 * - `onSelectBookmark`: ブックマーク選択ダイアログ等を開くアクション。
 * - `onManageBookmarks`: ブックマーク管理画面を開くアクション（メニューから呼び出し）。
 * - `onOpenSettings`: 設定画面を開くアクション（メニューから呼び出し）。
 * - `onOpenHistory`: 履歴画面を開くアクション（メニューから呼び出し）。
 * - `onOpenPastSearch`: 過去スレ検索画面を開くアクション（メニューから呼び出し）。
 * - `onImageEdit`/`onBrowseLocalImages`: 画像編集／ローカル画像のメニュー操作。
 * - `onVideoEdit`: 動画編集のメニュー操作。
 * - `promptFeaturesEnabled`: プロンプト機能が有効な場合に追加メニューを表示するフラグ。
 * - `onToggleDisplayMode`: グリッド/リスト表示を即座に切り替えるためのハンドラ。
 * - `onItemClick`: アイテムタップ時のハンドラ。
 * - `ngRules`: NG タイトルルール一覧（TITLE のみ対象）。
 * - `onImageLoadHttp404`: 画像ロードが 404 で失敗した際に呼ばれるコールバック。
 *                         ViewModel 側で URL 補正（代替URLの探索・差し替え）を行うために使用。
 * - `onImageLoadSuccess`: フル画像が取得できた際に呼ばれるコールバック。検証済み URL の保持に使用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainCatalogScreen(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    listIdentity: String,
    items: List<ImageItem>,
    isLoading: Boolean,
    spanCount: Int,
    catalogDisplayMode: String = "grid",
    topBarPosition: AppBarPosition = AppBarPosition.TOP,
    hasAnyBookmarks: Boolean,
    hasSelectedBookmark: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onReload: () -> Unit,
    onPrefetchHint: (CatalogPrefetchHint) -> Unit,
    onSelectBookmark: () -> Unit,
    onSelectSortMode: () -> Unit,
    onManageBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPastSearch: () -> Unit,
    onImageEdit: () -> Unit,
    onVideoEdit: () -> Unit,
    onBrowseLocalImages: () -> Unit,
    onOpenNgFilters: () -> Unit,
    promptFeaturesEnabled: Boolean = true,
    onToggleDisplayMode: () -> Unit,
    onItemClick: (ImageItem) -> Unit,
    ngRules: List<NgRule>,
    onImageLoadHttp404: (item: ImageItem, failedUrl: String) -> Unit,
    onImageLoadSuccess: (item: ImageItem, loadedUrl: String) -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()

    // NG タイトルルールとクエリ、画像有無で絞り込み（常時OP画像なしスレッドを非表示）
    val hasNgFilters = ngRules.isNotEmpty()
    val filtered = remember(items, query, ngRules) {
        val titleRules = ngRules.filter { it.type == RuleType.TITLE }
        items.asSequence()
            .filter { item ->
                val title = item.title.orEmpty()
                titleRules.none { r -> matchTitle(title, r) }
            }
            .filter { item ->
                if (query.isBlank()) true else item.title.contains(query, ignoreCase = true)
            }
            .filter { item ->
                hasImages(item)
            }
            .toList()
    }

    val isListMode = catalogDisplayMode == "list"
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    LaunchedEffect(listIdentity) {
        // ソースが切り替わったら新しい一覧の先頭へ戻す
        listState.scrollToItem(0)
        gridState.scrollToItem(0)
    }

    // CompositionLocals はコンポーズ文脈内で取得しておき、
    // LaunchedEffect 内では非 @Composable な値（Dp など）として参照する
    val spacing = LocalSpacing.current
    val sDp = spacing.s
    val xsDp = spacing.xs

    val density = LocalDensity.current
    val sPx by remember(sDp, density) { derivedStateOf { with(density) { sDp.toPx() } } }
    val xsPx by remember(xsDp, density) { derivedStateOf { with(density) { xsDp.toPx() } } }

    // 軽量プリフェッチ（可視範囲＋先読み分のみを事前ロード）
    // 実表示サイズと同一のサイズでプリフェッチし、メモリキャッシュのヒット率を最大化する
    LaunchedEffect(filtered, gridState, listState, spanCount, sPx, xsPx, isListMode) {
        snapshotFlow {
            if (isListMode) {
                val layout = listState.layoutInfo
                val first = layout.visibleItemsInfo.firstOrNull()?.index ?: 0
                val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                val viewportWidthPx = layout.viewportSize.width
                Triple(first, last, viewportWidthPx)
            } else {
                val layout = gridState.layoutInfo
                val first = layout.visibleItemsInfo.firstOrNull()?.index ?: 0
                val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                val viewportWidthPx = layout.viewportSize.width
                Triple(first, last, viewportWidthPx)
            }
        }
            .distinctUntilChanged()
            .collectLatest { (first, last, viewportWidthPx) ->
                if (last <= 0 || filtered.isEmpty()) return@collectLatest

                val contentWidthPx = (viewportWidthPx - (sPx * 2)).coerceAtLeast(0f)
                val cellWidthPx = if (isListMode) {
                    // リスト表示: サムネイル幅は約120dp相当の固定値を px 換算して利用
                    120.dp.value * 3f // 適切なdp値を使用
                } else {
                    ((contentWidthPx / spanCount) - (xsPx * 2)).coerceAtLeast(64f)
                }
                val cellHeightPx = cellWidthPx * 4f / 3f

                // 先読み行数は画面内の行数の約2倍（2画面分）
                val visibleCount = (last - first + 1).coerceAtLeast(if (isListMode) 1 else spanCount)
                val rowsVisible = if (isListMode) visibleCount else (visibleCount + spanCount - 1) / spanCount
                val prefetchRows = (rowsVisible * 2).coerceAtLeast(1)
                val prefetchAhead = if (isListMode) prefetchRows else prefetchRows * spanCount

                val end = (last + prefetchAhead).coerceAtMost(filtered.lastIndex)
                val start = first.coerceAtLeast(0)
                if (end < start) return@collectLatest

                val targets = filtered.subList(start, end + 1)
                if (targets.isEmpty()) return@collectLatest

                onPrefetchHint(
                    CatalogPrefetchHint(
                        items = targets.toList(),
                        cellWidthPx = cellWidthPx.toInt(),
                        cellHeightPx = cellHeightPx.toInt(),
                    )
                )
            }
    }

    // 再読み込み直後の能動的なフル化は行わない（経路を404修正に一本化）

    val toolbarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    )

    val toolbarWindowInsets = if (topBarPosition == AppBarPosition.TOP) {
        TopAppBarDefaults.windowInsets
    } else {
        WindowInsets(0, 0, 0, 0)
    }

    // システムナビゲーションバーのインセット（Androidの3ボタンナビゲーション対応）
    val systemBarsInsets = WindowInsets.systemBars
    val navigationBarHeight = with(LocalDensity.current) { systemBarsInsets.getBottom(this) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val toolbar: @Composable () -> Unit = {
        LaunchedEffect(searching) {
            if (searching) {
                focusRequester.requestFocus()
                keyboardController?.show()
            } else {
                keyboardController?.hide()
            }
        }
        TopAppBar(
            title = {
                if (searching) {
                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        placeholder = { Text(text = "検索") },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = {
                                    onQueryChange("")
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "検索キーワードをクリア")
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        )
                    )
                } else {
                    // サブタイトルのみを大きく表示（タイトルは非表示）
                    val sub = subtitle.orEmpty()
                    if (sub.isNotBlank()) {
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            actions = {
                // 最も頻繁に使う操作を前に配置
                IconButton(
                    onClick = {
                        if (hasAnyBookmarks) {
                            onSelectBookmark()
                        } else {
                            onManageBookmarks()
                        }
                    }
                ) {
                    Icon(Icons.Rounded.Bookmarks, contentDescription = "ブックマークを選択")
                }
                IconButton(onClick = {
                    if (searching) {
                        searching = false
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    } else {
                        searching = true
                    }
                }) {
                    Icon(Icons.Rounded.Search, contentDescription = "検索")
                }
                IconButton(
                    onClick = { if (!isLoading && hasSelectedBookmark) onReload() },
                    enabled = !isLoading && hasSelectedBookmark
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "再読み込み")
                }
                MoreMenu(
                    onToggleDisplayMode = onToggleDisplayMode,
                    onManageBookmarks = onManageBookmarks,
                    onSelectSortMode = onSelectSortMode,
                    onOpenHistory = onOpenHistory,
                    onOpenPastSearch = onOpenPastSearch,
                    onOpenSettings = onOpenSettings,
                    onImageEdit = onImageEdit,
                    onVideoEdit = onVideoEdit,
                    onBrowseLocalImages = onBrowseLocalImages,
                    promptFeaturesEnabled = promptFeaturesEnabled,
                    hasSelectedBookmark = hasSelectedBookmark,
                    isListMode = isListMode,
                )
            },
            colors = toolbarColors,
            windowInsets = toolbarWindowInsets
        )
    }

    Scaffold(
        topBar = {
            if (topBarPosition == AppBarPosition.TOP) {
                toolbar()
            }
        },
        bottomBar = {
            if (topBarPosition == AppBarPosition.BOTTOM) {
                Column(
                    modifier = Modifier
                        // システムナビゲーションバーと重ならないようにパディング追加
                        .padding(bottom = with(LocalDensity.current) { navigationBarHeight.toDp() })
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    HorizontalDivider()
                    toolbar()
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            state = pullState,
            isRefreshing = isLoading,
            onRefresh = onReload,
            indicator = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    PullToRefreshDefaults.Indicator(
                        modifier = Modifier.align(Alignment.Center),
                        state = pullState,
                        isRefreshing = isLoading
                    )
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val showFilters = query.isNotBlank() || hasNgFilters
                    if (showFilters) {
                        ActiveFilterRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = LocalSpacing.current.s, vertical = LocalSpacing.current.xs),
                            query = query,
                            hasNgFilters = hasNgFilters,
                            onClearQuery = { onQueryChange("") },
                            onOpenNgFilters = onOpenNgFilters
                        )
                    }
                    CatalogQuickActionChips(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LocalSpacing.current.s, vertical = LocalSpacing.current.xs),
                        hasAnyBookmarks = hasAnyBookmarks,
                        hasSelectedBookmark = hasSelectedBookmark,
                        isListMode = isListMode,
                        onShowBookmarks = {
                            if (hasAnyBookmarks) onSelectBookmark() else onManageBookmarks()
                        },
                        onToggleDisplayMode = onToggleDisplayMode,
                        onSelectSortMode = onSelectSortMode,
                    )
                    if (isListMode) {
                        // リスト本体
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            state = listState,
                            contentPadding = PaddingValues(LocalSpacing.current.s)
                        ) {
                            lazyListItems(
                                filtered,
                                key = { it.detailUrl },
                                contentType = { "image_list_item" }
                            ) { item ->
                                CatalogListItem(
                                    item = item,
                                    onClick = remember(item.detailUrl) { { onItemClick(item) } },
                                    onImageLoadHttp404 = onImageLoadHttp404,
                                    onImageLoadSuccess = onImageLoadSuccess,
                                )
                            }
                        }
                    } else {
                        // グリッド本体
                        LazyVerticalGrid(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            state = gridState,
                            columns = GridCells.Fixed(spanCount.coerceAtLeast(1)),
                            contentPadding = PaddingValues(LocalSpacing.current.s)
                        ) {
                            // 安定キーと contentType でリサイクルを効率化
                            items(
                                filtered,
                                key = { it.detailUrl },
                                contentType = { "image_card" }
                            ) { item ->
                                CatalogCard(
                                    item = item,
                                    // Stable 参照でリコンポジションを最小化
                                    onClick = remember(item.detailUrl) { { onItemClick(item) } },
                                    onImageLoadHttp404 = onImageLoadHttp404,
                                    onImageLoadSuccess = onImageLoadSuccess,
                                )
                            }
                        }
                    }
                }

                if (isLoading && items.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                if (!isLoading && filtered.isEmpty()) {
                    EmptyCatalogState(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(LocalSpacing.current.l),
                        hasAnyBookmarks = hasAnyBookmarks,
                        hasSelectedBookmark = hasSelectedBookmark,
                        query = query,
                        hasNgFilters = hasNgFilters,
                        onSelectBookmark = onSelectBookmark,
                        onManageBookmarks = onManageBookmarks
                    )
                }
            }
        }
    }
}
