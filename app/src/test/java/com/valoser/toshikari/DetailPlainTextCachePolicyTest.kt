package com.valoser.toshikari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailPlainTextCachePolicyTest {
    @Test
    fun addMissing_appendsOnlyUnknownEntries() {
        val updated = DetailPlainTextCachePolicy.addMissing(
            current = linkedMapOf("a" to "A"),
            missingEntries = listOf("a" to "ignored", "b" to "B")
        )

        assertEquals(mapOf("a" to "A", "b" to "B"), updated)
    }

    @Test
    fun addMissing_returnsNullWhenNothingChanges() {
        val updated = DetailPlainTextCachePolicy.addMissing(
            current = mapOf("a" to "A"),
            missingEntries = listOf("a" to "A2")
        )

        assertNull(updated)
    }

    @Test
    fun put_returnsNullWhenKeyAlreadyExists() {
        val updated = DetailPlainTextCachePolicy.put(
            current = mapOf("a" to "A"),
            id = "a",
            plainText = "new"
        )

        assertNull(updated)
    }

    @Test
    fun put_trimsToRetainedTailWhenCacheGetsTooLarge() {
        val current = linkedMapOf<String, String>().apply {
            repeat(500) { index -> put("id$index", "value$index") }
        }

        val updated = DetailPlainTextCachePolicy.put(
            current = current,
            id = "id500",
            plainText = "value500"
        )

        requireNotNull(updated)
        assertEquals(300, updated.size)
        assertEquals("value201", updated["id201"])
        assertEquals("value500", updated["id500"])
    }
}
