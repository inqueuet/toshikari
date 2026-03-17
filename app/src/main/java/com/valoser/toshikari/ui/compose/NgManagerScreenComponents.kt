package com.valoser.toshikari.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.valoser.toshikari.MatchType
import com.valoser.toshikari.NgRule
import com.valoser.toshikari.RuleType
import com.valoser.toshikari.ui.theme.LocalSpacing

@Composable
internal fun RuleItem(
    rule: NgRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // 1行分のNGルール表示。カードのクリックで編集、右端メニューから編集/削除
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LocalSpacing.current.m)
            .clickable { onEdit() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LocalSpacing.current.l, vertical = LocalSpacing.current.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                val typeLabel = when (rule.type) {
                    RuleType.ID -> "ID"
                    RuleType.BODY -> "本文"
                    RuleType.TITLE -> "タイトル"
                }
                // `match` が null の場合は種類に応じた既定値で表示
                val mt = rule.match ?: when (rule.type) {
                    RuleType.ID -> MatchType.EXACT
                    RuleType.BODY -> MatchType.SUBSTRING
                    RuleType.TITLE -> MatchType.SUBSTRING
                }
                val matchLabel = when (mt) {
                    MatchType.EXACT -> "完全一致"
                    MatchType.PREFIX -> "前方一致"
                    MatchType.SUBSTRING -> "部分一致"
                    MatchType.REGEX -> "正規表現"
                }
                Row(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.s), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = {},
                        label = { Text(typeLabel) },
                        colors = AssistChipDefaults.assistChipColors()
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text(matchLabel) }
                    )
                }
                Spacer(Modifier.height(LocalSpacing.current.s))
                Text(
                    text = rule.pattern,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "メニュー")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    text = { Text("編集") },
                    onClick = { menu = false; onEdit() }
                )
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    text = { Text("削除") },
                    onClick = { menu = false; onDelete() }
                )
            }
        }
    }
}

/**
 * 追加時の種類選択ダイアログ。
 * - `hideTitleOption` が true の場合はスレタイNGを非表示。
 * - 選択肢はカード行（TypePickRow）で表示し、タップで決定。
 */
@Composable
internal fun TypePickerDialog(
    onDismiss: () -> Unit,
    onPick: (RuleType) -> Unit,
    hideTitleOption: Boolean
) {
    // 追加時の種類選択ダイアログ。`hideTitleOption` が true の場合はスレタイNGを非表示
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("NGの種類") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.m)) {
                TypePickRow(label = "ID") { onPick(RuleType.ID) }
                TypePickRow(label = "本文ワード") { onPick(RuleType.BODY) }
                if (!hideTitleOption) TypePickRow(label = "スレタイ") { onPick(RuleType.TITLE) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}

@Composable
internal fun RuleEditDialog(
    initial: NgRule,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (pattern: String, match: MatchType?) -> Unit
) {
    // ルールの追加/編集ダイアログ。
    // - 入力欄ラベルは種類に応じて変化。
    // - IDの場合は常に完全一致に固定し、マッチ種別は選択不可。
    // - 確定時はトリムし、空文字の場合は何もせず閉じる。
    var pattern by remember { mutableStateOf(initial.pattern) }
    var match by remember { mutableStateOf(initial.match ?: defaultMatchFor(initial.type)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) labelForNewTitle(initial.type) else labelForEditTitle(initial.type)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.m)) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    singleLine = false,
                    label = { Text(if (initial.type == RuleType.TITLE) "含めたくないスレタイ語句" else if (initial.type == RuleType.ID) "例: abc123" else "含めたくない語句") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (initial.type != RuleType.ID) {
                    MatchTypeSelector(selected = match, onChange = { match = it })
                } else {
                    // IDはマッチ方法を固定（完全一致）
                    match = MatchType.EXACT
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = pattern.trim()
                    if (trimmed.isNotEmpty()) onConfirm(trimmed, match)
                    else onDismiss()
                }
            ) { Text(if (isNew) "追加" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

@Composable
internal fun MatchTypeSelector(selected: MatchType?, onChange: (MatchType) -> Unit) {
    // 本文/スレタイ用のマッチ方法選択（部分一致/前方一致/正規表現）。
    // 選択がnullの場合はUI上は部分一致が選択されたものとして扱う。
    Column(verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.s)) {
        Text("マッチ方法", style = MaterialTheme.typography.labelLarge)
        RadioRow(
            label = "部分一致",
            checked = (selected ?: MatchType.SUBSTRING) == MatchType.SUBSTRING,
            onClick = { onChange(MatchType.SUBSTRING) }
        )
        RadioRow(
            label = "前方一致",
            checked = selected == MatchType.PREFIX,
            onClick = { onChange(MatchType.PREFIX) }
        )
        RadioRow(
            label = "正規表現",
            checked = selected == MatchType.REGEX,
            onClick = { onChange(MatchType.REGEX) }
        )
    }
}

@Composable
internal fun RadioRow(label: String, checked: Boolean, onClick: () -> Unit) {
    // ラジオボタンとテキストの1行
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = LocalSpacing.current.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = checked, onClick = onClick)
        Spacer(Modifier.width(LocalSpacing.current.s))
        Text(label)
    }
}

@Composable
internal fun ConfirmDeleteDialog(rule: NgRule, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    // 削除確認ダイアログ。対象のパターン文字列を表示して確認を促す
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("削除") },
        text = { Text("このNGを削除しますか？\n${rule.pattern}") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("削除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

/**
 * 種類ごとの既定マッチ方法。
 * - ID: 完全一致
 * - 本文/スレタイ: 部分一致
 */
internal fun defaultMatchFor(type: RuleType): MatchType =
    when (type) {
        RuleType.ID -> MatchType.EXACT
        RuleType.BODY, RuleType.TITLE -> MatchType.SUBSTRING
    }

/** ダイアログタイトル（新規追加時） */
internal fun labelForNewTitle(type: RuleType): String = when (type) {
    RuleType.ID -> "NG IDを追加"
    RuleType.BODY -> "NGワードを追加"
    RuleType.TITLE -> "スレタイNGを追加"
}

/** ダイアログタイトル（編集時） */
internal fun labelForEditTitle(type: RuleType): String = when (type) {
    RuleType.ID -> "NG IDを編集"
    RuleType.BODY -> "NGワードを編集"
    RuleType.TITLE -> "スレタイNGを編集"
}

@Composable
internal fun TypeFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    // 種類フィルタ用の補助チップ。選択中は secondaryContainer で強調表示。
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = if (selected) AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) else AssistChipDefaults.assistChipColors()
    )
}

@Composable
internal fun TypePickRow(label: String, onClick: () -> Unit) {
    // 種類選択ダイアログの1行。カード行をタップすると該当種類を通知。
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.l, vertical = LocalSpacing.current.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(imageVector = Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NgDismissBackground(state: SwipeToDismissBoxState) {
    // スワイプ削除時の背景。左右いずれの方向でも削除アイコンのみを表示する簡易なレイアウト。
    val isStart = state.targetValue == SwipeToDismissBoxValue.StartToEnd
    val isEnd = state.targetValue == SwipeToDismissBoxValue.EndToStart
    val color = if (isStart || isEnd) MaterialTheme.colorScheme.errorContainer else androidx.compose.ui.graphics.Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = LocalSpacing.current.l),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isStart) {
            Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(modifier = Modifier)
        } else if (isEnd) {
            Spacer(modifier = Modifier)
            Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        } else {
            Spacer(modifier = Modifier)
            Spacer(modifier = Modifier)
        }
    }
}
