# Toshikari - CLAUDE.md

## プロジェクト概要

ふたば☆ちゃんねる非公式Androidクライアント。Kotlin / Jetpack Compose / Hilt。

## ビルド・テスト

```bash
# ビルド
./gradlew assembleDebug

# ユニットテスト
./gradlew testDebugUnitTest

# 特定テスト
./gradlew testDebugUnitTest --tests "com.valoser.toshikari.SafeRegexTest"
```

## コード規約

- Kotlin 2.3 / targetSdk 36 / minSdk 24
- DIはHilt + KSP
- UI層はJetpack Compose (Material 3)
- VideoEditorサブシステムはMVI + Clean Architecture
- DetailViewModelはEvent-Sourcingパターン（DetailEventStore）
- テストはJUnit 4
- Token検出系は共通インターフェース `DetailTokenMatch` / `DetailTokenFinder<T>` を実装

## アーキテクチャ

### metadata/ パッケージ（MetadataExtractor分割済み）
画像メタデータ抽出を責務別に分離:
- `MetadataExtractor.kt` — 公開API・キャッシュ・ルーティング
- `metadata/PromptTextScanner.kt` — テキスト/XMP/JSONプロンプト走査
- `metadata/ExifPromptExtractor.kt` — EXIF抽出（UserComment/ImageDescription/XPComment）
- `metadata/PngPromptExtractor.kt` — PNGチャンク解析（tEXt/zTXt/iTXt/c2pa/NovelAI Stealth）
- `metadata/JpegSegmentExtractor.kt` — JPEG APPセグメント解析（APP1 XMP/APP13 IPTC）
- `metadata/WorkflowPromptExtractor.kt` — ComfyUIワークフローJSON解析
- `metadata/MetadataByteUtils.kt` — 共通バイトユーティリティ

### DetailViewModel Delegate パターン
巨大なDetailViewModelから機能別Delegateを切り出し:
- `DetailDownloadDelegate.kt` — 一括ダウンロード・競合解決・進捗管理
- `DetailMemoryManager.kt` — 適応的メモリ監視・NGフィルタキャッシュ・Coilキャッシュ管理

### MainViewModel 分割
カタログ取得ロジックを責務別に分離:
- `CatalogHtmlParser.kt` — カタログHTML解析（#cattable/cgiフォールバック）
- `CatalogUrlResolver.kt` — プレビュー→フル画像URL推測・サムネイル候補構築

### ThreadArchiver / NetworkClient 純粋ロジック分離
Android依存を持たない純粋関数を切り出しJUnitでテスト可能に:
- `ThreadArchiverSupport.kt` — sanitizeFileName/generateDirectoryNameFromUrl/generateFileName/buildRelativePath/escapeHtml/formatParagraphsAndQuotes/replaceLinksWithLocalPaths/textToParagraphs/isLongQuote/wrapLongQuoteIfNeeded
- `NetworkClientCookieSupport.kt` — parseCookieString/mergeCookies（RFC 6265準拠）

### DetailScreen / DetailList コンポーネント分離
- `ui/detail/DetailScreenComponents.kt` — SearchNavigationBar/TtsControlPanel/DetailQuickActions/AdBanner/QuickFilterChip/DetailToolbarDropdownMenu
- `ui/detail/DetailScreenModifiers.kt` — bottomPullRefresh/isScrolledToEnd
- `ui/detail/DetailListSupport.kt` — stableKey/ordinalForIndex/createImageRequest等の純粋ロジック
- `ui/detail/DetailListDialogs.kt` — ResNumDialog/FileNameDialog/QuoteDialog/BodyDialog/LineSelectionDialog

### MainCatalogScreen コンポーネント分離
- `ui/compose/CatalogScreenComponents.kt` — ActiveFilterRow/CatalogQuickActionChips/EmptyCatalogState/MoreMenu

### SettingsScreen コンポーネント分離
- `ui/compose/SettingsScreenComponents.kt` — SectionHeader/ListRow/DropdownPreferenceRow/SwitchRow/CollapsibleSectionHeader

### NgManagerScreen コンポーネント分離
- `ui/compose/NgManagerScreenComponents.kt` — NgRuleRow/AddRuleDialog/TypePickRow/NgDismissBackground等

### ReplyRepository 純粋ロジック分離
- `ReplyRepositorySupport.kt` — looksLikeError/extractJsonThisNo/extractHtmlPostNo/containsSuccessKeyword
- ReplyRepository内のparseCookieStringをNetworkClientCookieSupportに統合

### PromptFormatter / DetailPromptSanitizer 純粋ロジック分離
- `PromptFormatterSupport.kt` — normalizeWeight/splitTags/stripSettingsLines
- `DetailPromptSanitizer.kt` — HtmlCompat依存をAndroid非依存の純粋Kotlin実装に置換

### 共通ユーティリティ統合
- `StringHashSupport.kt` — sha256/md5/scaleDimensions（DetailCacheManager/ThreadMonitorWorkerの重複を統合）

### Token検出系共通インターフェース
- `ui/detail/DetailTokenMatch.kt` — `DetailTokenMatch` / `DetailTokenFinder<T>` インターフェース
- 5つのTokenFinder（Id/Res/Filename/Url/Sodane）が統一インターフェースを実装

## 既知の問題

（現在なし）

## 保守性改善タスク（残り）

### 改修の進め方

- 各タスクはファイル単位で小さなコミットに分ける
- 分割・リファクタ時は既存テストが通ることを確認してから進める
- 新規切り出しクラスにはテストを追加する
- 外部から見た振る舞いを変えないよう注意する（リファクタのみ）
