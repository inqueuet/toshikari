/**
 * DetailListCompose で使用するダイアログ Composable 群。
 * No. / ファイル名 / 引用 / 本文 / 行選択の各メニューダイアログを提供する。
 */
package com.valoser.toshikari.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.toshikari.DetailContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * No. タップメニュー（返信 / 確認 / 削除）。
 */
@Composable
internal fun ResNumDialog(
    resNum: String,
    onReply: (String, String) -> Unit,
    onConfirm: (String) -> Unit,
    onDelete: ((String) -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("No.$resNum") },
        text = { androidx.compose.material3.Text("操作を選択してください") },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = {
                    onReply(resNum, ">No.$resNum")
                    onDismiss()
                }) { androidx.compose.material3.Text("返信") }
                TextButton(onClick = {
                    onConfirm(resNum)
                    onDismiss()
                }) { androidx.compose.material3.Text("確認") }
                onDelete?.let { handleDelete ->
                    TextButton(onClick = {
                        handleDelete(resNum)
                        onDismiss()
                    }) { androidx.compose.material3.Text("削除") }
                }
            }
        }
    )
}

/**
 * ファイル名タップメニュー（返信 / 確認）。
 */
@Composable
internal fun FileNameDialog(
    fileName: String,
    onReply: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(fileName) },
        text = { androidx.compose.material3.Text("操作を選択してください") },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = {
                    onReply(">" + fileName)
                    onDismiss()
                }) { androidx.compose.material3.Text("返信") }
                TextButton(onClick = {
                    onConfirm(fileName)
                    onDismiss()
                }) { androidx.compose.material3.Text("確認") }
            }
        }
    )
}

/**
 * 引用タップメニュー（返信 / 確認）。
 */
@Composable
internal fun QuoteDialog(
    quote: String,
    onReply: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("引用") },
        text = { androidx.compose.material3.Text("操作を選択してください") },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = {
                    onReply(">" + quote)
                    onDismiss()
                }) { androidx.compose.material3.Text("返信") }
                TextButton(onClick = {
                    onConfirm(quote)
                    onDismiss()
                }) { androidx.compose.material3.Text("確認") }
            }
        }
    )
}

/**
 * 本文タップメニュー（返信 / 確認 / NG / 選択して引用）。
 */
@Composable
internal fun BodyDialog(
    src: DetailContent.Text,
    plainTextCache: Map<String, String>,
    plainTextOf: (DetailContent.Text) -> String,
    onReply: (String) -> Unit,
    onShowBackRefs: (DetailContent.Text) -> Unit,
    onAddNg: (String) -> Unit,
    onSelectLines: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val plainState = produceState<String?>(
        initialValue = plainTextCache[src.id],
        key1 = src.id,
        key2 = plainTextCache[src.id]
    ) {
        val cached = plainTextCache[src.id]
        if (cached != null) {
            value = cached
        } else {
            value = withContext(Dispatchers.Default) { plainTextOf(src) }
        }
    }
    val plain = plainState.value.orEmpty()
    val bodyOnly = DetailListSupport.extractBodyOnlyPlain(plain)
    val source = if (bodyOnly.isNotBlank()) bodyOnly else plain
    val quoted = source.lines().joinToString("\n") { ">" + it }
    val lines = source.lines().filter { it.isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("本文") },
        text = { androidx.compose.material3.Text("操作を選択してください") },
        confirmButton = {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = {
                        onReply(quoted)
                        onDismiss()
                    }) { androidx.compose.material3.Text("返信") }
                    TextButton(onClick = {
                        onSelectLines(lines)
                        onDismiss()
                    }) { androidx.compose.material3.Text("選択") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = {
                        onShowBackRefs(src)
                        onDismiss()
                    }) { androidx.compose.material3.Text("確認") }
                    TextButton(onClick = {
                        onAddNg(plain)
                        onDismiss()
                    }) { androidx.compose.material3.Text("NG") }
                }
            }
        }
    )
}

/**
 * 行選択ダイアログ（引用する行を選択）。
 */
@Composable
internal fun LineSelectionDialog(
    lines: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedLines = remember {
        mutableStateMapOf<Int, Boolean>().apply {
            lines.indices.forEach { this[it] = true }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("引用する行を選択") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(lines.size) { index ->
                    val line = lines[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedLines[index] = !(selectedLines[index] ?: true) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = selectedLines[index] ?: true,
                            onCheckedChange = { selectedLines[index] = it }
                        )
                        androidx.compose.material3.Text(
                            text = line,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val selected = lines.filterIndexed { index, _ -> selectedLines[index] == true }
                val quoted = selected.joinToString("\n") { ">" + it }
                onConfirm(quoted)
                onDismiss()
            }) {
                androidx.compose.material3.Text("引用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("キャンセル")
            }
        }
    )
}
