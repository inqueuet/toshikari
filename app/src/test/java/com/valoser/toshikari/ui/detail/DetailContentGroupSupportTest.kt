package com.valoser.toshikari.ui.detail

import com.valoser.toshikari.DetailContent
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailContentGroupSupportTest {
    @Test
    fun collectGroupAt_returnsTextWithFollowingMediaUntilNextText() {
        val items = listOf(
            DetailContent.Text(id = "t1", htmlContent = "No.1", resNum = "1"),
            DetailContent.Image(id = "i1", imageUrl = "a.jpg"),
            DetailContent.Video(id = "v1", videoUrl = "a.mp4"),
            DetailContent.Text(id = "t2", htmlContent = "No.2", resNum = "2")
        )

        val group = DetailContentGroupSupport.collectGroupAt(items, 0)

        assertEquals(listOf(items[0], items[1], items[2]), group)
    }

    @Test
    fun collectGroupsAt_ignoresOutOfRangeIndexes() {
        val items = listOf(
            DetailContent.Text(id = "t1", htmlContent = "No.1", resNum = "1"),
            DetailContent.Image(id = "i1", imageUrl = "a.jpg")
        )

        val groups = DetailContentGroupSupport.collectGroupsAt(items, listOf(0, 9))

        assertEquals(listOf(listOf(items[0], items[1])), groups)
    }

    @Test
    fun regroupFlatItems_splitsOnTextBoundaries() {
        val items = listOf(
            DetailContent.Text(id = "t1", htmlContent = "No.1", resNum = "1"),
            DetailContent.Image(id = "i1", imageUrl = "a.jpg"),
            DetailContent.Text(id = "t2", htmlContent = "No.2", resNum = "2"),
            DetailContent.Video(id = "v2", videoUrl = "b.mp4")
        )

        val groups = DetailContentGroupSupport.regroupFlatItems(items)

        assertEquals(
            listOf(
                listOf(items[0], items[1]),
                listOf(items[2], items[3])
            ),
            groups
        )
    }

    @Test
    fun flattenDistinctGroups_deduplicatesByGroupHeadAndItemId() {
        val t1 = DetailContent.Text(id = "t1", htmlContent = "No.1", resNum = "1")
        val i1 = DetailContent.Image(id = "i1", imageUrl = "a.jpg")
        val t2 = DetailContent.Text(id = "t2", htmlContent = "No.2", resNum = "2")

        val result = DetailContentGroupSupport.flattenDistinctGroups(
            listOf(
                listOf(t1, i1),
                listOf(t1, i1),
                listOf(t2, i1)
            )
        )

        assertEquals(listOf(t1, i1, t2), result)
    }

    @Test
    fun mergeDistinctItems_preservesFirstOccurrenceOrder() {
        val t1 = DetailContent.Text(id = "t1", htmlContent = "No.1", resNum = "1")
        val i1 = DetailContent.Image(id = "i1", imageUrl = "a.jpg")
        val t2 = DetailContent.Text(id = "t2", htmlContent = "No.2", resNum = "2")

        val result = DetailContentGroupSupport.mergeDistinctItems(
            listOf(t1, i1, t1, t2, i1)
        )

        assertEquals(listOf(t1, i1, t2), result)
    }
}
