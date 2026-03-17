package com.valoser.toshikari.metadata

import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/**
 * EXIF メタデータからプロンプト文字列を抽出する。
 *
 * UserComment / ImageDescription / XPComment の順に試行し、
 * 最初に見つかった有効な値を返す。
 */
internal object ExifPromptExtractor {

    /**
     * バイト配列から EXIF を解析してプロンプトを返す。
     * 見つからない場合は null。
     */
    fun extractFromExif(fileBytes: ByteArray): String? {
        return try {
            val exif = ExifInterface(ByteArrayInputStream(fileBytes))
            listOf(
                exif.getAttribute(ExifInterface.TAG_USER_COMMENT)?.let { decodeUserComment(it) },
                exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION),
                exif.getAttribute("XPComment")?.let { decodeXpString(it) }
            ).firstOrNull { !it.isNullOrBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * EXIF UserComment のエンコーディングヘッダーを考慮してデコードする。
     *
     * UNICODE / ASCII / JIS マーカーを判別し、8バイト目以降を適切な文字コードで復号。
     * マーカーなしの場合はそのまま返す。
     */
    fun decodeUserComment(raw: String): String? {
        if (raw.length < 8) return raw.takeIf { it.isNotBlank() }

        val bytes = raw.toByteArray(StandardCharsets.ISO_8859_1)
        return try {
            when {
                // UNICODE (UTF-16) エンコーディングマーカー
                bytes.size >= 8 &&
                bytes[0] == 'U'.code.toByte() &&
                bytes[1] == 'N'.code.toByte() &&
                bytes[2] == 'I'.code.toByte() &&
                bytes[3] == 'C'.code.toByte() &&
                bytes[4] == 'O'.code.toByte() &&
                bytes[5] == 'D'.code.toByte() &&
                bytes[6] == 'E'.code.toByte() -> {
                    val textBytes = bytes.copyOfRange(8, bytes.size)
                    String(textBytes, StandardCharsets.UTF_16LE).trim().takeIf { it.isNotBlank() }
                }
                // ASCII エンコーディングマーカー
                bytes.size >= 8 &&
                bytes[0] == 'A'.code.toByte() &&
                bytes[1] == 'S'.code.toByte() &&
                bytes[2] == 'C'.code.toByte() &&
                bytes[3] == 'I'.code.toByte() &&
                bytes[4] == 'I'.code.toByte() -> {
                    val textBytes = bytes.copyOfRange(8, bytes.size)
                    String(textBytes, StandardCharsets.US_ASCII).trim().takeIf { it.isNotBlank() }
                }
                // JIS エンコーディングマーカー
                bytes.size >= 8 &&
                bytes.sliceArray(0..4).contentEquals("JIS\u0000\u0000".toByteArray(StandardCharsets.ISO_8859_1)) -> {
                    val textBytes = bytes.copyOfRange(8, bytes.size)
                    String(textBytes, charset("Shift_JIS")).trim().takeIf { it.isNotBlank() }
                }
                // エンコーディングマーカーがない場合はそのまま
                else -> raw.trim().takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            raw.trim().takeIf { it.isNotBlank() && it != "UNICODE" }
        }
    }

    /**
     * XPComment は UTF-16LE を ISO-8859-1 として受け取るため、UTF-16LE で復号する。
     */
    fun decodeXpString(raw: String): String? {
        val bytes = raw.toByteArray(StandardCharsets.ISO_8859_1)
        return try {
            val s = String(bytes, StandardCharsets.UTF_16LE).trim()
            if (s.isBlank()) null else s
        } catch (_: Exception) {
            null
        }
    }
}
