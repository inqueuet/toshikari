package com.valoser.toshikari.ui.compose

/**
 * NG ルール管理画面。
 *
 * 機能概要:
 * - 種別: NG ID / NG ワード（本文）/ スレタイ NG の一覧表示・追加・編集・削除に対応。
 * - 追加: 右下の FAB から。`limitType` 指定時は種類選択をスキップして該当種別の編集ダイアログを直接表示。
 * - 検索/絞り込み: 上部に検索欄（パターン/種類に部分一致）。`limitType == null` の場合は種類チップ（すべて/ID/本文/スレタイ）でフィルタ可能。
 * - 行操作: カードタップで編集、右端メニューから編集/削除、左右スワイプで削除（Undo なし）。
 * - アプリバー: CenterAlignedTopAppBar（pinned スクロール）。
 * - 空状態: 条件に一致しない場合はガイダンス文言を表示。
 *
 * パラメータ:
 * - `title`: 上部タイトル。
 * - `rules`: 表示対象の NG ルール一覧。
 * - `onBack`: 戻る押下時のハンドラ。
 * - `onAddRule`/`onUpdateRule`/`onDeleteRule`: 追加/更新/削除時のコールバック。
 * - `limitType`: 種類を固定する場合に指定（検索チップを非表示）。
 * - `hideTitleOption`: 追加/検索フィルタからスレタイ NG を除外する場合に true。
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.valoser.toshikari.MatchType
import com.valoser.toshikari.NgRule
import com.valoser.toshikari.RuleType
import com.valoser.toshikari.ui.expressive.FabMenu
import com.valoser.toshikari.ui.expressive.FabMenuItem
import com.valoser.toshikari.ui.theme.LocalSpacing

/**
 * NG ルールの一覧/検索/追加/編集/削除を提供する Compose 画面。
 * 種別固定（limitType）やスレタイNGの非表示（hideTitleOption）にも対応。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NgManagerScreen(
    title: String,
    rules: List<NgRule>,
    onBack: () -> Unit,
    onAddRule: (type: RuleType, pattern: String, match: MatchType?) -> Unit,
    onUpdateRule: (ruleId: String, pattern: String, match: MatchType?) -> Unit,
    onDeleteRule: (ruleId: String) -> Unit,
    limitType: RuleType? = null,
    hideTitleOption: Boolean = false
) {
    var showTypePicker by remember { mutableStateOf(false) }
    var editTarget: NgRule? by remember { mutableStateOf(null) }
    var deleteTarget: NgRule? by remember { mutableStateOf(null) }
    var query by remember { mutableStateOf("") }
    var activeTypeFilter by remember { mutableStateOf<RuleType?>(null) }

    // スクロールに追従しつつトップバーをピン留めする
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            // Expressive: limitType 未指定ならスピードダイヤルで種別を直接選択
            if (limitType == null) {
                var expanded by remember { mutableStateOf(false) }
                FabMenu(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    items = buildList {
                        add(FabMenuItem(icon = Icons.Rounded.Add, label = "ID 追加") {
                            editTarget = NgRule("", RuleType.ID, pattern = "", match = defaultMatchFor(RuleType.ID))
                        })
                        add(FabMenuItem(icon = Icons.Rounded.Add, label = "本文 追加") {
                            editTarget = NgRule("", RuleType.BODY, pattern = "", match = defaultMatchFor(RuleType.BODY))
                        })
                        if (!hideTitleOption) {
                            add(FabMenuItem(icon = Icons.Rounded.Add, label = "スレタイ 追加") {
                                editTarget = NgRule("", RuleType.TITLE, pattern = "", match = defaultMatchFor(RuleType.TITLE))
                            })
                        }
                    }
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = {
                        editTarget = NgRule("", limitType, pattern = "", match = defaultMatchFor(limitType))
                    },
                    text = { Text("追加") },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) }
                )
            }
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            // 検索欄と種類フィルタ（種類フィルタは limitType 未指定のときのみ表示）
            Column(Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.l, vertical = LocalSpacing.current.s)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("検索（パターン/種類）") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "クリア")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors()
                )

                if (limitType == null) {
                    Spacer(Modifier.height(LocalSpacing.current.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.s)) {
                        TypeFilterChip(
                            label = "すべて",
                            selected = activeTypeFilter == null,
                            onClick = { activeTypeFilter = null }
                        )
                        TypeFilterChip(
                            label = "ID",
                            selected = activeTypeFilter == RuleType.ID,
                            onClick = { activeTypeFilter = RuleType.ID }
                        )
                        TypeFilterChip(
                            label = "本文",
                            selected = activeTypeFilter == RuleType.BODY,
                            onClick = { activeTypeFilter = RuleType.BODY }
                        )
                        if (!hideTitleOption) {
                            TypeFilterChip(
                                label = "スレタイ",
                                selected = activeTypeFilter == RuleType.TITLE,
                                onClick = { activeTypeFilter = RuleType.TITLE }
                            )
                        }
                    }
                }
            }

            val filtered = rules.filter { r ->
                val matchesType = activeTypeFilter?.let { it == r.type } ?: true
                val q = query.trim()
                val matchesQuery = if (q.isEmpty()) true else run {
                    val typeLabel = when (r.type) { RuleType.ID -> "ID"; RuleType.BODY -> "本文"; RuleType.TITLE -> "スレタイ" }
                    r.pattern.contains(q, ignoreCase = true) || typeLabel.contains(q, ignoreCase = true)
                }
                matchesType && matchesQuery
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.s)
            ) {
                item { Spacer(Modifier.height(LocalSpacing.current.xs)) }
                items(filtered, key = { it.id }) { rule ->
                    val dismissState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismissState.currentValue) {
                        val value = dismissState.currentValue
                        if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                            // 直接削除（メニューと同等の動作）
                            onDeleteRule(rule.id)
                            dismissState.reset()
                        }
                    }
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = true,
                        enableDismissFromEndToStart = true,
                        backgroundContent = { NgDismissBackground(state = dismissState) },
                        content = {
                            RuleItem(
                                rule = rule,
                                onEdit = { editTarget = rule },
                                onDelete = { deleteTarget = rule }
                            )
                        }
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(LocalSpacing.current.xl),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("該当するNGがありません", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(LocalSpacing.current.s))
                            Text("検索条件を見直すか、右下の＋で追加", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { Spacer(Modifier.height(LocalSpacing.current.s)) }
            }
        }
    }

    if (showTypePicker && limitType == null) {
        TypePickerDialog(
            onDismiss = { showTypePicker = false },
            onPick = { t ->
                showTypePicker = false
                editTarget = NgRule("", t, pattern = "", match = defaultMatchFor(t))
            },
            hideTitleOption = hideTitleOption
        )
    }

    // 追加/編集ダイアログの表示制御。`id` が空なら新規追加、そうでなければ更新
    editTarget?.let { tgt ->
        RuleEditDialog(
            initial = tgt,
            isNew = tgt.id.isBlank(),
            onDismiss = { editTarget = null },
            onConfirm = { pattern, match ->
                if (tgt.id.isBlank()) {
                    onAddRule(tgt.type, pattern, match)
                } else {
                    onUpdateRule(tgt.id, pattern, match)
                }
                editTarget = null
            }
        )
    }

    // 削除確認ダイアログの表示制御
    deleteTarget?.let { tgt ->
        ConfirmDeleteDialog(
            rule = tgt,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                onDeleteRule(tgt.id)
                deleteTarget = null
            }
        )
    }
}
