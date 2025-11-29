package com.valoser.toshikari

import android.os.Bundle
import androidx.activity.compose.setContent
import com.valoser.toshikari.ui.compose.SettingsScreen
import com.valoser.toshikari.ui.theme.ToshikariTheme

/**
 * アプリの設定画面を表示する `Activity`。
 * `ToshikariTheme(expressive = true)` 上で `SettingsScreen` を描画し、戻る操作時には
 * `onBackPressedDispatcher.onBackPressed()` を呼び出す。
 */
class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ToshikariTheme(expressive = true) {
                // 戻る時は onBackPressedDispatcher.onBackPressed() を実行。
                SettingsScreen(onBack = { onBackPressedDispatcher.onBackPressed() })
            }
        }
    }
}
