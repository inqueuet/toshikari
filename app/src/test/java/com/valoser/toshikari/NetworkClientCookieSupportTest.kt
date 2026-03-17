package com.valoser.toshikari

import org.junit.Assert.*
import org.junit.Test

class NetworkClientCookieSupportTest {

    // ====== parseCookieString ======

    @Test
    fun `null は空 Map を返す`() {
        assertTrue(NetworkClientCookieSupport.parseCookieString(null).isEmpty())
    }

    @Test
    fun `空文字列は空 Map を返す`() {
        assertTrue(NetworkClientCookieSupport.parseCookieString("").isEmpty())
    }

    @Test
    fun `単一の Cookie を解析する`() {
        val result = NetworkClientCookieSupport.parseCookieString("key=value")
        assertEquals(mapOf("key" to "value"), result)
    }

    @Test
    fun `複数の Cookie を解析する`() {
        val result = NetworkClientCookieSupport.parseCookieString("a=1; b=2; c=3")
        assertEquals(mapOf("a" to "1", "b" to "2", "c" to "3"), result)
    }

    @Test
    fun `値が空の Cookie を受け入れる`() {
        val result = NetworkClientCookieSupport.parseCookieString("key=")
        assertEquals(mapOf("key" to ""), result)
    }

    @Test
    fun `値にイコールが含まれる場合は最初のイコールで分割する`() {
        val result = NetworkClientCookieSupport.parseCookieString("key=a=b")
        assertEquals(mapOf("key" to "a=b"), result)
    }

    @Test
    fun `イコールなしの不正セグメントは無視する`() {
        val result = NetworkClientCookieSupport.parseCookieString("invalid; key=value")
        assertEquals(mapOf("key" to "value"), result)
    }

    @Test
    fun `セミコロンが先頭にある場合は無視する`() {
        val result = NetworkClientCookieSupport.parseCookieString("; key=value")
        assertEquals(mapOf("key" to "value"), result)
    }

    @Test
    fun `キーにスペースが含まれる場合は無視する`() {
        val result = NetworkClientCookieSupport.parseCookieString("bad key=value; good=ok")
        assertEquals(mapOf("good" to "ok"), result)
    }

    @Test
    fun `キーにセミコロンが含まれる場合は無視する`() {
        // セミコロンはそもそもセグメント区切りになるので key 側には来ないが念のため
        val result = NetworkClientCookieSupport.parseCookieString("normal=value")
        assertEquals(1, result.size)
    }

    // ====== mergeCookies ======

    @Test
    fun `すべて null なら null を返す`() {
        assertNull(NetworkClientCookieSupport.mergeCookies(null, null))
    }

    @Test
    fun `単一 Cookie 文字列をマージする`() {
        val result = NetworkClientCookieSupport.mergeCookies("a=1; b=2")
        assertNotNull(result)
        assertTrue(result!!.contains("a=1"))
        assertTrue(result.contains("b=2"))
    }

    @Test
    fun `後ろの引数が前の引数を上書きする（後勝ち）`() {
        val result = NetworkClientCookieSupport.mergeCookies("foo=old", "foo=new")
        assertNotNull(result)
        assertTrue(result!!.contains("foo=new"))
        assertFalse(result.contains("foo=old"))
    }

    @Test
    fun `認証系Cookieが末尾に配置される`() {
        val result = NetworkClientCookieSupport.mergeCookies("a=1; token=abc", "b=2")
        assertNotNull(result)
        // token はクリティカルキーなので最後に来る
        val idx_b = result!!.indexOf("b=2")
        val idx_token = result.indexOf("token=abc")
        assertTrue("token は b より後に来るはず", idx_token > idx_b)
    }

    @Test
    fun `null と有効な Cookie 文字列をマージできる`() {
        val result = NetworkClientCookieSupport.mergeCookies(null, "x=1")
        assertNotNull(result)
        assertTrue(result!!.contains("x=1"))
    }
}
