package com.valoser.toshikari

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.valoser.toshikari.ui.expressive.ExpressiveShowcaseScreen
import com.valoser.toshikari.ui.theme.ToshikariTheme

/**
 * 開発用アクティビティ: Material 3 の「Expressive」スタイルを
 * まとめて確認するショーケース画面のエントリポイント。
 *
 * - Compose テーマに `ToshikariTheme(expressive = true)` を適用して描画します。
 * - ショーケースの中身は `ExpressiveShowcaseScreen` など `ui.expressive` 配下で実装されています。
 * - 実運用向けではないため、リリースビルドでは Manifest から無効化/削除してください。
 */
class ExpressiveDemoActivity : ComponentActivity() {
    /**
     * `ToshikariTheme(expressive = true)` を適用し、`Surface` 上にショーケース画面を描画する。
     * 実運用では使わず、開発時の見た目検証用エントリポイントとして利用する。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ToshikariTheme(expressive = true) {
                Surface { ExpressiveShowcaseScreen() }
            }
        }
    }
}
