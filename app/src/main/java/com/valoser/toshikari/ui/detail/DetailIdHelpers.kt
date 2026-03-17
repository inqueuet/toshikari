/**
 * Detail 画面向けの ID 絞り込みヘルパー群。
 *
 * 目的:
 * - 指定 ID に紐づく投稿（テキスト＋直後のメディア）をまとめて抽出し、
 *   可能であれば投稿番号で並べ替えたうえでフラット化して返します。
 *
 * 注意:
 * - 検索は HTML をプレーンテキスト化した上で行います。
 * - コメントは処理仕様の備忘録として整備しています。
 */
package com.valoser.toshikari.ui.detail

import com.valoser.toshikari.DetailContent
import com.valoser.toshikari.DetailPlainTextFormatter

/**
 * 指定した ID に紐づく投稿群を抽出して並べ替えるヘルパー。
 *
 * 挙動:
 * - `DetailContent.Text` のプレーンテキストに `"ID:<id>"` を含む行を起点として拾う。
 * - 各起点テキストの直後に連続する `Image` / `Video` を同一グループとして束ね、
 *   次の `Text` または `ThreadEndTime` が現れた時点でそのグループを確定する。
 * - グループの先頭要素（通常は Text）の `id` で重複排除する。
 * - 可能ならテキスト内の投稿番号（例: `No.123456`）をパースして昇順に並べ替える。
 * - 最後にグループをフラット化し、[Text, メディア...] の形で元の出現順を維持したまま返す。
 *
 * 注意:
 * - HTML のプレーンテキスト化は `plainTextOf` デリゲートで行い、デフォルトでは
 *   `DetailPlainTextFormatter` を用いる。
 */
// Build list: same-ID posts (Text + immediate following media until next Text/End)
internal fun buildIdPostsItems(
    all: List<DetailContent>,
    id: String,
    plainTextOf: (DetailContent.Text) -> String = DetailPlainTextFormatter::fromText,
): List<DetailContent> {
    // 対象IDを含むテキスト要素のインデックスを抽出
    val textIndexes = all.withIndex().filter { (_, c) ->
        c is DetailContent.Text &&
                plainTextOf(c).contains("ID:$id")
    }.map { it.index }
    if (textIndexes.isEmpty()) return emptyList()

    val groups = DetailContentGroupSupport.collectGroupsAt(all, textIndexes)
    // 先頭要素の id で重複排除し、投稿番号（No.xxx）が取れる場合はその数値で昇順ソート
    val ordered = groups
        .distinctBy { it.firstOrNull()?.id }
        .let { DetailContentResOrderSupport.sortGroupsByResNumber(it, plainTextOf) }
        .flatten()
    return ordered
}
