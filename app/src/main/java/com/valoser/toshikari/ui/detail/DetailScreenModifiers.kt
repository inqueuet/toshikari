/**
 * DetailScreen から分離した Modifier 拡張と LazyListState ユーティリティ。
 *
 * - bottomPullRefresh: リスト末尾での下方向ドラッグで更新をトリガーする Modifier
 * - isScrolledToEnd: LazyListState が末尾までスクロール済みかを判定する拡張
 */
package com.valoser.toshikari.ui.detail

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * リスト末尾に到達した状態で下方向へドラッグすると [onRefresh] をトリガーする Modifier。
 * プルリフレッシュの逆方向版（「もっと読む」用途）。
 */
internal fun Modifier.bottomPullRefresh(
    listState: LazyListState,
    isRefreshing: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
    triggerDistance: Dp = 72.dp,
): Modifier = composed {
    if (!enabled) {
        return@composed this
    }
    val refreshed = rememberUpdatedState(onRefresh)
    val refreshingState = rememberUpdatedState(isRefreshing)
    val triggerPx = with(LocalDensity.current) { triggerDistance.toPx() }
    var dragAccumulated by remember { mutableFloatStateOf(0f) }
    var gestureTriggered by remember { mutableStateOf(false) }

    val reset: () -> Unit = {
        dragAccumulated = 0f
        gestureTriggered = false
    }

    LaunchedEffect(refreshingState.value) {
        if (!refreshingState.value) {
            reset()
        }
    }

    val connection = remember(listState, triggerPx, enabled) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!enabled) {
                    reset()
                    return Offset.Zero
                }
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y >= 0f) {
                    reset()
                    return Offset.Zero
                }
                if (!listState.isScrolledToEnd()) {
                    reset()
                    return Offset.Zero
                }
                dragAccumulated += -available.y
                if (!gestureTriggered && !refreshingState.value && dragAccumulated >= triggerPx) {
                    gestureTriggered = true
                    refreshed.value.invoke()
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!enabled) {
                    reset()
                    return Offset.Zero
                }
                if (source == NestedScrollSource.UserInput) {
                    val changedDirection = available.y > 0f || consumed.y > 0f
                    if (changedDirection || !listState.isScrolledToEnd()) {
                        reset()
                    }
                } else if (!listState.isScrolledToEnd()) {
                    reset()
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                reset()
                return Velocity.Zero
            }
        }
    }

    this.nestedScroll(connection)
}

/**
 * LazyListState が末尾までスクロール済みかを判定する。
 * afterContentPadding を考慮して正確に判定する。
 */
internal fun LazyListState.isScrolledToEnd(): Boolean {
    val layout = layoutInfo
    if (layout.totalItemsCount == 0) return false
    val lastVisible = layout.visibleItemsInfo.lastOrNull() ?: return false
    if (lastVisible.index < layout.totalItemsCount - 1) return false
    val effectiveEnd = (layout.viewportEndOffset - layout.afterContentPadding)
        .coerceAtMost(layout.viewportEndOffset)
        .coerceAtLeast(0)
    val itemEnd = lastVisible.offset + lastVisible.size
    return itemEnd >= effectiveEnd
}
