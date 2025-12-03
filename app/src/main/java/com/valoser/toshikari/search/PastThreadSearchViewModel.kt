package com.valoser.toshikari.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

data class PastSearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<PastThreadSearchResult> = emptyList(),
    val boardScope: PastSearchScope? = null,
    val errorMessage: String? = null,
)

/**
 * 過去スレ検索 API との通信を担う ViewModel。
 * - 検索キーワードと板スコープ（server/board）を保持し、結果のリストを `StateFlow` で公開
 * - リクエストは最新のみ有効になるように前回ジョブをキャンセル
 */
@HiltViewModel
class PastThreadSearchViewModel @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    private val gson = Gson()
    private val _uiState = MutableStateFlow(PastSearchUiState())
    val uiState: StateFlow<PastSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    /** カタログ側から渡された板スコープを設定する。 */
    fun setBoardScope(scope: PastSearchScope?) {
        _uiState.update { it.copy(boardScope = scope) }
    }

    /** キーワードで検索を実行する。 */
    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "キーワードを入力してください") }
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(query = trimmed, isLoading = true, errorMessage = null) }
            try {
                val results = fetchResults(trimmed, _uiState.value.boardScope)
                _uiState.update { it.copy(results = results, isLoading = false) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.w("PastSearch", "search failed", e)
                val msg = e.message ?: "不明なエラー"
                _uiState.update { it.copy(isLoading = false, errorMessage = "検索に失敗しました: $msg") }
            }
        }
    }

    private suspend fun fetchResults(
        query: String,
        scope: PastSearchScope?,
    ): List<PastThreadSearchResult> = withContext(Dispatchers.IO) {
        val baseUrl = "https://spider.serendipity01234.workers.dev/search"
        val builder = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("検索URLを構築できませんでした")
        builder.addQueryParameter("q", query)
        scope?.let {
            builder.addQueryParameter("server", it.server)
            builder.addQueryParameter("board", it.board)
        }
        val url = builder.build().toString()

        val req = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", com.valoser.toshikari.Ua.STRING)
            .header("Accept", "application/json")
            .build()

        okHttpClient.newCall(req).executeAsync().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}")
            }
            val body = resp.body?.string() ?: throw IllegalStateException("レスポンスが空でした")
            val parsed = gson.fromJson(body, PastThreadSearchResponse::class.java)
            parsed?.results?.filter { it.htmlUrl.isNotBlank() } ?: emptyList()
        }
    }
}
