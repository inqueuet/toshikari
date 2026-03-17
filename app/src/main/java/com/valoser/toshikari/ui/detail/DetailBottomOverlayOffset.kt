package com.valoser.toshikari.ui.detail

import com.valoser.toshikari.ui.common.AppBarPosition

/**
 * 下部オーバーレイ用の bottom offset を計算する補助。
 */
internal object DetailBottomOverlayOffset {
    fun totalPx(
        baseBottomPx: Int,
        appBarPosition: AppBarPosition,
        bottomBarHeightPx: Int,
        navigationBarHeightPx: Int,
    ): Int {
        val appBarOffset = if (appBarPosition == AppBarPosition.BOTTOM) {
            bottomBarHeightPx + navigationBarHeightPx
        } else {
            0
        }
        return baseBottomPx + appBarOffset
    }
}
