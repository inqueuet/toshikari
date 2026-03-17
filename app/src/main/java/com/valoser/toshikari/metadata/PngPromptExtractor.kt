package com.valoser.toshikari.metadata

import com.valoser.toshikari.metadata.MetadataByteUtils.containsChunkType
import com.valoser.toshikari.metadata.MetadataByteUtils.readBytesLimited
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import java.util.zip.InflaterInputStream

/**
 * PNG チャンク (tEXt / zTXt / iTXt / c2pa) からプロンプトを抽出する。
 *
 * NovelAI Stealth PNGInfo (アルファ LSB) のデコードも含む。
 */
internal object PngPromptExtractor {

    private val PROMPT_KEYS = setOf("parameters", "Description", "Comment", "prompt")

    private val RE_NOVELAI_SOFTWARE: Pattern = Pattern.compile(
        """"software"\s*:\s*"NovelAI"""",
        Pattern.CASE_INSENSITIVE
    )
    private val RE_JSON_BRACE: Pattern = Pattern.compile("""\{[\s\S]*?\}""", Pattern.DOTALL)

    private fun isPromptKey(key: String): Boolean {
        val t = key.trim()
        return PROMPT_KEYS.any { it.equals(t, ignoreCase = true) }
    }

    /**
     * PNG バイト配列からチャンクを走査してプロンプトを抽出する。
     * IEND チャンク到達で打ち切り。見つからなければ NovelAI Stealth を試行。
     */
    fun extractFromPngChunks(bytes: ByteArray): String? {
        if (!MetadataByteUtils.isPng(bytes)) return null
        val prompts = mutableListOf<String>()
        var offset = 8

        while (offset + 12 <= bytes.size) {
            val length = ByteBuffer.wrap(bytes, offset, 4).int
            if (length < 0 || offset + 12 + length > bytes.size) break

            val type = String(bytes, offset + 4, 4, StandardCharsets.US_ASCII)
            val dataStart = offset + 8
            val dataEnd = dataStart + length
            val data = bytes.copyOfRange(dataStart, dataEnd)

            when (type) {
                "tEXt" -> parseTEXt(data, prompts)
                "zTXt" -> parseZTXt(data, prompts)
                "iTXt" -> parseITXt(data, prompts)
                "c2pa" -> PromptTextScanner.extractPromptFromC2paData(data)?.let { prompts += it }
                "IEND" -> return prompts.joinToString("\n\n").ifEmpty { null }
            }
            offset += 12 + length
        }
        if (prompts.isEmpty()) {
            decodeNovelAIAlphaStego(bytes)?.let { return it }
        }
        return prompts.joinToString("\n\n").ifEmpty { null }
    }

    private fun parseTEXt(data: ByteArray, prompts: MutableList<String>) {
        val nul = data.indexOf(0.toByte())
        if (nul <= 0) return
        val key = String(data, 0, nul, StandardCharsets.ISO_8859_1)
        val value = String(data, nul + 1, data.size - (nul + 1), StandardCharsets.ISO_8859_1)
        if (key.equals("XML:com.adobe.xmp", ignoreCase = true)) {
            PromptTextScanner.scanXmpForPrompts(value)?.let { prompts += it }
        } else if (isPromptKey(key)) {
            if (value.isNotBlank() && value.trim() != "UNICODE") prompts += value
        }
    }

    private fun parseZTXt(data: ByteArray, prompts: MutableList<String>) {
        val nul = data.indexOf(0.toByte())
        if (nul <= 0 || nul + 1 >= data.size) return
        val key = String(data, 0, nul, StandardCharsets.ISO_8859_1)
        val isTarget = key.equals("XML:com.adobe.xmp", ignoreCase = true) || isPromptKey(key)
        if (!isTarget) return
        val compressed = data.copyOfRange(nul + 2, data.size)
        val valueBytes = MetadataByteUtils.decompress(compressed)
        val value = valueBytes.toString(StandardCharsets.UTF_8)
        if (key.equals("XML:com.adobe.xmp", ignoreCase = true)) {
            PromptTextScanner.scanXmpForPrompts(value)?.let { prompts += it }
        } else if (isPromptKey(key)) {
            if (value.isNotBlank() && value.trim() != "UNICODE") prompts += value
        }
    }

    private fun parseITXt(data: ByteArray, prompts: MutableList<String>) {
        val nul = data.indexOf(0.toByte())
        if (nul <= 0 || nul + 2 >= data.size) return
        val key = String(data, 0, nul, StandardCharsets.ISO_8859_1)
        val compFlag = data[nul + 1].toInt() and 0xFF
        var p = nul + 3

        val langEnd = MetadataByteUtils.indexOfZero(data, p)
        if (langEnd == -1) {
            processITXtValue(key, compFlag, data.copyOfRange(p, data.size), prompts)
        } else {
            p = langEnd + 1
            val transEnd = MetadataByteUtils.indexOfZero(data, p)
            if (transEnd == -1) {
                processITXtValue(key, compFlag, data.copyOfRange(p, data.size), prompts)
            } else {
                p = transEnd + 1
                if (p <= data.size) {
                    processITXtValue(key, compFlag, data.copyOfRange(p, data.size), prompts)
                }
            }
        }
    }

    private fun processITXtValue(key: String, compFlag: Int, textField: ByteArray, prompts: MutableList<String>) {
        val isTarget = key.equals("XML:com.adobe.xmp", ignoreCase = true) || isPromptKey(key)
        if (!isTarget) return
        val valueBytes = if (compFlag == 1) MetadataByteUtils.decompress(textField) else textField
        val value = valueBytes.toString(StandardCharsets.UTF_8)
        if (key.equals("XML:com.adobe.xmp", ignoreCase = true)) {
            PromptTextScanner.scanXmpForPrompts(value)?.let { prompts += it }
        } else if (isPromptKey(key) && value.isNotBlank() && value.trim() != "UNICODE") {
            prompts += value
        }
    }

    // ====== NovelAI Stealth PNGInfo (アルファLSB) ======

    private data class IHDR(val width: Int, val height: Int, val bitDepth: Int, val colorType: Int, val interlace: Int)

    /**
     * NovelAI Stealth PNGInfo をアルファ LSB からデコードする。
     * 仕様は非公開のため、ビット順を複数仮定してJSONを探索するベストエフォート実装。
     */
    fun decodeNovelAIAlphaStego(png: ByteArray): String? {
        val ihdr = readIHDR(png) ?: return null
        if (ihdr.interlace != 0 || ihdr.bitDepth != 8) return null
        val channels = when (ihdr.colorType) {
            6 -> 4 // RGBA
            4 -> 2 // Gray+Alpha
            else -> return null
        }
        val bpp = channels

        val idat = collectIDATData(png)
        val decompressed = try {
            InflaterInputStream(ByteArrayInputStream(idat)).use {
                it.readBytesLimited(10 * 1024 * 1024)
            }
        } catch (_: Exception) { return null }

        val rowSize = ihdr.width * bpp
        val expected = ihdr.height * (1 + rowSize)
        if (decompressed.size < expected) return null

        val out = unfilterRows(decompressed, ihdr.width, ihdr.height, rowSize, bpp) ?: return null
        val alpha = extractAlphaChannel(out, ihdr.width, ihdr.height, rowSize, channels)

        val bits = IntArray(alpha.size) { alpha[it].toInt() and 1 }
        val candidates = listOf(
            bitsToBytes(bits, lsbFirst = true),
            bitsToBytes(bits, lsbFirst = false)
        )

        for (c in candidates) {
            tryParseNovelAIPayload(c)?.let { return it }
        }
        return null
    }

    private fun readIHDR(png: ByteArray): IHDR? {
        if (!MetadataByteUtils.isPng(png)) return null
        var p = 8
        while (p + 12 <= png.size) {
            val len = ByteBuffer.wrap(png, p, 4).int
            if (len < 0 || p + 12 + len > png.size) break
            val type = String(png, p + 4, 4, StandardCharsets.US_ASCII)
            if (type == "IHDR" && len >= 13) {
                val w = ByteBuffer.wrap(png, p + 8, 4).int
                val h = ByteBuffer.wrap(png, p + 12, 4).int
                val bitDepth = png[p + 16].toInt() and 0xFF
                val colorType = png[p + 17].toInt() and 0xFF
                val interlace = png[p + 20].toInt() and 0xFF
                return IHDR(w, h, bitDepth, colorType, interlace)
            }
            p += 12 + len
        }
        return null
    }

    private fun collectIDATData(png: ByteArray): ByteArray {
        val idat = ByteArrayOutputStream()
        var p = 8
        while (p + 12 <= png.size) {
            val len = ByteBuffer.wrap(png, p, 4).int
            if (len < 0 || p + 12 + len > png.size) break
            val type = String(png, p + 4, 4, StandardCharsets.US_ASCII)
            if (type == "IDAT" && len > 0) {
                idat.write(png, p + 8, len)
            }
            p += 12 + len
        }
        return idat.toByteArray()
    }

    private fun unfilterRows(decompressed: ByteArray, width: Int, height: Int, rowSize: Int, bpp: Int): ByteArray? {
        val out = ByteArray(height * rowSize)
        fun paeth(a: Int, b: Int, c: Int): Int {
            val p = a + b - c
            val pa = kotlin.math.abs(p - a)
            val pb = kotlin.math.abs(p - b)
            val pc = kotlin.math.abs(p - c)
            return when {
                pa <= pb && pa <= pc -> a
                pb <= pc -> b
                else -> c
            }
        }
        var src = 0
        for (y in 0 until height) {
            val filter = decompressed[src].toInt() and 0xFF
            src += 1
            val dstRow = y * rowSize
            when (filter) {
                0 -> System.arraycopy(decompressed, src, out, dstRow, rowSize)
                1 -> for (x in 0 until rowSize) {
                    val left = if (x >= bpp) out[dstRow + x - bpp].toInt() and 0xFF else 0
                    out[dstRow + x] = (((decompressed[src + x].toInt() and 0xFF) + left) and 0xFF).toByte()
                }
                2 -> for (x in 0 until rowSize) {
                    val up = if (y > 0) out[dstRow - rowSize + x].toInt() and 0xFF else 0
                    out[dstRow + x] = (((decompressed[src + x].toInt() and 0xFF) + up) and 0xFF).toByte()
                }
                3 -> for (x in 0 until rowSize) {
                    val left = if (x >= bpp) out[dstRow + x - bpp].toInt() and 0xFF else 0
                    val up = if (y > 0) out[dstRow - rowSize + x].toInt() and 0xFF else 0
                    out[dstRow + x] = (((decompressed[src + x].toInt() and 0xFF) + ((left + up) / 2)) and 0xFF).toByte()
                }
                4 -> for (x in 0 until rowSize) {
                    val a = if (x >= bpp) out[dstRow + x - bpp].toInt() and 0xFF else 0
                    val b = if (y > 0) out[dstRow - rowSize + x].toInt() and 0xFF else 0
                    val c = if (y > 0 && x >= bpp) out[dstRow - rowSize + x - bpp].toInt() and 0xFF else 0
                    out[dstRow + x] = (((decompressed[src + x].toInt() and 0xFF) + paeth(a, b, c)) and 0xFF).toByte()
                }
                else -> return null
            }
            src += rowSize
        }
        return out
    }

    private fun extractAlphaChannel(pixels: ByteArray, width: Int, height: Int, rowSize: Int, channels: Int): ByteArray {
        val alpha = ByteArray(width * height)
        var i = 0
        for (y in 0 until height) {
            val row = y * rowSize
            for (x in 0 until width) {
                val idx = when (channels) {
                    4 -> row + x * 4 + 3
                    2 -> row + x * 2 + 1
                    else -> return alpha
                }
                alpha[i++] = pixels[idx]
            }
        }
        return alpha
    }

    private fun bitsToBytes(bits: IntArray, lsbFirst: Boolean): ByteArray {
        val outB = ByteArray(bits.size / 8)
        var bi = 0
        var acc = 0
        var count = 0
        for (b in bits) {
            acc = if (lsbFirst) acc or ((b and 1) shl count) else (acc shl 1) or (b and 1)
            count++
            if (count == 8) {
                outB[bi++] = (if (lsbFirst) acc else acc and 0xFF).toByte()
                acc = 0
                count = 0
                if (bi >= outB.size) break
            }
        }
        return outB
    }

    private fun tryParseNovelAIPayload(bytes: ByteArray): String? {
        val s = try { String(bytes, StandardCharsets.UTF_8) } catch (_: Exception) { return null }
        val m = RE_JSON_BRACE.matcher(s)
        while (m.find()) {
            val cand = s.substring(m.start(), m.end())
            if (RE_NOVELAI_SOFTWARE.matcher(cand).find()) {
                PromptTextScanner.scanTextForPrompts(cand)?.let { return it }
                if (cand.trim() != "UNICODE") return cand
            }
        }
        return null
    }
}
