package com.valoser.toshikari.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.valoser.toshikari.DetailContent
import com.valoser.toshikari.ui.theme.LocalSpacing

/**
 * Detail 一覧系の共通ボトムシート。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailItemsBottomSheet(
    items: List<DetailContent>,
    onDismissRequest: () -> Unit,
    threadUrl: String?,
    lowBandwidthMode: Boolean,
    promptLoadingIds: Set<String>,
    plainTextCache: Map<String, String>,
    plainTextOf: (DetailContent.Text) -> String,
    onQuoteClick: ((String) -> Unit)?,
    onImageLoaded: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp
        val maxHeight = with(LocalDensity.current) { (screenHeight * 0.9f).dp }
        Box(modifier = Modifier.heightIn(max = maxHeight)) {
            DetailListCompose(
                items = items,
                searchQuery = null,
                threadUrl = threadUrl,
                useLowBandwidthThumbnails = lowBandwidthMode,
                modifier = Modifier.wrapContentHeight(),
                promptLoadingIds = promptLoadingIds,
                plainTextCache = plainTextCache,
                plainTextOf = plainTextOf,
                onQuoteClick = onQuoteClick,
                onSodaneClick = null,
                onThreadEndTimeClick = null,
                onResNumClick = null,
                onResNumConfirmClick = null,
                onResNumDelClick = null,
                onIdClick = null,
                onBodyClick = null,
                onAddNgFromBody = null,
                getSodaneState = { false },
                sodaneCounts = emptyMap(),
                onSetSodaneCount = null,
                onImageLoaded = onImageLoaded,
                onVisibleMaxOrdinal = null,
                contentPadding = PaddingValues(
                    horizontal = LocalSpacing.current.s,
                    vertical = LocalSpacing.current.s
                )
            )
        }
    }
}
