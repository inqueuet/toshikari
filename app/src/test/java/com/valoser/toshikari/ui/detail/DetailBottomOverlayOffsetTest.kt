package com.valoser.toshikari.ui.detail

import com.valoser.toshikari.ui.common.AppBarPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailBottomOverlayOffsetTest {
    @Test
    fun totalPx_addsBottomBarAndNavBarOnlyForBottomAppBar() {
        assertEquals(
            180,
            DetailBottomOverlayOffset.totalPx(
                baseBottomPx = 100,
                appBarPosition = AppBarPosition.BOTTOM,
                bottomBarHeightPx = 50,
                navigationBarHeightPx = 30
            )
        )
    }

    @Test
    fun totalPx_keepsBaseOffsetForTopAppBar() {
        assertEquals(
            100,
            DetailBottomOverlayOffset.totalPx(
                baseBottomPx = 100,
                appBarPosition = AppBarPosition.TOP,
                bottomBarHeightPx = 50,
                navigationBarHeightPx = 30
            )
        )
    }
}
