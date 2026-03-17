package com.valoser.toshikari

internal object DetailDownloadMessageBuilder {
    fun buildNoTargetMessage(existingCount: Int): String {
        return if (existingCount > 0) {
            "${existingCount}件の画像は既にダウンロード済みでした"
        } else {
            "ダウンロード対象の画像がありません"
        }
    }

    fun buildSkipMessage(downloadedCount: Int, skippedCount: Int): String {
        return when {
            downloadedCount > 0 && skippedCount > 0 -> "新規ダウンロード: ${downloadedCount}件、スキップ: ${skippedCount}件"
            downloadedCount > 0 -> "${downloadedCount}件の画像をダウンロードしました"
            skippedCount > 0 -> "${skippedCount}件の画像は既にダウンロード済みでした"
            else -> "ダウンロード対象の画像がありません"
        }
    }

    fun buildOverwriteMessage(newSuccess: Int, overwriteSuccess: Int, failureCount: Int): String {
        val totalSuccess = newSuccess + overwriteSuccess
        return when {
            totalSuccess > 0 && failureCount > 0 -> "保存完了: ${totalSuccess}件（新規: ${newSuccess}件、上書き: ${overwriteSuccess}件、失敗: ${failureCount}件）"
            totalSuccess > 0 && overwriteSuccess > 0 && newSuccess > 0 -> "保存完了: ${totalSuccess}件（新規: ${newSuccess}件、上書き: ${overwriteSuccess}件）"
            totalSuccess > 0 && overwriteSuccess > 0 -> "既存ファイルを${overwriteSuccess}件上書き保存しました"
            totalSuccess > 0 -> "${totalSuccess}件の画像をダウンロードしました"
            failureCount > 0 -> "画像の保存に失敗しました"
            else -> "ダウンロード対象の画像がありません"
        }
    }
}
