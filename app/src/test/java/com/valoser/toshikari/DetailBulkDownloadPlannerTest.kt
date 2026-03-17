package com.valoser.toshikari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailBulkDownloadPlannerTest {
    private data class ExistingStub(val fileName: String)

    @Test
    fun createPendingRequest_splitsExistingAndNewUrls() {
        val existing = mapOf(
            "https://example.com/a.jpg" to listOf(ExistingStub("a.jpg"))
        )

        val pending = DetailBulkDownloadPlanner.createPendingRequest(
            requestId = 10L,
            urls = listOf("https://example.com/a.jpg", "https://example.com/b.jpg"),
            existingByUrl = existing
        )

        assertEquals(10L, pending.id)
        assertEquals(listOf("https://example.com/b.jpg"), pending.newUrls)
        assertEquals(1, pending.existingCount)
    }

    @Test
    fun buildConflictRequest_flattensAndSortsExistingFiles() {
        val pending = DetailPendingDownloadRequest(
            id = 11L,
            urls = listOf("u1", "u2"),
            newUrls = listOf("u2"),
            existingByUrl = mapOf(
                "u1" to listOf(ExistingStub("b.jpg"), ExistingStub("a.jpg"))
            )
        )

        val request = DetailBulkDownloadPlanner.buildConflictRequest(pending) { it.fileName }

        assertEquals(11L, request.requestId)
        assertEquals(2, request.totalCount)
        assertEquals(1, request.newCount)
        assertEquals(listOf("a.jpg", "b.jpg"), request.existingFiles.map { it.fileName })
    }

    @Test
    fun planner_selectsUrlsAndDetectsMissingTargetsByResolution() {
        val pending = DetailPendingDownloadRequest(
            id = 1L,
            urls = listOf("u1", "u2"),
            newUrls = listOf("u2"),
            existingByUrl = mapOf("u1" to listOf(ExistingStub("a.jpg")))
        )

        assertEquals(
            listOf("u2"),
            DetailBulkDownloadPlanner.selectUrlsToDownload(pending, DetailDownloadConflictResolution.SkipExisting)
        )
        assertEquals(
            listOf("u1", "u2"),
            DetailBulkDownloadPlanner.selectUrlsToDownload(pending, DetailDownloadConflictResolution.OverwriteExisting)
        )
        assertFalse(
            DetailBulkDownloadPlanner.hasNoDownloadTargets(pending, DetailDownloadConflictResolution.SkipExisting)
        )
        assertTrue(DetailBulkDownloadPlanner.shouldShowConflictDialog(pending))
    }

    @Test
    fun planner_handlesNoConflictsAndNoTargets() {
        val noConflict = DetailPendingDownloadRequest(
            id = 2L,
            urls = listOf("u1"),
            newUrls = listOf("u1"),
            existingByUrl = emptyMap<String, List<ExistingStub>>()
        )
        val noTarget = DetailPendingDownloadRequest(
            id = 3L,
            urls = listOf("u1"),
            newUrls = emptyList(),
            existingByUrl = mapOf("u1" to listOf(ExistingStub("a.jpg")))
        )

        assertFalse(DetailBulkDownloadPlanner.shouldShowConflictDialog(noConflict))
        assertTrue(
            DetailBulkDownloadPlanner.hasNoDownloadTargets(noTarget, DetailDownloadConflictResolution.SkipExisting)
        )
    }

}
