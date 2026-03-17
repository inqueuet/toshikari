package com.valoser.toshikari.metadata

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class PngPromptExtractorTest {

    // PNG シグネチャ
    private val PNG_SIG = byteArrayOf(
        137.toByte(), 80, 78, 71, 13, 10, 26, 10
    )

    /** PNG チャンクを組み立てるヘルパー。CRC は簡略化（テスト用に 0 固定）。 */
    private fun buildChunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val lenBuf = ByteBuffer.allocate(4).putInt(data.size).array()
        out.write(lenBuf)
        out.write(type.toByteArray(StandardCharsets.US_ASCII))
        out.write(data)
        out.write(ByteArray(4)) // CRC placeholder
        return out.toByteArray()
    }

    /** 最小 IHDR チャンク (13 バイトデータ) を作成。 */
    private fun buildIHDR(width: Int = 1, height: Int = 1): ByteArray {
        val data = ByteBuffer.allocate(13)
            .putInt(width)
            .putInt(height)
            .put(8.toByte())  // bitDepth
            .put(2.toByte())  // colorType (RGB)
            .put(0.toByte())  // compression
            .put(0.toByte())  // filter
            .put(0.toByte())  // interlace
            .array()
        return buildChunk("IHDR", data)
    }

    private fun buildTEXt(key: String, value: String): ByteArray {
        val data = ByteArrayOutputStream()
        data.write(key.toByteArray(StandardCharsets.ISO_8859_1))
        data.write(0) // null separator
        data.write(value.toByteArray(StandardCharsets.ISO_8859_1))
        return buildChunk("tEXt", data.toByteArray())
    }

    private fun buildIEND(): ByteArray = buildChunk("IEND", ByteArray(0))

    @Test
    fun `tEXt の parameters キーからプロンプトを抽出できる`() {
        val png = ByteArrayOutputStream()
        png.write(PNG_SIG)
        png.write(buildIHDR())
        png.write(buildTEXt("parameters", "masterpiece, best quality, 1girl"))
        png.write(buildIEND())

        val result = PngPromptExtractor.extractFromPngChunks(png.toByteArray())
        assertNotNull(result)
        assertTrue(result!!.contains("masterpiece"))
    }

    @Test
    fun `tEXt の prompt キーからプロンプトを抽出できる`() {
        val png = ByteArrayOutputStream()
        png.write(PNG_SIG)
        png.write(buildIHDR())
        png.write(buildTEXt("prompt", "a starry night sky"))
        png.write(buildIEND())

        val result = PngPromptExtractor.extractFromPngChunks(png.toByteArray())
        assertNotNull(result)
        assertTrue(result!!.contains("starry night"))
    }

    @Test
    fun `tEXt の Description キーからプロンプトを抽出できる`() {
        val png = ByteArrayOutputStream()
        png.write(PNG_SIG)
        png.write(buildIHDR())
        png.write(buildTEXt("Description", "a photo of a sunset"))
        png.write(buildIEND())

        val result = PngPromptExtractor.extractFromPngChunks(png.toByteArray())
        assertNotNull(result)
        assertTrue(result!!.contains("sunset"))
    }

    @Test
    fun `tEXt の Comment キーからプロンプトを抽出できる`() {
        val png = ByteArrayOutputStream()
        png.write(PNG_SIG)
        png.write(buildIHDR())
        png.write(buildTEXt("Comment", "a detailed illustration"))
        png.write(buildIEND())

        val result = PngPromptExtractor.extractFromPngChunks(png.toByteArray())
        assertNotNull(result)
        assertTrue(result!!.contains("illustration"))
    }

    @Test
    fun `UNICODE のみの値は除外する`() {
        val png = ByteArrayOutputStream()
        png.write(PNG_SIG)
        png.write(buildIHDR())
        png.write(buildTEXt("parameters", "UNICODE"))
        png.write(buildIEND())

        val result = PngPromptExtractor.extractFromPngChunks(png.toByteArray())
        assertNull(result)
    }

    @Test
    fun `関連キーがない場合は null を返す`() {
        val png = ByteArrayOutputStream()
        png.write(PNG_SIG)
        png.write(buildIHDR())
        png.write(buildTEXt("Software", "GIMP"))
        png.write(buildIEND())

        val result = PngPromptExtractor.extractFromPngChunks(png.toByteArray())
        assertNull(result)
    }

    @Test
    fun `PNG シグネチャがないバイト配列では null`() {
        val result = PngPromptExtractor.extractFromPngChunks(byteArrayOf(0, 1, 2, 3))
        assertNull(result)
    }

    @Test
    fun `空のバイト配列では null`() {
        val result = PngPromptExtractor.extractFromPngChunks(byteArrayOf())
        assertNull(result)
    }

    @Test
    fun `複数のプロンプトキーがある場合は結合する`() {
        val png = ByteArrayOutputStream()
        png.write(PNG_SIG)
        png.write(buildIHDR())
        png.write(buildTEXt("parameters", "first prompt value"))
        png.write(buildTEXt("prompt", "second prompt value"))
        png.write(buildIEND())

        val result = PngPromptExtractor.extractFromPngChunks(png.toByteArray())
        assertNotNull(result)
        assertTrue(result!!.contains("first prompt"))
        assertTrue(result.contains("second prompt"))
    }

    @Test
    fun `XMP tEXt から prompt 属性を抽出できる`() {
        val xmp = """<x:xmpmeta><rdf:Description sd:prompt="anime style girl" /></x:xmpmeta>"""
        val png = ByteArrayOutputStream()
        png.write(PNG_SIG)
        png.write(buildIHDR())
        png.write(buildTEXt("XML:com.adobe.xmp", xmp))
        png.write(buildIEND())

        val result = PngPromptExtractor.extractFromPngChunks(png.toByteArray())
        assertNotNull(result)
        assertTrue(result!!.contains("anime"))
    }
}
