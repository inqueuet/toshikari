/**
 * MainCatalogScreen から抽出した補助 Composable 群。
 *
 * - ActiveFilterRow: 検索・NGフィルタの適用状態チップ行
 * - CatalogQuickActionChips: ブックマーク追加・表示切替・並び順チップ行
 * - EmptyCatalogState: カタログが空の場合の案内 UI
 * - MoreMenu: 右上「その他」ドロップダウンメニュー
 * - CatalogListItem: カタログアイテムのリスト形式表示
 * - CatalogCard: カタログアイテムのカード表示
 * - hasImages: 画像有無判定ヘルパー
 * - matchTitle: NGタイトルルールマッチ判定
 */
package com.valoser.toshikari.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.imageLoader
import coil3.network.HttpException
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.transitionFactory
import coil3.size.Dimension
import coil3.size.Precision
import coil3.transition.CrossfadeTransition
import com.valoser.toshikari.ImageItem
import com.valoser.toshikari.MatchType
import com.valoser.toshikari.image.ImageKeys
import com.valoser.toshikari.NgRule
import com.valoser.toshikari.SafeRegex
import com.valoser.toshikari.Ua
import com.valoser.toshikari.ui.theme.LocalSpacing

@Composable
internal fun ActiveFilterRow(
    modifier: Modifier = Modifier,
    query: String,
    hasNgFilters: Boolean,
    onClearQuery: () -> Unit,
    onOpenNgFilters: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (query.isNotBlank()) {
            AssistChip(
                onClick = onClearQuery,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "検索") },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "検索: $query",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "検索条件をクリア",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )
        }
        if (hasNgFilters) {
            AssistChip(
                onClick = onOpenNgFilters,
                enabled = true,
                leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = "NGフィルタ") },
                label = { Text("NGフィルタ適用中") }
            )
        }
    }
}

@Composable
internal fun CatalogQuickActionChips(
    modifier: Modifier = Modifier,
    hasAnyBookmarks: Boolean,
    hasSelectedBookmark: Boolean,
    isListMode: Boolean,
    onShowBookmarks: () -> Unit,
    onToggleDisplayMode: () -> Unit,
    onSelectSortMode: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!hasAnyBookmarks) {
            AssistChip(
                onClick = onShowBookmarks,
                label = { Text("ブックマーク追加") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Bookmarks,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
        AssistChip(
            onClick = onToggleDisplayMode,
            label = { Text(if (isListMode) "グリッド表示" else "リスト表示") },
            leadingIcon = {
                val icon = if (isListMode) Icons.Rounded.GridView else Icons.Rounded.ViewList
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        )
        AssistChip(
            onClick = onSelectSortMode,
            enabled = hasSelectedBookmark,
            label = { Text("並び順") },
            leadingIcon = {
                Icon(imageVector = Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        )
    }
}

@Composable
internal fun EmptyCatalogState(
    modifier: Modifier = Modifier,
    hasAnyBookmarks: Boolean,
    hasSelectedBookmark: Boolean,
    query: String,
    hasNgFilters: Boolean,
    onSelectBookmark: () -> Unit,
    onManageBookmarks: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            !hasAnyBookmarks -> {
                Text(
                    text = "ブックマークがまだ登録されていません",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(spacing.s))
                Text(
                    text = "「ブックマークを管理」から板を追加してください。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(spacing.m))
                Button(onClick = onManageBookmarks) {
                    Text("ブックマークを追加")
                }
            }
            !hasSelectedBookmark -> {
                Text(
                    text = "表示するブックマークを選択してください",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(spacing.s))
                Button(onClick = onSelectBookmark) {
                    Text("ブックマークを選ぶ")
                }
                Spacer(modifier = Modifier.height(spacing.xs))
                TextButton(onClick = onManageBookmarks) {
                    Text("ブックマークを管理")
                }
            }
            else -> {
                Text(
                    text = "表示できるスレッドがありません",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(spacing.s))
                val hints = buildList {
                    if (query.isNotBlank()) add("検索キーワード")
                    if (hasNgFilters) add("NG設定")
                }
                val hintText = if (hints.isEmpty()) {
                    "一覧を更新するか、別のブックマークを選択してください。"
                } else {
                    hints.joinToString("・", postfix = "を確認してください。")
                }
                Text(
                    text = hintText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 右上「その他」メニュー。ブックマーク管理や編集系の操作を集約し、
 * プロンプト機能が有効な場合のみ「ローカル画像を開く」を追加表示する。
 * 選択時はメニューを閉じてから各ハンドラを呼び出す。
 */
@Composable
internal fun MoreMenu(
    onToggleDisplayMode: () -> Unit,
    onManageBookmarks: () -> Unit,
    onSelectSortMode: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onImageEdit: () -> Unit,
    onVideoEdit: () -> Unit,
    onBrowseLocalImages: () -> Unit,
    promptFeaturesEnabled: Boolean,
    hasSelectedBookmark: Boolean,
    isListMode: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "その他")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // よく使う操作グループ
            DropdownMenuItem(
                text = { Text("並び順") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "並び順") },
                onClick = { expanded = false; onSelectSortMode() },
                enabled = hasSelectedBookmark
            )
            DropdownMenuItem(
                text = { Text(if (isListMode) "グリッド表示に切り替え" else "リスト表示に切り替え") },
                leadingIcon = {
                    val icon = if (isListMode) Icons.Rounded.GridView else Icons.Rounded.ViewList
                    Icon(icon, contentDescription = "表示切替")
                },
                onClick = { expanded = false; onToggleDisplayMode() }
            )
            DropdownMenuItem(
                text = { Text("履歴") },
                leadingIcon = { Icon(Icons.Rounded.History, contentDescription = "履歴") },
                onClick = { expanded = false; onOpenHistory() }
            )

            HorizontalDivider()

            // 管理系操作グループ
            DropdownMenuItem(
                text = { Text("ブックマーク管理") },
                leadingIcon = { Icon(Icons.Rounded.Bookmarks, contentDescription = "ブックマーク管理") },
                onClick = { expanded = false; onManageBookmarks() }
            )
            DropdownMenuItem(
                text = { Text("設定") },
                leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = "設定") },
                onClick = { expanded = false; onOpenSettings() }
            )

            HorizontalDivider()

            // 編集系操作グループ
            if (promptFeaturesEnabled) {
                DropdownMenuItem(
                    text = { Text("ローカル画像を開く") },
                    leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = "ローカル画像を開く") },
                    onClick = { expanded = false; onBrowseLocalImages() }
                )
            }
            DropdownMenuItem(
                text = { Text("画像編集") },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = "画像編集") },
                onClick = { expanded = false; onImageEdit() }
            )
            DropdownMenuItem(
                text = { Text("動画編集") },
                leadingIcon = { Icon(Icons.Rounded.Movie, contentDescription = "動画編集") },
                onClick = { expanded = false; onVideoEdit() }
            )
        }
    }
}

/**
 * カタログアイテムのリスト形式表示。
 * 左側にサムネイル、右側にスレタイ・返信数を配置する、としあきアプリ風のレイアウト。
 * 画像とテキストは重ならないよう分離して配置する。
 * アイテムの下部には太めの区切り線を配置する。
 */
@Composable
internal fun CatalogListItem(
    item: ImageItem,
    onClick: () -> Unit,
    onImageLoadHttp404: (item: ImageItem, failedUrl: String) -> Unit,
    onImageLoadSuccess: (item: ImageItem, loadedUrl: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = LocalSpacing.current.xs),
            onClick = onClick,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            // Ripple effectを明確にするための設定
            interactionSource = remember { MutableInteractionSource() }
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LocalSpacing.current.s)
        ) {
            // 左側: サムネイル（固定サイズ）
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .aspectRatio(3f / 4f)
            ) {
                val displayUrl = when {
                    !item.lastVerifiedFullUrl.isNullOrBlank() -> item.lastVerifiedFullUrl
                    !item.previewUnavailable -> item.previewUrl
                    else -> null
                }

                if (!displayUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        modifier = Modifier.fillMaxSize(),
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(displayUrl)
                            .memoryCacheKey(ImageKeys.full(displayUrl))
                            .httpHeaders(
                                NetworkHeaders.Builder()
                                    .add("Referer", item.detailUrl)
                                    .add("Accept", "*/*")
                                    .add("Accept-Language", "ja,en-US;q=0.9,en;q=0.8")
                                    .add("User-Agent", Ua.STRING)
                                    .build()
                            )
                            .listener(
                                onSuccess = { request, _ ->
                                    val loaded = request.data?.toString()
                                    if (loaded?.contains("/src/") == true && !item.hadFullSuccess) {
                                        onImageLoadSuccess(item, loaded)
                                    }
                                },
                                onError = { request, result ->
                                    val ex = result.throwable
                                    if (ex is HttpException && ex.response.code == 404) {
                                        val failed = request.data?.toString() ?: ""
                                        if (failed.isNotEmpty()) onImageLoadHttp404(item, failed)
                                    }
                                }
                            )
                            .build(),
                        imageLoader = LocalContext.current.imageLoader,
                        contentDescription = item.title,
                        loading = {
                            Box(Modifier.fillMaxSize()) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                        },
                        error = {
                            Box(Modifier.fillMaxSize()) {
                                Text(
                                    text = "画像なし",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    )
                } else {
                    Box(Modifier.fillMaxSize()) {
                        Text(
                            text = "画像なし",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                // 動画アイコン
                val isVideo = displayUrl?.let { url ->
                    url.lowercase().endsWith(".webm") || url.lowercase().endsWith(".mp4") || url.lowercase().endsWith(".mkv")
                } ?: false
                if (isVideo) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                    )
                }
            }

            Spacer(modifier = Modifier.width(LocalSpacing.current.m))

            // 右側: スレタイと返信数
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.replyCount.isNullOrBlank()) {
                    Text(
                        text = "返信: ${item.replyCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = LocalSpacing.current.s),
            thickness = 2.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/**
 * カタログアイテムのカード表示。
 * 画像とタイトルを分離して配置（画像が上、タイトルが下）。
 * 動画拡張子（.webm/.mp4/.mkv）は中央に再生アイコンを重ねる。
 * エラー時の挙動: 検証済みのフル画像があればそれを優先し、失敗した場合はプレビューへフォールバック。
 * プレビューも取得できない場合は簡易プレースホルダを表示し、HTTP 404 は `onImageLoadHttp404` に通知して
 * ViewModel 側で代替 URL の探索・補正を試みる。
 */
@Composable
internal fun CatalogCard(
    item: ImageItem,
    onClick: () -> Unit,
    onImageLoadHttp404: (item: ImageItem, failedUrl: String) -> Unit,
    onImageLoadSuccess: (item: ImageItem, loadedUrl: String) -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(LocalSpacing.current.xxs)
            .aspectRatio(3f / 4f), // カード全体を4:3に
        onClick = onClick,
        border = BorderStroke(
            width = 2.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        // Ripple effectを明確にするための設定
        interactionSource = remember { MutableInteractionSource() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ViewModel の判定を信頼し、表示URLはモデルから決定
            val displayUrl = when {
                // 検証済みURLがあれば最優先（未検証フルは使用しない）
                !item.lastVerifiedFullUrl.isNullOrBlank() -> item.lastVerifiedFullUrl
                // プレビューを即時表示
                !item.previewUnavailable -> item.previewUrl
                else -> null
            }

            // 画像部分（タイトル領域を確保するため、weightで残りの空間を使用）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // 実表示サイズを Coil に伝えてキャッシュ共有を確実にする
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    // 実表示幅そのものを使用（余白の二重減算を避ける）
                    val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                    val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
                    // 再挑戦経路はVMの404修正に一本化。UIからの能動的フル化要求は行わない。

                    if (!displayUrl.isNullOrBlank()) {
                        SubcomposeAsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(displayUrl)
                                .memoryCacheKey(ImageKeys.full(displayUrl))
                                .size(Dimension.Pixels(widthPx.toInt()), Dimension.Pixels(heightPx.toInt()))
                                .precision(Precision.INEXACT)
                                // 画像（プレビュー⇄フル）切替時のフラッシュ感を抑える
                                .transitionFactory(CrossfadeTransition.Factory())
                                .httpHeaders(
                                    NetworkHeaders.Builder()
                                        .add("Referer", item.detailUrl)
                                        .add("Accept", "*/*")
                                        .add("Accept-Language", "ja,en-US;q=0.9,en;q=0.8")
                                        .add("User-Agent", Ua.STRING)
                                        .build()
                                )
                                .listener(
                                    onSuccess = { request, _ ->
                                        val loaded = request.data?.toString()
                                        if (loaded?.contains("/src/") == true && !item.hadFullSuccess) {
                                            onImageLoadSuccess(item, loaded)
                                        }
                                    },
                                    onError = { request, result ->
                                        val ex = result.throwable
                                        if (ex is HttpException && ex.response.code == 404) {
                                            val failed = request.data?.toString() ?: ""
                                            if (failed.isNotEmpty()) onImageLoadHttp404(item, failed)
                                        }
                                    }
                                )
                            .build(),
                            imageLoader = LocalContext.current.imageLoader,
                            contentDescription = item.title,
                            loading = {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            },
                            error = {
                                // フル画像の読み込みエラー時は、可能ならプレビュー画像を表示する
                                if (displayUrl == item.fullImageUrl && !item.previewUnavailable) {
                                    SubcomposeAsyncImage(
                                        modifier = Modifier.fillMaxSize(),
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(item.previewUrl)
                                            .memoryCacheKey(ImageKeys.full(item.previewUrl))
                                            .size(Dimension.Pixels(widthPx.toInt()), Dimension.Pixels(heightPx.toInt()))
                                            .precision(Precision.EXACT)
                                            // フォールバック時もクロスフェードで切替を穏やかに
                                            .transitionFactory(CrossfadeTransition.Factory())
                                            .httpHeaders(
                                                NetworkHeaders.Builder()
                                                    .add("Referer", item.detailUrl)
                                                    .add("Accept", "*/*")
                                                    .add("Accept-Language", "ja,en-US;q=0.9,en;q=0.8")
                                                    .add("User-Agent", Ua.STRING)
                                                    .build()
                                            )
                                            .build(),
                                        imageLoader = LocalContext.current.imageLoader,
                                        contentDescription = item.title,
                                        loading = {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.align(Alignment.Center)
                                                )
                                            }
                                        },
                                        error = {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.errorContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.BrokenImage,
                                                        contentDescription = "画像エラー",
                                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(LocalSpacing.current.xs))
                                                    Text(
                                                        text = "読み込みエラー",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.errorContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.BrokenImage,
                                                contentDescription = "画像エラー",
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(modifier = Modifier.height(LocalSpacing.current.xs))
                                            Text(
                                                text = "読み込みエラー",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    } else {
                        val giveUp = item.preferPreviewOnly || item.previewUnavailable
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (!giveUp) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            } else {
                                Text(
                                    text = "画像を表示できません",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                }

                // 動画の場合は中央に再生アイコンを重ねる（表示URL基準）
                val isVideo = displayUrl?.let { url ->
                    url.lowercase().endsWith(".webm") || url.lowercase().endsWith(".mp4") || url.lowercase().endsWith(".mkv")
                } ?: false
                if (isVideo) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                    )
                }

                // 返信数を右上に表示
                if (!item.replyCount.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(LocalSpacing.current.xs),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = item.replyCount,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(
                                horizontal = LocalSpacing.current.xs,
                                vertical = LocalSpacing.current.xxs
                            )
                        )
                    }
                }
            }

            // タイトル部分（画像の下に分離して配置、高さを固定）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(LocalSpacing.current.s)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * アイテムが画像を持っているかどうかを判定する。
 * プレビュー画像が利用不可の場合は画像なしと判定し、それ以外は画像ありとする。
 */
internal fun hasImages(item: ImageItem): Boolean {
    return !item.previewUnavailable
}

/**
 * タイトルに対して NG ルールを適用してマッチ判定する。
 * `RuleType.TITLE` のみを対象とし、`MatchType` に応じて一致判定を行う。
 */
internal fun matchTitle(title: String, rule: NgRule): Boolean {
    val pattern = rule.pattern
    val mt = rule.match ?: MatchType.SUBSTRING
    return when (mt) {
        MatchType.EXACT -> title == pattern
        MatchType.PREFIX -> title.startsWith(pattern, ignoreCase = true)
        MatchType.SUBSTRING -> title.contains(pattern, ignoreCase = true)
        MatchType.REGEX -> SafeRegex.containsMatchIn(pattern = pattern, target = title, ignoreCase = true)
    }
}
