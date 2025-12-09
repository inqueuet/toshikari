package com.valoser.toshikari.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * ふたば風の配色セット（実際のふたばちゃんねるのCSSに基づく）。
 *
 * ライトモード:
 * - 背景: #FFFFEE (クリーム色)
 * - レス背景: #F0E0D6 (ベージュ)
 * - テキスト: #800000 (濃い赤茶)
 * - リンク: #0000EE (青)
 * - 無念: #CC1105 (赤・太字)
 * - Name: #117743 (緑・太字)
 * - 引用: #789922 (緑がかった色)
 *
 * ダークモード:
 * - 色相は維持しつつコントラストを確保（背景を暗く、文字色を明るく）
 */

// ふたば固有の色定義
private val FutabaBackgroundLight = Color(0xFFFFFFEE)  // body bgcolor
private val FutabaSurfaceLight = Color(0xFFF0E0D6)     // レス背景 (.rtd)
private val FutabaTextLight = Color(0xFF800000)        // text color
private val FutabaLinkLight = Color(0xFF0000EE)        // link color（一般リンク用、実際のレス内No.は#800000）
private val FutabaMunenLight = Color(0xFFCC1105)       // 無念 (.csb)
private val FutabaNameLight = Color(0xFF117743)        // Name (.cnm)
private val FutabaQuoteLight = Color(0xFF789922)       // 引用文

val FutabaLightColorScheme = lightColorScheme(
    // プライマリ: レス内のNo.は#800000を使用（DetailList.ktで直接指定）
    primary = FutabaTextLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF000070),

    // セカンダリ: Name の緑（自レス番号など）
    secondary = FutabaNameLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDEAD8),
    onSecondaryContainer = Color(0xFF0D2814),

    // ターシャリ: 無念の赤
    tertiary = FutabaMunenLight,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE4DC),
    onTertiaryContainer = Color(0xFF6B0C02),

    error = Color(0xFFB00020),
    onError = Color(0xFFFFFFFF),

    // 背景: ふたばのクリーム色
    background = FutabaBackgroundLight,
    onBackground = FutabaTextLight,

    // サーフェス: レス背景のベージュ
    surface = FutabaSurfaceLight,
    onSurface = FutabaTextLight,

    surfaceVariant = Color(0xFFE6D5C8),
    onSurfaceVariant = Color(0xFF5C4637),

    // アウトライン: 仕切り線
    outline = Color(0xFFB0937A),

    // 追加: 引用背景用
    surfaceContainerHighest = FutabaQuoteLight.copy(alpha = 0.1f),
)

val FutabaDarkColorScheme = darkColorScheme(
    // プライマリ: リンク色（明るく調整）
    primary = Color(0xFF9999FF),
    onPrimary = Color(0xFF000033),
    primaryContainer = Color(0xFF000070),
    onPrimaryContainer = Color(0xFFE0E0FF),

    // セカンダリ: Name の緑（明るく調整）
    secondary = Color(0xFF90D0A6),
    onSecondary = Color(0xFF0B2613),
    secondaryContainer = Color(0xFF234D2C),
    onSecondaryContainer = Color(0xFFD5F6D9),

    // ターシャリ: 無念の赤（明るく調整）
    tertiary = Color(0xFFFFB4A3),
    onTertiary = Color(0xFF571000),
    tertiaryContainer = Color(0xFF7C1A05),
    onTertiaryContainer = Color(0xFFFFDAD2),

    error = Color(0xFFFFB4A9),
    onError = Color(0xFF680003),

    // 背景: 暗めのクリーム色調
    background = Color(0xFF1C1511),
    onBackground = Color(0xFFE9DED2),

    // サーフェス: 暗めのベージュ調
    surface = Color(0xFF221912),
    onSurface = Color(0xFFE9DED2),

    surfaceVariant = Color(0xFF4B3A2F),
    onSurfaceVariant = Color(0xFFD5C2B4),

    outline = Color(0xFF9E8575),

    // 追加: 引用背景用
    surfaceContainerHighest = Color(0xFF3A4A3A),
)
