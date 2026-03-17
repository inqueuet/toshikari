package com.valoser.toshikari.metadata

import java.nio.charset.StandardCharsets

/**
 * JPEG の APP セグメント (APP1: XMP, APP13: Photoshop IRB/IPTC) からプロンプトを抽出する。
 */
internal object JpegSegmentExtractor {

    /**
     * JPEG バイト配列の APP セグメントを走査してプロンプトを抽出する。
     * APP1 (XMP) → APP13 (IPTC) の順に試行。
     */
    fun extractFromJpegAppSegments(bytes: ByteArray): String? {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null
        var p = 2
        while (p + 4 <= bytes.size) {
            if (bytes[p] != 0xFF.toByte()) { p++; continue }
            val marker = bytes[p + 1].toInt() and 0xFF
            p += 2
            if (marker == 0xD9 || marker == 0xDA) break // EOI/SOS
            if (p + 2 > bytes.size) break
            val len = ((bytes[p].toInt() and 0xFF) shl 8) or (bytes[p + 1].toInt() and 0xFF)
            p += 2
            val dataLen = len - 2
            if (p + dataLen > bytes.size || dataLen <= 0) break
            val seg = bytes.copyOfRange(p, p + dataLen)
            when (marker) {
                0xE1 -> { // APP1: XMP (またはEXIF)
                    val xmpPrefix = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(StandardCharsets.ISO_8859_1)
                    if (seg.size > xmpPrefix.size && seg.copyOfRange(0, xmpPrefix.size).contentEquals(xmpPrefix)) {
                        val xmpBytes = seg.copyOfRange(xmpPrefix.size, seg.size)
                        val xmpStr = try { String(xmpBytes, StandardCharsets.UTF_8) } catch (_: Exception) { String(xmpBytes, StandardCharsets.ISO_8859_1) }
                        PromptTextScanner.scanXmpForPrompts(xmpStr)?.let { return it }
                    }
                }
                0xED -> { // APP13: Photoshop IRB (IPTC)
                    parsePhotoshopIrbForIptc(seg)?.let { return it }
                }
            }
            p += dataLen
        }
        return null
    }

    /**
     * Photoshop IRB ブロックから IPTC(IIM) を取り出し、説明に相当する項目を抽出する。
     */
    fun parsePhotoshopIrbForIptc(app13: ByteArray): String? {
        val header = "Photoshop 3.0\u0000".toByteArray(StandardCharsets.ISO_8859_1)
        if (app13.size < header.size || !app13.copyOfRange(0, header.size).contentEquals(header)) return null
        var p = header.size
        while (p + 12 <= app13.size) {
            if (app13[p] != '8'.code.toByte() || app13[p + 1] != 'B'.code.toByte() || app13[p + 2] != 'I'.code.toByte() || app13[p + 3] != 'M'.code.toByte()) break
            p += 4
            if (p + 2 > app13.size) break
            val resId = ((app13[p].toInt() and 0xFF) shl 8) or (app13[p + 1].toInt() and 0xFF)
            p += 2
            if (p >= app13.size) break
            val nameLen = app13[p].toInt() and 0xFF
            p += 1
            val nameEnd = (p + nameLen).coerceAtMost(app13.size)
            p = nameEnd
            if ((1 + nameLen) % 2 == 1) p += 1
            if (p + 4 > app13.size) break
            val size = ((app13[p].toInt() and 0xFF) shl 24) or ((app13[p + 1].toInt() and 0xFF) shl 16) or ((app13[p + 2].toInt() and 0xFF) shl 8) or (app13[p + 3].toInt() and 0xFF)
            p += 4
            if (p + size > app13.size) break
            val data = app13.copyOfRange(p, p + size)
            p += size
            if (size % 2 == 1) p += 1
            if (resId == 0x0404) {
                parseIptcIimForPrompt(data)?.let { return it }
            }
        }
        return null
    }

    /**
     * IPTC IIM の見出し(2:xxx)から説明/キャプション相当を抽出する。
     */
    fun parseIptcIimForPrompt(data: ByteArray): String? {
        var p = 0
        while (p + 5 <= data.size) {
            if (data[p] != 0x1C.toByte()) { p++; continue }
            val rec = data[p + 1].toInt() and 0xFF
            val dset = data[p + 2].toInt() and 0xFF
            val len = ((data[p + 3].toInt() and 0xFF) shl 8) or (data[p + 4].toInt() and 0xFF)
            p += 5
            if (p + len > data.size) break
            val valueBytes = data.copyOfRange(p, p + len)
            p += len
            if (rec == 2 && (dset == 120 || dset == 105 || dset == 116 || dset == 122)) {
                val str = try { String(valueBytes, StandardCharsets.UTF_8) } catch (_: Exception) { String(valueBytes, StandardCharsets.ISO_8859_1) }
                PromptTextScanner.scanTextForPrompts(str)?.let { return it }
                if (str.isNotBlank() && !WorkflowPromptExtractor.isLabely(str) && str.trim() != "UNICODE") return str.trim()
            }
        }
        return null
    }
}
