package com.valoser.toshikari.metadata

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.InflaterInputStream
import kotlin.math.min

/**
 * メタデータ抽出で共用するバイト操作ユーティリティ。
 */
internal object MetadataByteUtils {

    /** PNG シグネチャ判定。 */
    fun isPng(fileBytes: ByteArray): Boolean {
        if (fileBytes.size < 8) return false
        return fileBytes[0] == 137.toByte() &&
                fileBytes[1] == 80.toByte() &&
                fileBytes[2] == 78.toByte() &&
                fileBytes[3] == 71.toByte() &&
                fileBytes[4] == 13.toByte() &&
                fileBytes[5] == 10.toByte() &&
                fileBytes[6] == 26.toByte() &&
                fileBytes[7] == 10.toByte()
    }

    /** バイト配列中のゼロバイト位置を返す。見つからなければ -1。 */
    fun indexOfZero(arr: ByteArray, from: Int): Int {
        if (from >= arr.size) return -1
        for (i in from until arr.size) if (arr[i].toInt() == 0) return i
        return -1
    }

    /** zlib 伸長。 */
    fun decompress(compressedData: ByteArray): ByteArray {
        val inflater = InflaterInputStream(ByteArrayInputStream(compressedData))
        val out = ByteArrayOutputStream()
        inflater.use { i -> out.use { o -> i.copyTo(o) } }
        return out.toByteArray()
    }

    /** チャンクタイプ名がバイト配列中に存在するか。 */
    fun ByteArray.containsChunkType(type: String): Boolean {
        val needle = type.toByteArray(StandardCharsets.US_ASCII)
        if (needle.isEmpty() || this.size < needle.size) return false
        outer@ for (i in 0..(this.size - needle.size)) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }

    /** 上限付き InputStream.readBytes。 */
    fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val r = this.read(buf)
            if (r <= 0) break
            val canWrite = min(limit - total, r)
            if (canWrite <= 0) break
            out.write(buf, 0, canWrite)
            total += canWrite
        }
        return out.toByteArray()
    }
}
