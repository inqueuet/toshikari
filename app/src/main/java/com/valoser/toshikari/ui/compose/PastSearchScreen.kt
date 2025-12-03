package com.valoser.toshikari.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.valoser.toshikari.search.PastSearchScope
import com.valoser.toshikari.search.PastThreadSearchResult
import com.valoser.toshikari.ui.theme.LocalSpacing
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * 過去スレ検索画面の UI。
 * - 上部に検索欄と検索/キャンセルボタンのみを配置
 * - 下部に結果をリスト表示し、サムネイルとスレタイを縦詰めで見せる
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
    results: List<PastThreadSearchResult>,
    isLoading: Boolean,
    boardScope: PastSearchScope?,
    errorMessage: String?,
    onClickResult: (PastThreadSearchResult) -> Unit,
) {
    val spacing = LocalSpacing.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("過去スレ検索", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = spacing.l)
            ) {
                Spacer(modifier = Modifier.height(spacing.m))
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("検索キーワード") },
                    singleLine = true,
                    trailingIcon = { Icon(Icons.Rounded.Search, contentDescription = "検索") }
                )
                Spacer(modifier = Modifier.height(spacing.s))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onSearch) {
                        Text("検索")
                    }
                    TextButton(onClick = onCancel) {
                        Text("キャンセル")
                    }
                    val scopeLabel = boardScope?.label
                    if (!scopeLabel.isNullOrBlank()) {
                        Text(
                            text = "対象板: $scopeLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(spacing.xs))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(spacing.m))
                if (results.isEmpty() && !isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "検索結果がありません",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(spacing.s)
                    ) {
                        items(results) { item ->
                            PastSearchResultRow(
                                result = item,
                                onClick = remember(item.htmlUrl) { { onClickResult(item) } }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(spacing.l)) }
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun PastSearchResultRow(
    result: PastThreadSearchResult,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val title = result.title.orEmpty().ifBlank { "タイトル不明" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(spacing.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val thumb = result.thumbUrl
        val context = LocalContext.current
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!thumb.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(thumb)
                        .httpHeaders(
                            NetworkHeaders.Builder()
                                .add("Referer", result.htmlUrl)
                                .build()
                        )
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(spacing.s))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(
                listOfNotNull(result.server, result.board).joinToString("/").takeIf { it.isNotBlank() },
                result.createdAt
            ).joinToString(" ・ ")
            if (meta.isNotBlank()) {
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
