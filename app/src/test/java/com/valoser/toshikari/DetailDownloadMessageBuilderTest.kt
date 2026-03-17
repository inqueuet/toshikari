package com.valoser.toshikari

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailDownloadMessageBuilderTest {
    @Test
    fun buildNoTargetMessage_prefersExistingCountMessage() {
        assertEquals(
            "3件の画像は既にダウンロード済みでした",
            DetailDownloadMessageBuilder.buildNoTargetMessage(3)
        )
        assertEquals(
            "ダウンロード対象の画像がありません",
            DetailDownloadMessageBuilder.buildNoTargetMessage(0)
        )
    }

    @Test
    fun buildSkipMessage_coversDownloadAndSkipPatterns() {
        assertEquals(
            "新規ダウンロード: 2件、スキップ: 1件",
            DetailDownloadMessageBuilder.buildSkipMessage(downloadedCount = 2, skippedCount = 1)
        )
        assertEquals(
            "1件の画像をダウンロードしました",
            DetailDownloadMessageBuilder.buildSkipMessage(downloadedCount = 1, skippedCount = 0)
        )
        assertEquals(
            "4件の画像は既にダウンロード済みでした",
            DetailDownloadMessageBuilder.buildSkipMessage(downloadedCount = 0, skippedCount = 4)
        )
    }

    @Test
    fun buildOverwriteMessage_coversMixedAndFailurePatterns() {
        assertEquals(
            "保存完了: 3件（新規: 2件、上書き: 1件、失敗: 4件）",
            DetailDownloadMessageBuilder.buildOverwriteMessage(newSuccess = 2, overwriteSuccess = 1, failureCount = 4)
        )
        assertEquals(
            "既存ファイルを2件上書き保存しました",
            DetailDownloadMessageBuilder.buildOverwriteMessage(newSuccess = 0, overwriteSuccess = 2, failureCount = 0)
        )
        assertEquals(
            "画像の保存に失敗しました",
            DetailDownloadMessageBuilder.buildOverwriteMessage(newSuccess = 0, overwriteSuccess = 0, failureCount = 1)
        )
    }
}
