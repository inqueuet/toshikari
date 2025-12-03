package com.valoser.toshikari

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.valoser.toshikari.search.PastSearchScope
import com.valoser.toshikari.search.PastThreadSearchViewModel
import com.valoser.toshikari.ui.compose.PastSearchScreen
import com.valoser.toshikari.ui.theme.ToshikariTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * カタログから遷移する過去スレ検索画面。
 * - 検索キーワード入力と検索/キャンセルのみのシンプルな UI
 * - 現在表示中の板に合わせて server/board を自動指定する
 */
@AndroidEntryPoint
class PastSearchActivity : BaseActivity() {

    companion object {
        const val EXTRA_SERVER = "extra_server"
        const val EXTRA_BOARD = "extra_board"
    }

    private val viewModel: PastThreadSearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scope = intentScope()
        viewModel.setBoardScope(scope)

        setContent {
            ToshikariTheme(expressive = true) {
                val uiState by viewModel.uiState.collectAsState()
                var query by rememberSaveable { mutableStateOf(uiState.query) }

                LaunchedEffect(uiState.query) {
                    if (uiState.query != query) {
                        query = uiState.query
                    }
                }

                PastSearchScreen(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { viewModel.search(query) },
                    onCancel = { finish() },
                    results = uiState.results,
                    isLoading = uiState.isLoading,
                    boardScope = uiState.boardScope,
                    errorMessage = uiState.errorMessage,
                    onClickResult = { openThread(it.htmlUrl, it.title.orEmpty()) }
                )
            }
        }
    }

    private fun intentScope(): PastSearchScope? {
        val server = intent.getStringExtra(EXTRA_SERVER)
        val board = intent.getStringExtra(EXTRA_BOARD)
        return if (!server.isNullOrBlank() && !board.isNullOrBlank()) {
            PastSearchScope(server, board)
        } else {
            null
        }
    }

    private fun openThread(url: String, title: String) {
        val intent = Intent(this, DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_URL, url)
            putExtra(DetailActivity.EXTRA_TITLE, title)
        }
        startActivity(intent)
    }
}
