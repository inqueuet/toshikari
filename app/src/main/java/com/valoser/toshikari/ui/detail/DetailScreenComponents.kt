/**
 * DetailScreen から切り出した再利用可能な Composable コンポーネント群。
 *
 * - SearchNavigationBar: 検索ヒットの前後ナビゲーション
 * - QuickFilterChip: ドック型検索のサジェストチップ
 * - TtsControlPanel: TTS 音声読み上げ制御パネル
 * - DetailQuickActions: 再読み込み/メディア一覧/NG管理等のアクションバー
 * - AdBanner: Google Mobile Ads バナー広告ホスト
 */
package com.valoser.toshikari.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.valoser.toshikari.ui.theme.LocalSpacing

/**
 * 検索用ナビゲーションバー（下部オーバーレイ）。
 * 現在位置/総ヒット数を表示し、矢印押下で前後移動。
 */
@Composable
internal fun SearchNavigationBar(
    modifier: Modifier = Modifier,
    current: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        modifier = modifier,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = LocalSpacing.current.s, vertical = LocalSpacing.current.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev")
            }
            Text(
                text = if (total > 0 && current in 1..total) "$current/$total" else "0/0",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = LocalSpacing.current.s)
            )
            IconButton(onClick = onNext) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
            }
        }
    }
}

/**
 * ドック型検索 UI 内で使う簡易サジェスト用の AssistChip。
 */
@Composable
internal fun QuickFilterChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/**
 * TTS音声読み上げ制御パネル（下部オーバーレイ）。
 * 再生/一時停止、停止、スキップボタンと現在読み上げ中のレス番号を表示。
 */
@Composable
internal fun TtsControlPanel(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    currentResNum: String?,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
) {
    Surface(
        modifier = modifier,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = LocalSpacing.current.s, vertical = LocalSpacing.current.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.xs)
        ) {
            IconButton(onClick = onSkipPrevious) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous"
                )
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Block else Icons.AutoMirrored.Rounded.Reply,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
            IconButton(onClick = onStop) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Stop"
                )
            }
            IconButton(onClick = onSkipNext) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next"
                )
            }
            if (currentResNum != null) {
                Text(
                    text = "No.$currentResNum",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = LocalSpacing.current.s)
                )
            }
        }
    }
}

/**
 * 再読み込み/メディア一覧/NG管理等のクイックアクションバー。
 */
@Composable
internal fun DetailQuickActions(
    modifier: Modifier = Modifier,
    onReload: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenNg: () -> Unit,
    onImageEdit: (() -> Unit)?,
    onTtsStart: (() -> Unit)?,
) {
    val spacing = LocalSpacing.current
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(spacing.s)
    ) {
        FilledTonalButton(onClick = onReload) {
            Icon(Icons.Rounded.Refresh, contentDescription = "再読み込み", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(spacing.xs))
            Text("再読み込み")
        }
        FilledTonalButton(onClick = onOpenMedia) {
            Icon(Icons.Rounded.Image, contentDescription = "メディア一覧", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(spacing.xs))
            Text("メディア一覧")
        }
        OutlinedButton(onClick = onOpenNg) {
            Icon(Icons.Rounded.Block, contentDescription = "NG管理", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(spacing.xs))
            Text("NG管理")
        }
        if (onImageEdit != null) {
            OutlinedButton(onClick = onImageEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "画像編集", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(spacing.xs))
                Text("画像編集")
            }
        }
        if (onTtsStart != null) {
            OutlinedButton(onClick = onTtsStart) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "読み上げ", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(spacing.xs))
                Text("読み上げ")
            }
        }
    }
}

/**
 * シンプルなバナー広告ホスト（Google Mobile Ads の `AdView`）。
 * 実測高さを `onHeightChanged` で通知する。
 */
@Composable
internal fun AdBanner(adUnitId: String, onHeightChanged: (Int) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            com.google.android.gms.ads.AdView(ctx).apply {
                setAdSize(com.google.android.gms.ads.AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
                viewTreeObserver.addOnGlobalLayoutListener {
                    onHeightChanged(measuredHeight)
                }
            }
        },
        update = { v: com.google.android.gms.ads.AdView -> onHeightChanged(v.measuredHeight) }
    )
}

/**
 * DetailScreen のトップバーに表示するオーバーフローメニュー。
 * 基本操作・ダウンロード・その他機能の3グループに分けて表示する。
 */
@Composable
internal fun DetailToolbarDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onJumpToBottom: () -> Unit,
    onReload: () -> Unit,
    onOpenMediaSheet: () -> Unit,
    onBulkDownloadImages: (() -> Unit)?,
    onBulkDownloadPromptImages: (() -> Unit)?,
    onArchiveThread: (() -> Unit)?,
    onTtsStart: (() -> Unit)?,
    onOpenNg: () -> Unit,
    onImageEdit: (() -> Unit)?,
    promptFeaturesEnabled: Boolean,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        // 基本操作グループ
        DropdownMenuItem(
            text = { Text("一番下まで飛ぶ") },
            leadingIcon = { Icon(Icons.Rounded.KeyboardDoubleArrowDown, contentDescription = "一番下まで飛ぶ") },
            onClick = { onDismiss(); onJumpToBottom() }
        )
        DropdownMenuItem(
            text = { Text("再読み込み") },
            leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = "再読み込み") },
            onClick = { onDismiss(); onReload() }
        )
        DropdownMenuItem(
            text = { Text("メディア一覧") },
            leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = "メディア一覧") },
            onClick = { onDismiss(); onOpenMediaSheet() }
        )

        // ダウンロード関連グループ
        DropdownMenuItem(
            text = { Text("画像一括ダウンロード") },
            leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = "ダウンロード") },
            onClick = { onDismiss(); onBulkDownloadImages?.invoke() }
        )
        if (promptFeaturesEnabled) {
            DropdownMenuItem(
                text = { Text("プロンプト付き画像DL") },
                leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = "ダウンロード") },
                onClick = { onDismiss(); onBulkDownloadPromptImages?.invoke() }
            )
        }
        if (onArchiveThread != null) {
            DropdownMenuItem(
                text = { Text("スレッド保存") },
                leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = "ダウンロード") },
                onClick = { onDismiss(); onArchiveThread() }
            )
        }

        // その他機能グループ
        DropdownMenuItem(
            text = { Text("音声読み上げ") },
            leadingIcon = { Icon(Icons.Rounded.RecordVoiceOver, contentDescription = "音声読み上げ") },
            onClick = { onDismiss(); onTtsStart?.invoke() }
        )
        DropdownMenuItem(
            text = { Text("NG 管理") },
            leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = "NG管理") },
            onClick = { onDismiss(); onOpenNg() }
        )
        if (onImageEdit != null) {
            DropdownMenuItem(
                text = { Text("画像編集") },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = "編集") },
                onClick = { onDismiss(); onImageEdit() }
            )
        }
    }
}
