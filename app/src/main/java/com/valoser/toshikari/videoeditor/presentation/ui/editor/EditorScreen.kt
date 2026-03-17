package com.valoser.toshikari.videoeditor.presentation.ui.editor

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valoser.toshikari.videoeditor.domain.model.ExportCompression
import com.valoser.toshikari.videoeditor.domain.model.ExportOptions
import com.valoser.toshikari.videoeditor.domain.model.Resolution
import com.valoser.toshikari.videoeditor.presentation.viewmodel.EditorViewModel
import com.valoser.toshikari.videoeditor.presentation.ui.components.TimelineView
import com.valoser.toshikari.videoeditor.presentation.ui.components.PreviewView

import com.valoser.toshikari.videoeditor.presentation.ui.components.MainToolbar
import com.valoser.toshikari.videoeditor.presentation.ui.components.InspectorPanel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Slider
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

/**
 * エディタ画面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val sessionFlow = remember(viewModel) {
        viewModel.state.map { it.session }.distinctUntilChanged()
    }
    val selectionFlow = remember(viewModel) {
        viewModel.state.map { it.selection }.distinctUntilChanged()
    }
    val isPlayingFlow = remember(viewModel) {
        viewModel.state.map { it.isPlaying }.distinctUntilChanged()
    }
    val zoomFlow = remember(viewModel) {
        viewModel.state.map { it.zoom }.distinctUntilChanged()
    }
    val isLoadingFlow = remember(viewModel) {
        viewModel.state.map { it.isLoading }.distinctUntilChanged()
    }
    val exportProgressFlow = remember(viewModel) {
        viewModel.state.map { it.exportProgress }.distinctUntilChanged()
    }
    val errorFlow = remember(viewModel) {
        viewModel.state.map { it.error }.distinctUntilChanged()
    }
    val modeFlow = remember(viewModel) {
        viewModel.state.map { it.mode }.distinctUntilChanged()
    }
    val rangeSelectionFlow = remember(viewModel) {
        viewModel.state.map { it.rangeSelection }.distinctUntilChanged()
    }
    val splitMarkerPositionFlow = remember(viewModel) {
        viewModel.state.map { it.splitMarkerPosition }.distinctUntilChanged()
    }

    val session by sessionFlow.collectAsStateWithLifecycle(initialValue = null)
    val selection by selectionFlow.collectAsStateWithLifecycle(initialValue = null)
    val isPlaying by isPlayingFlow.collectAsStateWithLifecycle(initialValue = false)
    val zoom by zoomFlow.collectAsStateWithLifecycle(initialValue = 1f)
    val isLoading by isLoadingFlow.collectAsStateWithLifecycle(initialValue = false)
    val exportProgress by exportProgressFlow.collectAsStateWithLifecycle(initialValue = null)
    val error by errorFlow.collectAsStateWithLifecycle(initialValue = null)
    val mode by modeFlow.collectAsStateWithLifecycle(
        initialValue = com.valoser.toshikari.videoeditor.domain.model.EditMode.NORMAL
    )
    val rangeSelection by rangeSelectionFlow.collectAsStateWithLifecycle(initialValue = null)
    val splitMarkerPosition by splitMarkerPositionFlow.collectAsStateWithLifecycle(initialValue = null)

    var showExportDialog by remember { mutableStateOf(false) }
    var pendingExportOptions by remember { mutableStateOf(ExportOptions()) }
    var exportResolution by rememberSaveable(session?.id) {
        mutableStateOf(session?.settings?.resolution ?: Resolution.HD1080)
    }
    var exportCompression by rememberSaveable(session?.id) {
        mutableStateOf(ExportCompression.STANDARD)
    }
    var showTransitionDialog by remember { mutableStateOf(false) }
    var selectedTransitionPosition by remember { mutableStateOf(0L) }

    // エクスポート先ファイル選択ランチャー
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri: Uri? ->
        uri?.let { outputUri ->
            viewModel.handleIntent(
                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.Export(
                    outputUri = outputUri,
                    exportOptions = pendingExportOptions
                )
            )
        }
    }

    val addAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { audioUri ->
            viewModel.handleIntent(
                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.AddAudioTrack(
                    name = "New Audio",
                    audioUri = audioUri,
                    position = viewModel.state.value.playhead
                )
            )
        }
    }

    val replaceAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { audioUri ->
            val selection = viewModel.state.value.selection as? com.valoser.toshikari.videoeditor.domain.model.Selection.AudioClip
            val range = viewModel.state.value.rangeSelection
            if (selection != null && range != null) {
                viewModel.handleIntent(
                    com.valoser.toshikari.videoeditor.domain.model.EditorIntent.ReplaceAudio(
                        selection.trackId,
                        selection.clipId,
                        range.start.value,
                        range.end.value,
                        audioUri
                    )
                )
            }
            viewModel.handleIntent(com.valoser.toshikari.videoeditor.domain.model.EditorIntent.SetEditMode(com.valoser.toshikari.videoeditor.domain.model.EditMode.NORMAL))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("動画編集") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // バックアイコン
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Undoボタン
                    IconButton(
                        onClick = {
                            viewModel.handleIntent(com.valoser.toshikari.videoeditor.domain.model.EditorIntent.Undo)
                        },
                        enabled = session != null && !isLoading
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }

                    // Redoボタン
                    IconButton(
                        onClick = {
                            viewModel.handleIntent(com.valoser.toshikari.videoeditor.domain.model.EditorIntent.Redo)
                        },
                        enabled = session != null && !isLoading
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }

                    TextButton(
                        onClick = { showExportDialog = true },
                        enabled = session != null && !isLoading
                    ) {
                        Text("書き出し")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            PreviewView(
                player = viewModel.playerEngine.player,
                isPlaying = isPlaying,
                onPlayPause = {
                    if (isPlaying) {
                        viewModel.handleIntent(com.valoser.toshikari.videoeditor.domain.model.EditorIntent.Pause)
                    } else {
                        viewModel.handleIntent(com.valoser.toshikari.videoeditor.domain.model.EditorIntent.Play)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
            )

            HorizontalDivider()

            // タイムライン操作バー（48dp）
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    // 最初へ移動
                    IconButton(
                        onClick = {
                            viewModel.handleIntent(
                                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.SeekTo(0L)
                            )
                        },
                        enabled = session != null
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Seek to Start")
                    }

                    // ズームアウト
                    IconButton(
                        onClick = {
                            val newZoom = (zoom - 0.25f).coerceAtLeast(0.25f)
                            viewModel.handleIntent(
                                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.SetZoom(newZoom)
                            )
                        },
                        enabled = zoom > 0.25f
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                    }

                    // ズーム表示
                    Text(
                        text = "ズーム: ${String.format("%.2f", zoom)}x",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // ズームイン
                    IconButton(
                        onClick = {
                            val newZoom = (zoom + 0.25f).coerceAtMost(4f)
                            viewModel.handleIntent(
                                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.SetZoom(newZoom)
                            )
                        },
                        enabled = zoom < 4f
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                    }

                    // 範囲削除ボタン
                    if (mode == com.valoser.toshikari.videoeditor.domain.model.EditMode.RANGE_SELECT
                        && rangeSelection != null) {
                        IconButton(onClick = {
                            val r = rangeSelection!!
                            viewModel.handleIntent(
                                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.DeleteTimeRange(
                                    start = r.start.value,
                                    end = r.end.value,
                                    ripple = true
                                )
                            )
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "範囲削除")
                        }
                    }

                    // 最後へ移動
                    IconButton(
                        onClick = {
                            session?.let { currentSession ->
                                viewModel.handleIntent(
                                    com.valoser.toshikari.videoeditor.domain.model.EditorIntent.SeekTo(currentSession.duration)
                                )
                            }
                        },
                        enabled = session != null
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Seek to End")
                    }
                }
            }

            // タイムライン（残り）
            session?.let { currentSession ->
                EditorTimelinePane(
                    viewModel = viewModel,
                    session = currentSession,
                    selection = selection,
                    isPlaying = isPlaying,
                    zoom = zoom,
                    splitMarkerPosition = splitMarkerPosition,
                    mode = mode,
                    rangeSelection = rangeSelection,
                    onTransitionClick = { position ->
                        selectedTransitionPosition = position
                        showTransitionDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f)
                )
            }

            HorizontalDivider()

            // Main toolbar (always visible)
            MainToolbar(
                onSplitClick = {
                    viewModel.handleIntent(com.valoser.toshikari.videoeditor.domain.model.EditorIntent.SplitAtPlayhead)
                },
                onAddAudioClick = {
                    addAudioLauncher.launch("audio/*")
                }
            )

            // Inspector panel (slides in when a clip is selected)
            AnimatedVisibility(visible = selection != null) {
                val audioSelection = selection as? com.valoser.toshikari.videoeditor.domain.model.Selection.AudioClip
                val selectedAudioClip = audioSelection?.let { sel ->
                    session?.audioTracks?.find { it.id == sel.trackId }?.clips?.find { it.id == sel.clipId }
                }

                InspectorPanel(
                    selection = selection,
                    clipVolume = selectedAudioClip?.volume,
                    isClipMuted = selectedAudioClip?.muted,
                    onDeleteClick = {
                        Log.d("EditorScreen", "Delete button clicked, selection: $selection")
                        when (val currentSelection = selection) {
                            is com.valoser.toshikari.videoeditor.domain.model.Selection.VideoClip -> {
                                Log.d("EditorScreen", "Deleting video clip: ${currentSelection.clipId}")
                                viewModel.handleIntent(
                                    com.valoser.toshikari.videoeditor.domain.model.EditorIntent.DeleteClip(currentSelection.clipId)
                                )
                            }
                            is com.valoser.toshikari.videoeditor.domain.model.Selection.AudioClip -> {
                                Log.d("EditorScreen", "Deleting audio clip: ${currentSelection.clipId}")
                                viewModel.handleIntent(
                                    com.valoser.toshikari.videoeditor.domain.model.EditorIntent.DeleteAudioClip(
                                        currentSelection.trackId,
                                        currentSelection.clipId
                                    )
                                )
                            }
                            else -> {
                                Log.w("EditorScreen", "Delete clicked but selection is: $currentSelection")
                            }
                        }
                        viewModel.handleIntent(com.valoser.toshikari.videoeditor.domain.model.EditorIntent.ClearSelection)
                    },
                    onCopyClick = {
                        when (val currentSelection = selection) {
                            is com.valoser.toshikari.videoeditor.domain.model.Selection.VideoClip -> {
                                viewModel.handleIntent(
                                    com.valoser.toshikari.videoeditor.domain.model.EditorIntent.CopyClip(currentSelection.clipId)
                                )
                            }
                            is com.valoser.toshikari.videoeditor.domain.model.Selection.AudioClip -> {
                                viewModel.handleIntent(
                                    com.valoser.toshikari.videoeditor.domain.model.EditorIntent.CopyAudioClip(
                                        currentSelection.trackId,
                                        currentSelection.clipId
                                    )
                                )
                            }
                            else -> {}
                        }
                    },
                    onFadeInChange = { duration ->
                        if (audioSelection != null) {
                            viewModel.handleIntent(
                                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.AddFade(
                                    audioSelection.trackId,
                                    audioSelection.clipId,
                                    com.valoser.toshikari.videoeditor.domain.model.FadeType.FADE_IN,
                                    duration
                                )
                            )
                        }
                    },
                    onFadeOutChange = { duration ->
                        if (audioSelection != null) {
                            viewModel.handleIntent(
                                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.AddFade(
                                    audioSelection.trackId,
                                    audioSelection.clipId,
                                    com.valoser.toshikari.videoeditor.domain.model.FadeType.FADE_OUT,
                                    duration
                                )
                            )
                        }
                    },
                    onToggleMute = {
                        if (audioSelection != null) {
                            viewModel.handleIntent(
                                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.ToggleMuteAudioClip(
                                    audioSelection.trackId,
                                    audioSelection.clipId
                                )
                            )
                        }
                    },
                    onVolumeChange = { volume ->
                        if (audioSelection != null) {
                            viewModel.handleIntent(
                                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.SetVolume(
                                    audioSelection.trackId,
                                    audioSelection.clipId,
                                    volume
                                )
                            )
                        }
                    }
                )
            }
        }
    }

    // エラー表示
    error?.let { message ->
        LaunchedEffect(message) {
            // Snackbarなどで表示
        }
    }

    // トランジション設定ダイアログ
    if (showTransitionDialog) {
        val transitionDurations = listOf(300L, 500L, 1000L)
        AlertDialog(
            onDismissRequest = { showTransitionDialog = false },
            title = { Text("トランジション設定") },
            text = {
                Column {
                    Text("クロスフェードの長さを選択してください。")
                    Row {
                        transitionDurations.forEach { duration ->
                            TextButton(onClick = {
                                val clip = session?.videoClips?.find { it.position + it.duration == selectedTransitionPosition }
                                if (clip != null) {
                                    viewModel.handleIntent(
                                        com.valoser.toshikari.videoeditor.domain.model.EditorIntent.AddTransition(
                                            clipId = clip.id,
                                            type = com.valoser.toshikari.videoeditor.domain.model.TransitionType.CROSSFADE,
                                            duration = duration
                                        )
                                    )
                                }
                                showTransitionDialog = false
                            }) {
                                Text("${duration / 1000f}s")
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showExportDialog) {
        val currentSession = session
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("書き出し設定") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("解像度")
                    Resolution.entries.forEach { resolution ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = exportResolution == resolution,
                                onClick = { exportResolution = resolution }
                            )
                            Text("${resolution.width} x ${resolution.height}")
                        }
                    }

                    HorizontalDivider()

                    Text("圧縮率")
                    ExportCompression.entries.forEach { compression ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = exportCompression == compression,
                                onClick = { exportCompression = compression }
                            )
                            Text(compression.displayName)
                        }
                    }

                    val previewOptions = ExportOptions(
                        resolution = exportResolution,
                        compression = exportCompression,
                        frameRate = currentSession?.settings?.fps ?: 30,
                        audioSampleRate = currentSession?.settings?.sampleRate ?: 48_000
                    )
                    HorizontalDivider()
                    Text("出力: ${previewOptions.width} x ${previewOptions.height}")
                    Text("映像ビットレート: ${formatBitrate(previewOptions.videoBitrate)}")
                    Text("音声ビットレート: ${formatBitrate(previewOptions.audioBitrate)}")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingExportOptions = ExportOptions(
                            resolution = exportResolution,
                            compression = exportCompression,
                            frameRate = currentSession?.settings?.fps ?: 30,
                            audioSampleRate = currentSession?.settings?.sampleRate ?: 48_000
                        )
                        showExportDialog = false
                        exportLauncher.launch("edited_video.mp4")
                    },
                    enabled = currentSession != null
                ) {
                    Text("保存先を選択")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // ローディング表示
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val clampedProgress = exportProgress?.coerceIn(0f, 100f)
                if (clampedProgress != null) {
                    CircularProgressIndicator(progress = { clampedProgress / 100f })
                    Text("エクスポート中... ${clampedProgress.roundToInt()}%")
                } else {
                    CircularProgressIndicator()
                    Text("処理中...")
                }
            }
        }
    }
}

private fun formatBitrate(bitsPerSecond: Int): String {
    val mbps = bitsPerSecond / 1_000_000f
    return if (mbps >= 1f) {
        String.format("%.1f Mbps", mbps)
    } else {
        String.format("%d kbps", bitsPerSecond / 1000)
    }
}

@Composable
private fun EditorTimelinePane(
    viewModel: EditorViewModel,
    session: com.valoser.toshikari.videoeditor.domain.model.EditorSession,
    selection: com.valoser.toshikari.videoeditor.domain.model.Selection?,
    isPlaying: Boolean,
    zoom: Float,
    splitMarkerPosition: Long?,
    mode: com.valoser.toshikari.videoeditor.domain.model.EditMode,
    rangeSelection: com.valoser.toshikari.videoeditor.domain.model.TimeRange?,
    onTransitionClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val playheadFlow = remember(viewModel) {
        viewModel.state.map { it.playhead }.distinctUntilChanged()
    }
    val playhead by playheadFlow.collectAsStateWithLifecycle(initialValue = 0L)

    TimelineView(
        waveformGenerator = viewModel.waveformGenerator,
        session = session,
        selection = selection,
        playhead = playhead,
        isPlaying = isPlaying,
        zoom = zoom,
        splitMarkerPosition = splitMarkerPosition,
        onClipSelected = { clipId ->
            viewModel.handleIntent(
                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.SelectClip(
                    com.valoser.toshikari.videoeditor.domain.model.Selection.VideoClip(clipId)
                )
            )
        },
        onClipTrimmed = { clipId, start, end ->
            viewModel.handleIntent(
                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.TrimClip(
                    clipId, start, end
                )
            )
        },
        onClipMoved = { clipId, position ->
            viewModel.handleIntent(
                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.MoveClip(
                    clipId, position
                )
            )
        },
        onAudioClipSelected = { trackId, clipId ->
            viewModel.handleIntent(
                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.SelectClip(
                    com.valoser.toshikari.videoeditor.domain.model.Selection.AudioClip(trackId, clipId)
                )
            )
        },
        onAudioClipTrimmed = { trackId, clipId, start, end ->
            viewModel.handleIntent(
                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.TrimAudioClip(
                    trackId, clipId, start, end
                )
            )
        },
        onAudioClipMoved = { trackId, clipId, position ->
            viewModel.handleIntent(
                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.MoveAudioClip(
                    trackId, clipId, position
                )
            )
        },
        onZoomChange = { newZoom ->
            viewModel.handleIntent(
                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.SetZoom(newZoom)
            )
        },
        onSeek = { target ->
            viewModel.handleIntent(
                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.SeekTo(target)
            )
        },
        onMarkerClick = { marker ->
            viewModel.handleIntent(
                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.RemoveMarker(marker.time)
            )
        },
        onKeyframeClick = { trackId, clipId, keyframe ->
            viewModel.handleIntent(
                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.RemoveVolumeKeyframe(
                    trackId,
                    clipId,
                    keyframe
                )
            )
        },
        mode = mode,
        rangeSelection = rangeSelection,
        onRangeChange = { start, end ->
            viewModel.handleIntent(
                com.valoser.toshikari.videoeditor.domain.model.EditorIntent.SetRangeSelection(start, end)
            )
        },
        onTransitionClick = onTransitionClick,
        modifier = modifier
    )
}
