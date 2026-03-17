package com.valoser.toshikari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WebViewCookieHelper の定数と設計意図を検証するテスト。
 *
 * 注: [android.webkit.CookieManager] を必要とする実際のCookie取得は
 * 計装テスト（androidTest）で検証する。ここではJVM上で検証可能な部分のみ。
 */
class WebViewCookieHelperTest {

    @Test
    fun defaultTimeoutIsThreeSeconds() {
        assertEquals(3_000L, WebViewCookieHelper.DEFAULT_TIMEOUT_MS)
    }

    @Test
    fun shortTimeoutIsLessThanDefault() {
        assertTrue(WebViewCookieHelper.SHORT_TIMEOUT_MS < WebViewCookieHelper.DEFAULT_TIMEOUT_MS)
    }

    @Test
    fun shortTimeoutIsPositive() {
        assertTrue(WebViewCookieHelper.SHORT_TIMEOUT_MS > 0)
    }
}
