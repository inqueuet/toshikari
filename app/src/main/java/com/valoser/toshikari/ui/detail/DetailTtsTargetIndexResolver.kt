package com.valoser.toshikari.ui.detail

import com.valoser.toshikari.DetailContent

/**
 * TTS 再生中レス番号からスクロール対象 index を解決する補助。
 */
internal object DetailTtsTargetIndexResolver {
    fun resolve(items: List<DetailContent>, resNum: String?): Int? {
        if (resNum.isNullOrBlank()) return null

        val index = items.indexOfFirst { item ->
            item is DetailContent.Text && item.resNum == resNum
        }
        return index.takeIf { it >= 0 }
    }
}
