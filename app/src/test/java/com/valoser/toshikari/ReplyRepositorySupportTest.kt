package com.valoser.toshikari

import org.junit.Assert.*
import org.junit.Test

class ReplyRepositorySupportTest {

    // ====== looksLikeError ======

    @Test
    fun `成功HTML - 書きこみましたは false を返す`() {
        assertFalse(ReplyRepositorySupport.looksLikeError("<html><body>書きこみました</body></html>"))
    }

    @Test
    fun `成功HTML - No付きは false を返す`() {
        assertFalse(ReplyRepositorySupport.looksLikeError("<html><body>No.12345678</body></html>"))
    }

    @Test
    fun `エラーHTML - エラーが発生は true を返す`() {
        assertTrue(ReplyRepositorySupport.looksLikeError("<html><body>エラーが発生しました</body></html>"))
    }

    @Test
    fun `エラーHTML - 連続投稿は true を返す`() {
        assertTrue(ReplyRepositorySupport.looksLikeError("<html><body>連続投稿はできません</body></html>"))
    }

    @Test
    fun `エラーHTML - 規制中は true を返す`() {
        assertTrue(ReplyRepositorySupport.looksLikeError("<html><body>規制中です</body></html>"))
    }

    @Test
    fun `プレーンテキストのエラーは true を返す`() {
        assertTrue(ReplyRepositorySupport.looksLikeError("エラーが発生しました"))
    }

    @Test
    fun `空文字やシンプルな成功メッセージは false を返す`() {
        // エラーパターンにも成功パターンにもマッチしない → false
        assertFalse(ReplyRepositorySupport.looksLikeError(""))
        assertFalse(ReplyRepositorySupport.looksLikeError("OK"))
    }

    // ====== extractJsonThisNo ======

    @Test
    fun `JSON レスポンスから thisno を抽出する`() {
        val response = """{"status":"ok","thisno":1345629398,"res":""}"""
        assertEquals("1345629398", ReplyRepositorySupport.extractJsonThisNo(response))
    }

    @Test
    fun `thisno がない場合は null を返す`() {
        assertNull(ReplyRepositorySupport.extractJsonThisNo("""{"status":"ok"}"""))
    }

    @Test
    fun `6桁未満の数値は null を返す`() {
        assertNull(ReplyRepositorySupport.extractJsonThisNo("""{"thisno":12345}"""))
    }

    // ====== extractHtmlPostNo ======

    @Test
    fun `No付き投稿番号を抽出する`() {
        assertEquals("1234567", ReplyRepositorySupport.extractHtmlPostNo("No.1234567"))
    }

    @Test
    fun `Noとドットの後にスペースがある場合も抽出する`() {
        assertEquals("1234567", ReplyRepositorySupport.extractHtmlPostNo("No. 1234567"))
    }

    @Test
    fun `マッチしない場合は null を返す`() {
        assertNull(ReplyRepositorySupport.extractHtmlPostNo("何もない文字列"))
    }

    // ====== containsSuccessKeyword ======

    @Test
    fun `書きこみましたは true を返す`() {
        assertTrue(ReplyRepositorySupport.containsSuccessKeyword("書きこみました"))
    }

    @Test
    fun `送信完了は true を返す`() {
        assertTrue(ReplyRepositorySupport.containsSuccessKeyword("送信完了しました"))
    }

    @Test
    fun `エラーテキストは false を返す`() {
        assertFalse(ReplyRepositorySupport.containsSuccessKeyword("エラーが発生しました"))
    }
}
