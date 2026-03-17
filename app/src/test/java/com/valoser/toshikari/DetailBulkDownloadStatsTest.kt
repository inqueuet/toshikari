package com.valoser.toshikari

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailBulkDownloadStatsTest {
    @Test
    fun initial_skipExistingStartsWithExistingCount() {
        val stats = DetailBulkDownloadStats.initial(
            resolution = DetailBulkDownloadStats.Resolution.SkipExisting,
            existingCount = 3
        )

        assertEquals(3, stats.skippedCount)
        assertEquals(0, stats.newSuccess)
        assertEquals(0, stats.overwriteSuccess)
        assertEquals(0, stats.failureCount)
    }

    @Test
    fun recordResult_skipExistingTreatsFailureAsSkipped() {
        val stats = DetailBulkDownloadStats.initial(
            resolution = DetailBulkDownloadStats.Resolution.SkipExisting,
            existingCount = 1
        )
            .recordResult(success = true, hadExistingFile = false)
            .recordResult(success = false, hadExistingFile = true)

        assertEquals(2, stats.skippedCount)
        assertEquals(1, stats.newSuccess)
        assertEquals("新規ダウンロード: 1件、スキップ: 2件", stats.buildMessage())
    }

    @Test
    fun recordResult_overwriteSeparatesOverwriteAndFailureCounts() {
        val stats = DetailBulkDownloadStats.initial(
            resolution = DetailBulkDownloadStats.Resolution.OverwriteExisting,
            existingCount = 5
        )
            .recordResult(success = true, hadExistingFile = true)
            .recordResult(success = true, hadExistingFile = false)
            .recordResult(success = false, hadExistingFile = true)

        assertEquals(0, stats.skippedCount)
        assertEquals(1, stats.newSuccess)
        assertEquals(1, stats.overwriteSuccess)
        assertEquals(1, stats.failureCount)
        assertEquals(
            "保存完了: 2件（新規: 1件、上書き: 1件、失敗: 1件）",
            stats.buildMessage()
        )
    }
}
