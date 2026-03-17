package com.valoser.toshikari

internal data class DetailBulkDownloadStats(
    val resolution: Resolution,
    val skippedCount: Int,
    val newSuccess: Int,
    val overwriteSuccess: Int,
    val failureCount: Int
) {
    enum class Resolution {
        SkipExisting,
        OverwriteExisting
    }

    fun recordResult(success: Boolean, hadExistingFile: Boolean): DetailBulkDownloadStats {
        return when {
            success && hadExistingFile && resolution == Resolution.OverwriteExisting ->
                copy(overwriteSuccess = overwriteSuccess + 1)
            success ->
                copy(newSuccess = newSuccess + 1)
            resolution == Resolution.SkipExisting ->
                copy(skippedCount = skippedCount + 1)
            else ->
                copy(failureCount = failureCount + 1)
        }
    }

    fun buildMessage(): String {
        return when (resolution) {
            Resolution.SkipExisting ->
                DetailDownloadMessageBuilder.buildSkipMessage(newSuccess, skippedCount)
            Resolution.OverwriteExisting ->
                DetailDownloadMessageBuilder.buildOverwriteMessage(newSuccess, overwriteSuccess, failureCount)
        }
    }

    companion object {
        fun initial(resolution: Resolution, existingCount: Int): DetailBulkDownloadStats {
            return DetailBulkDownloadStats(
                resolution = resolution,
                skippedCount = if (resolution == Resolution.SkipExisting) existingCount else 0,
                newSuccess = 0,
                overwriteSuccess = 0,
                failureCount = 0
            )
        }
    }
}
