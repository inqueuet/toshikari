package com.valoser.toshikari

import org.junit.Assert.*
import org.junit.Test

class StringHashSupportTest {

    // ====== sha256 ======

    @Test
    fun `空文字列のSHA-256は既知の値`() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            StringHashSupport.sha256("")
        )
    }

    @Test
    fun `同じ入力は同じハッシュを返す`() {
        val hash1 = StringHashSupport.sha256("test")
        val hash2 = StringHashSupport.sha256("test")
        assertEquals(hash1, hash2)
    }

    @Test
    fun `異なる入力は異なるハッシュを返す`() {
        assertNotEquals(
            StringHashSupport.sha256("hello"),
            StringHashSupport.sha256("world")
        )
    }

    @Test
    fun `SHA-256は64文字の16進文字列`() {
        val hash = StringHashSupport.sha256("anything")
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]+")))
    }

    // ====== md5 ======

    @Test
    fun `空文字列のMD5は既知の値`() {
        // MD5("") = d41d8cd98f00b204e9800998ecf8427e
        assertEquals(
            "d41d8cd98f00b204e9800998ecf8427e",
            StringHashSupport.md5("")
        )
    }

    @Test
    fun `MD5は32文字の16進文字列`() {
        val hash = StringHashSupport.md5("anything")
        assertEquals(32, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]+")))
    }

    // ====== scaleDimensions ======

    @Test
    fun `最長辺が制限内ならそのまま返す`() {
        assertEquals(100 to 50, StringHashSupport.scaleDimensions(100, 50, 200))
    }

    @Test
    fun `ちょうど制限と同じならそのまま返す`() {
        assertEquals(200 to 100, StringHashSupport.scaleDimensions(200, 100, 200))
    }

    @Test
    fun `横長画像を縮小する`() {
        val (w, h) = StringHashSupport.scaleDimensions(400, 200, 200)
        assertEquals(200, w)
        assertEquals(100, h)
    }

    @Test
    fun `縦長画像を縮小する`() {
        val (w, h) = StringHashSupport.scaleDimensions(200, 400, 200)
        assertEquals(100, w)
        assertEquals(200, h)
    }

    @Test
    fun `正方形を縮小する`() {
        val (w, h) = StringHashSupport.scaleDimensions(1000, 1000, 500)
        assertEquals(500, w)
        assertEquals(500, h)
    }

    @Test
    fun `幅0は0x0を返す`() {
        assertEquals(0 to 0, StringHashSupport.scaleDimensions(0, 100, 200))
    }

    @Test
    fun `高さ0は0x0を返す`() {
        assertEquals(0 to 0, StringHashSupport.scaleDimensions(100, 0, 200))
    }

    @Test
    fun `負の値は0x0を返す`() {
        assertEquals(0 to 0, StringHashSupport.scaleDimensions(-10, 100, 200))
    }

    @Test
    fun `縮小後の最小値は1`() {
        val (w, h) = StringHashSupport.scaleDimensions(10000, 1, 100)
        assertEquals(100, w)
        assertTrue(h >= 1)
    }

    // ====== buildMediaFileName ======

    @Test
    fun `URLからハッシュベースのファイル名を生成する`() {
        val name = StringHashSupport.buildMediaFileName("https://example.com/image.jpg")
        assertTrue(name.endsWith(".jpg"))
        assertEquals(64 + 4, name.length) // sha256(64) + ".jpg"(4)
    }

    @Test
    fun `拡張子が大文字でも小文字化される`() {
        val name = StringHashSupport.buildMediaFileName("https://example.com/photo.PNG")
        assertTrue(name.endsWith(".png"))
    }

    @Test
    fun `拡張子がないURLはハッシュのみ`() {
        val name = StringHashSupport.buildMediaFileName("https://example.com/noext")
        assertEquals(64, name.length)
        assertFalse(name.contains("."))
    }

    @Test
    fun `同じURLは同じファイル名を返す`() {
        val url = "https://example.com/test.webp"
        assertEquals(
            StringHashSupport.buildMediaFileName(url),
            StringHashSupport.buildMediaFileName(url)
        )
    }

    // ====== calculateFrameTimeUs ======

    @Test
    fun `null duration は1秒を返す`() {
        assertEquals(1_000_000L, StringHashSupport.calculateFrameTimeUs(null))
    }

    @Test
    fun `0ms duration は1秒を返す`() {
        assertEquals(1_000_000L, StringHashSupport.calculateFrameTimeUs(0L))
    }

    @Test
    fun `負の duration は1秒を返す`() {
        assertEquals(1_000_000L, StringHashSupport.calculateFrameTimeUs(-100L))
    }

    @Test
    fun `10秒の動画は5秒地点を返す`() {
        // 10000ms * 1000 / 2 = 5_000_000μs
        assertEquals(5_000_000L, StringHashSupport.calculateFrameTimeUs(10_000L))
    }

    @Test
    fun `非常に短い動画は最低1秒を保証する`() {
        // 500ms * 1000 / 2 = 250_000μs → coerceAtLeast(1_000_000)
        assertEquals(1_000_000L, StringHashSupport.calculateFrameTimeUs(500L))
    }
}
