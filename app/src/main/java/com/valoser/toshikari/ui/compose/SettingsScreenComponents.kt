/**
 * SettingsScreen から切り出した共通ヘルパー Composable 群。
 * セクション見出し・設定行・ドロップダウン・スイッチ・折りたたみヘッダーを提供します。
 */
package com.valoser.toshikari.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.valoser.toshikari.ui.theme.LocalSpacing

/** セクション見出しのテキスト表示。設定グループの区切りに使用。 */
@Composable
internal fun SectionHeader(text: String) {
    // セクションタイトル
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = LocalSpacing.current.l, vertical = LocalSpacing.current.s)
    )
}

/**
 * 単純な設定行（タイトル＋任意サマリ）。
 * 行全体がクリック対象となり、押下時に `onClick` を呼び出します。
 */
@Composable
internal fun ListRow(title: String, summary: String, enabled: Boolean = true, onClick: () -> Unit) {
    // 設定の1行（タイトル＋サマリ）。`enabled` に応じてクリック可否を切り替える
    val summaryColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.7f else 0.4f)
    val titleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.6f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = LocalSpacing.current.l, vertical = LocalSpacing.current.m)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
        if (summary.isNotBlank()) {
            Spacer(modifier = Modifier.size(LocalSpacing.current.xxs))
            Text(summary, style = MaterialTheme.typography.bodySmall, color = summaryColor)
        }
    }
}

/**
 * ドロップダウンで値を選択する設定行。
 * `entries` は表示ラベル、`values` は保存値。`value` が現在値で、変更時に `onValueChange` をコール。
 */
@Composable
internal fun DropdownPreferenceRow(
    title: String,
    entries: List<String>,
    values: List<String>,
    value: String,
    summary: String = "",
    enabled: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // 現在値に対応するラベルを算出（見つからない場合は値をそのまま表示）
    val currentLabel = entries.getOrNull(values.indexOf(value)) ?: value

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LocalSpacing.current.l, vertical = LocalSpacing.current.m)
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        val contentModifier = if (enabled) {
            Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        } else {
            Modifier.fillMaxWidth()
        }
        Column(
            modifier = contentModifier
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.size(LocalSpacing.current.xxs))
            Text(currentLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            if (summary.isNotBlank()) {
                Spacer(modifier = Modifier.size(LocalSpacing.current.xxs))
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }

        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            values.forEachIndexed { index, v ->
                val label = entries.getOrNull(index) ?: v
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        if (v != value) onValueChange(v)
                    }
                )
            }
        }
    }
}

/** タイトル＋任意サマリ＋トグルスイッチの設定行。`checked` の変化は `onToggle` で受け取ります。 */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    summary: String = "",
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    // タイトル＋サマリ＋スイッチの1行
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LocalSpacing.current.l, vertical = LocalSpacing.current.m)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary.isNotBlank()) {
                Spacer(modifier = Modifier.size(LocalSpacing.current.xxs))
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
        Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
    }
}

/**
 * 折りたたみ可能なセクションヘッダー。
 * 展開/折りたたみアイコンを持つクリック可能な見出しを表示します。
 *
 * パラメータ:
 * - `text`: セクションのタイトル。
 * - `expanded`: 現在の展開状態。
 * - `onToggle`: クリック時に呼ばれるハンドラ。
 */
@Composable
internal fun CollapsibleSectionHeader(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = LocalSpacing.current.l, vertical = LocalSpacing.current.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
            contentDescription = if (expanded) "折りたたむ" else "展開する"
        )
    }
}
