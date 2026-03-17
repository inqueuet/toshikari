package com.valoser.toshikari.metadata

import com.valoser.toshikari.metadata.MetadataByteUtils.containsChunkType
import com.valoser.toshikari.metadata.MetadataByteUtils.readBytesLimited
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class MetadataByteUtilsTest {

    // ====== isPng ======

    @Test
    fun `正しい PNG シグネチャを検出する`() {
        val png = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        assertTrue(MetadataByteUtils.isPng(png))
    }

    @Test
    fun `不正なバイト配列は PNG でない`() {
        assertFalse(MetadataByteUtils.isPng(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)))
    }

    @Test
    fun `短すぎるバイト配列は PNG でない`() {
        assertFalse(MetadataByteUtils.isPng(byteArrayOf(137.toByte(), 80, 78)))
    }

    @Test
    fun `空のバイト配列は PNG でない`() {
        assertFalse(MetadataByteUtils.isPng(byteArrayOf()))
    }

    // ====== indexOfZero ======

    @Test
    fun `ゼロバイトの位置を見つける`() {
        val data = byteArrayOf(1, 2, 0, 3)
        assertEquals(2, MetadataByteUtils.indexOfZero(data, 0))
    }

    @Test
    fun `開始位置以降のゼロバイトを見つける`() {
        val data = byteArrayOf(0, 1, 2, 0, 3)
        assertEquals(3, MetadataByteUtils.indexOfZero(data, 1))
    }

    @Test
    fun `ゼロバイトがない場合は -1`() {
        val data = byteArrayOf(1, 2, 3, 4)
        assertEquals(-1, MetadataByteUtils.indexOfZero(data, 0))
    }

    @Test
    fun `開始位置が配列外の場合は -1`() {
        val data = byteArrayOf(1, 2, 0)
        assertEquals(-1, MetadataByteUtils.indexOfZero(data, 10))
    }

    // ====== decompress ======

    @Test
    fun `zlib 圧縮データを伸長できる`() {
        val original = "Hello, World! This is a test of zlib compression."
        val compressed = java.io.ByteArrayOutputStream().use { baos ->
            java.util.zip.DeflaterOutputStream(baos).use { dos ->
                dos.write(original.toByteArray())
            }
            baos.toByteArray()
        }
        val decompressed = MetadataByteUtils.decompress(compressed)
        assertEquals(original, String(decompressed))
    }

    // ====== containsChunkType ======

    @Test
    fun `チャンクタイプを検出する`() {
        val data = byteArrayOf(0, 0, 0, 0, 'I'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte(), 'D'.code.toByte())
        assertTrue(data.containsChunkType("IEND"))
    }

    @Test
    fun `チャンクタイプがない場合は false`() {
        val data = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        assertFalse(data.containsChunkType("IEND"))
    }

    @Test
    fun `空のバイト配列では false`() {
        assertFalse(byteArrayOf().containsChunkType("IEND"))
    }

    // ====== readBytesLimited ======

    @Test
    fun `上限以内のデータをすべて読み取る`() {
        val data = "Hello, World!".toByteArray()
        val input = ByteArrayInputStream(data)
        val result = input.readBytesLimited(1000)
        assertArrayEquals(data, result)
    }

    @Test
    fun `上限でデータを切り詰める`() {
        val data = "Hello, World!".toByteArray()
        val input = ByteArrayInputStream(data)
        val result = input.readBytesLimited(5)
        assertEquals(5, result.size)
        assertEquals("Hello", String(result))
    }
}
