package com.valoser.toshikari.ui.detail

internal data class DetailMediaPrefetchPlan(
    val aheadIndices: IntRange?,
    val backIndices: IntRange?,
)

/**
 * メディアグリッドの可視範囲から先読み対象 index を求める補助。
 */
internal object DetailMediaPrefetchPlanner {
    fun plan(
        firstVisibleIndex: Int,
        lastVisibleIndex: Int,
        lastItemIndex: Int,
        aheadCount: Int,
        backCount: Int,
    ): DetailMediaPrefetchPlan {
        if (lastItemIndex < 0) {
            return DetailMediaPrefetchPlan(aheadIndices = null, backIndices = null)
        }

        val aheadStart = (lastVisibleIndex + 1).coerceAtLeast(0)
        val aheadEnd = (lastVisibleIndex + aheadCount).coerceAtMost(lastItemIndex)
        val backStart = (firstVisibleIndex - backCount).coerceAtLeast(0)
        val backEnd = (firstVisibleIndex - 1).coerceAtLeast(-1)

        return DetailMediaPrefetchPlan(
            aheadIndices = if (aheadStart <= aheadEnd) aheadStart..aheadEnd else null,
            backIndices = if (backStart <= backEnd) backStart..backEnd else null,
        )
    }
}
