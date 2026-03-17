package com.valoser.toshikari

internal data class DetailPendingDownloadRequest<T>(
    val id: Long,
    val urls: List<String>,
    val newUrls: List<String>,
    val existingByUrl: Map<String, List<T>>
) {
    val existingCount: Int
        get() = existingByUrl.values.sumOf { it.size }
}

internal enum class DetailDownloadConflictResolution {
    SkipExisting,
    OverwriteExisting
}

internal object DetailBulkDownloadPlanner {
    fun <T> createPendingRequest(
        requestId: Long,
        urls: List<String>,
        existingByUrl: Map<String, List<T>>
    ): DetailPendingDownloadRequest<T> {
        return DetailPendingDownloadRequest(
            id = requestId,
            urls = urls,
            newUrls = urls.filterNot(existingByUrl::containsKey),
            existingByUrl = existingByUrl
        )
    }

    fun <T> buildConflictRequest(
        pending: DetailPendingDownloadRequest<T>,
        fileNameOf: (T) -> String
    ): DownloadConflictRequest {
        val conflictFiles = pending.existingByUrl
            .flatMap { (url, entries) ->
                entries.map { entry ->
                    DownloadConflictFile(url = url, fileName = fileNameOf(entry))
                }
            }
            .sortedBy { it.fileName }

        return DownloadConflictRequest(
            requestId = pending.id,
            totalCount = pending.urls.size,
            newCount = pending.newUrls.size,
            existingFiles = conflictFiles
        )
    }

    fun selectUrlsToDownload(
        pending: DetailPendingDownloadRequest<*>,
        resolution: DetailDownloadConflictResolution
    ): List<String> {
        return when (resolution) {
            DetailDownloadConflictResolution.SkipExisting -> pending.newUrls
            DetailDownloadConflictResolution.OverwriteExisting -> pending.urls
        }
    }

    fun shouldShowConflictDialog(pending: DetailPendingDownloadRequest<*>): Boolean {
        return pending.existingByUrl.isNotEmpty()
    }

    fun hasNoDownloadTargets(
        pending: DetailPendingDownloadRequest<*>,
        resolution: DetailDownloadConflictResolution
    ): Boolean {
        return selectUrlsToDownload(pending, resolution).isEmpty()
    }
}
