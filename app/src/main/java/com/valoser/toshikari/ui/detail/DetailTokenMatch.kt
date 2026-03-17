package com.valoser.toshikari.ui.detail

/**
 * テキスト中のトークン検出結果の共通インターフェース。
 * すべての TokenFinder はこのインターフェースを実装する match データクラスを返す。
 */
internal interface DetailTokenMatch {
    /** マッチ開始位置（inclusive）。 */
    val start: Int
    /** マッチ終了位置（exclusive）。 */
    val end: Int
}

/**
 * テキストからトークンを検出する共通インターフェース。
 */
internal interface DetailTokenFinder<T : DetailTokenMatch> {
    fun findMatches(text: String): List<T>
}
