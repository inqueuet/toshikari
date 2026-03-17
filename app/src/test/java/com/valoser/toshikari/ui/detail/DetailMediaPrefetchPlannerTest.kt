package com.valoser.toshikari.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailMediaPrefetchPlannerTest {
    @Test
    fun plan_returnsAheadAndBackRangesWithinBounds() {
        val plan = DetailMediaPrefetchPlanner.plan(
            firstVisibleIndex = 5,
            lastVisibleIndex = 8,
            lastItemIndex = 20,
            aheadCount = 12,
            backCount = 6
        )

        assertEquals(9..20, plan.aheadIndices)
        assertEquals(0..4, plan.backIndices)
    }

    @Test
    fun plan_returnsNullRangeWhenNothingToPrefetch() {
        val plan = DetailMediaPrefetchPlanner.plan(
            firstVisibleIndex = 0,
            lastVisibleIndex = 4,
            lastItemIndex = 4,
            aheadCount = 12,
            backCount = 6
        )

        assertEquals(null, plan.aheadIndices)
        assertEquals(null, plan.backIndices)
    }

    @Test
    fun plan_returnsNullsForEmptyList() {
        val plan = DetailMediaPrefetchPlanner.plan(
            firstVisibleIndex = 0,
            lastVisibleIndex = -1,
            lastItemIndex = -1,
            aheadCount = 12,
            backCount = 6
        )

        assertEquals(DetailMediaPrefetchPlan(null, null), plan)
    }
}
