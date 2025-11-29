package com.valoser.toshikari

/**
 * サンプル板（example.com）用のMockデータを提供するプロバイダー。
 *
 * - カタログHTMLとスレッド詳細HTMLを生成
 * - 実際のネットワーク通信なしでアプリの動作確認が可能
 * - 履歴機能とも完全に連携
 */
object MockDataProvider {

    private val mockThreads = listOf(
        MockThread(
            id = "1001",
            title = "サンプルスレッド1",
            replyCount = "42",
            thumbnailUrl = "https://placehold.co/300x200/orange/white?text=Thread+1"
        ),
        MockThread(
            id = "1002",
            title = "テストスレッド2",
            replyCount = "128",
            thumbnailUrl = "https://placehold.co/300x200/blue/white?text=Thread+2"
        ),
        MockThread(
            id = "1003",
            title = "デモスレッド3",
            replyCount = "95",
            thumbnailUrl = "https://placehold.co/300x200/green/white?text=Thread+3"
        ),
        MockThread(
            id = "1004",
            title = "例示スレッド4",
            replyCount = "67",
            thumbnailUrl = "https://placehold.co/300x200/purple/white?text=Thread+4"
        ),
        MockThread(
            id = "1005",
            title = "サンプル画像スレッド5",
            replyCount = "203",
            thumbnailUrl = "https://placehold.co/300x200/red/white?text=Thread+5"
        ),
        MockThread(
            id = "1006",
            title = "動作確認用スレッド6",
            replyCount = "89",
            thumbnailUrl = "https://placehold.co/300x200/teal/white?text=Thread+6"
        ),
        MockThread(
            id = "1007",
            title = "Mockデータスレッド7",
            replyCount = "156",
            thumbnailUrl = "https://placehold.co/300x200/pink/white?text=Thread+7"
        ),
        MockThread(
            id = "1008",
            title = "検証用スレッド8",
            replyCount = "34",
            thumbnailUrl = "https://placehold.co/300x200/yellow/black?text=Thread+8"
        )
    )

    /**
     * URLがMock対象かどうかを判定する。
     */
    fun isMockUrl(url: String): Boolean {
        return url.contains("example.com", ignoreCase = true)
    }

    /**
     * カタログ用のMock HTMLを生成する。
     */
    fun getMockCatalogHtml(): String {
        val threadCells = mockThreads.joinToString("\n") { thread ->
            """
            <td>
                <a href="res/${thread.id}.htm">
                    <img src="${thread.thumbnailUrl}">
                </a>
                <small>${thread.title}</small>
                <font>${thread.replyCount}</font>
            </td>
            """.trimIndent()
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>サンプル板 - カタログ</title>
            </head>
            <body>
                <table id="cattable">
                    <tr>
                        $threadCells
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * スレッド詳細用のMock HTMLを生成する。
     *
     * @param threadId スレッドID
     * @return スレッド詳細HTML（見つからない場合は404 HTML）
     */
    fun getMockThreadHtml(threadId: String): String {
        val thread = mockThreads.find { it.id == threadId }
            ?: return getMockNotFoundHtml()

        val replyCount = thread.replyCount.toIntOrNull() ?: 10
        val opTimestamp = System.currentTimeMillis() / 1000 - 7200 // 2時間前
        val replies = generateMockReplies(threadId, replyCount, opTimestamp)

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>${thread.title}</title>
            </head>
            <body>
                <div class="thre">
                    <blockquote>
                        <font color="#008000"><b>無念</b></font>
                        Name <font color="#117743"><b>としあき</b></font>
                        ${opTimestamp}
                        No.${threadId}
                        <br>
                        <a href="https://placehold.co/600x400/orange/white?text=OP+Image+${threadId}" target="_blank">
                            <img src="${thread.thumbnailUrl}" border="0" alt="OP画像">
                        </a>
                        <br>
                        ${thread.title}<br>
                        これはMockスレッドです。サンプル板での動作確認用データとなります。
                    </blockquote>
                    $replies
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Mock レスを生成する。
     *
     * @param threadId スレッドID
     * @param count レス数
     * @param baseTimestamp 基準タイムスタンプ
     * @return 生成されたレスのHTML
     */
    private fun generateMockReplies(threadId: String, count: Int, baseTimestamp: Long): String {
        val displayCount = minOf(count, 100) // 最大100レスまで生成
        return (1..displayCount).joinToString("\n") { index ->
            val resNum = threadId.toInt() + index
            val timestamp = baseTimestamp + index * 120 // 2分ごと
            val hasImage = index % 5 == 0 // 5レスごとに画像付き

            val imageHtml = if (hasImage) {
                """
                <br>
                <a href="https://placehold.co/400x300/blue/white?text=Reply+Image+$resNum" target="_blank">
                    <img src="https://placehold.co/200x150/blue/white?text=Reply+$resNum" border="0" alt="返信画像">
                </a>
                """.trimIndent()
            } else {
                ""
            }

            """
            <table border="0">
                <tr>
                    <td class="rth" nowrap bgcolor="#f0e0d6">
                        <input type="checkbox" name="$resNum" value="delete">
                        <font color="#008000"><b>無念</b></font>
                        Name <font color="#117743"><b>としあき</b></font>
                        $timestamp
                        No.$resNum
                        <a href="javascript:quote('$resNum');">🔗</a>
                    </td>
                </tr>
                <tr>
                    <td class="rtd">
                        <blockquote>
                            ${generateMockReplyText(index, resNum)}$imageHtml
                        </blockquote>
                    </td>
                </tr>
            </table>
            """.trimIndent()
        }
    }

    /**
     * Mock レス本文を生成する。
     *
     * @param index レスのインデックス
     * @param resNum レス番号
     * @return レス本文
     */
    private fun generateMockReplyText(index: Int, resNum: Int): String {
        val templates = listOf(
            "これはサンプルレス${index}です",
            "テストコメント${index}<br>複数行のテストです",
            "Mockデータの動作確認用レス${index}",
            ">>$resNum<br>引用テスト（No.$resNum への返信）",
            "画像付きレスのサンプルです",
            "履歴機能のテスト用コメント${index}",
            "長文テスト：<br>これは長文のサンプルレスです。<br>複数行にわたるコメントの表示確認用です。<br>改行や引用の動作を確認できます。",
            "リンクテスト：<br><a href=\"https://example.com\">example.com</a>",
            "そうだねテスト用レス${index}",
            "未読管理の動作確認用レス${index}"
        )
        return templates[index % templates.size]
    }

    /**
     * 404エラー用のHTMLを生成する。
     */
    private fun getMockNotFoundHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>404 - Not Found</title>
            </head>
            <body>
                <h1>404 - スレッドが見つかりません</h1>
                <p>指定されたスレッドは存在しません。</p>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Mock スレッドのデータクラス。
     */
    private data class MockThread(
        val id: String,
        val title: String,
        val replyCount: String,
        val thumbnailUrl: String
    )
}
