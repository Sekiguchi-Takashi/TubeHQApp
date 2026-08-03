# TubeHQApp HANDOFF

## 概要
YouTube制作パイプラインの管理アプリ。撮影以外の工程をスマホ1台で回す。
v1 は「アプリだけで完結する範囲」。編集(ffmpeg)と投稿(YouTube Data API)は v2 / v3。

- package: `com.appathy.tubehq`
- repo: `Sekiguchi-Takashi/TubeHQApp`
- ブランド: Appathy（Less Motivation, More Automation）

## ビルド構成（Appathy共通）
- AGP 8.5.2 / Kotlin 1.9.24 / Gradle 8.9（Actionsで直接インストール、wrapperなし）
- minSdk 26 / targetSdk 34 / compileSdk 34 / Java 17
- 外部依存ゼロ（androidxも不使用。`android.app.Activity` 直系）
- XMLレイアウトなし。UIは全てKotlinから生成
- `debug.keystore` をリポジトリに同梱（release も同じ鍵で署名）
- CI: `.github/workflows/build.yml` → artifact `TubeHQApp-apk`

## ファイル構成
```
app/src/main/java/com/appathy/tubehq/
  MainActivity.kt      タブナビ + ホーム(パイプライン) + ネタ帳
  Screens.kt           台本 / サムネ / メタデータ の各画面
  PrompterActivity.kt  全画面カンペ（自動スクロール）
  Yokoku.kt            次回予告フォーマットのサムネ描画（Canvas）
  Model.kt             Project / Scene / ThumbSpec / Store / Templates
  Ui.kt                色・ボタン・カード等のUIヘルパー
```

## データ
- 保存先: `filesDir/tubehq.json`（単一JSON）
- バックアップ: ホーム画面から SAF で書き出し / 読み込み
- `Project`
  - `type`: talk（一人喋り）/ slide（写真スライド）/ screen（画面録画）
  - `status`: idea → script → shoot → edit → done
  - `scenes[]`: `head`（見出し）/ `body`（喋る内容）/ `note`（画・操作メモ）
  - `thumbs[2]`: A案・B案。`main` `sub` `ep` `bg`(URI文字列)
- 尺の推定: `Project.CPS = 5.3` 文字/秒。`sceneSeconds()` がチャプター生成の根拠

### v2への設計上の伏線
`scenes[]` はそのまま編集指示に変換できる形にしてある。
v2では各Sceneに `start` / `duration` / `srcUri` を後付けし、ffmpegのconcatリストに落とす。
**この構造を壊さないこと。** 壊すとv2で台本と編集が分離して作り直しになる。

## サムネ仕様（Yokoku.kt）
昔のTVアニメ「次回予告」風。**画角とレイアウトは固定**、可変は背景画像と文字だけ。
- 1280×720。上下に黒帯＋金線（`BAR = 26f`）
- 左上に赤い「次回」札（白の内枠付き）、その下に話数、右上に `N E X T   E P I S O D E`
- 主題は右側に**縦書き**、最大4列。列は右から左へ折り返し。`TITLE_RIGHT = 1188f`
- 文字サイズは 104f から 4f 刻みで自動縮小（収まる最大を採用）
- 白フィル＋黒フチ（`size * 0.20f`）＋赤いドロップシャドウ
- `ー` `〜` `－` は90度回転して縦書き対応
- 副題は下帯に横書き（金色）、最大2行で自動縮小、左端に赤いアクセントバー
- 全体に走査線（4pxごとに2px、alpha 0x1F）＋セピア寄りの退色グレード
- フォントは `Typeface.SERIF` + BOLD（端末標準の明朝系。外部フォント不使用）

レイアウトを変えたい時に触る定数: `BAR` `TOP_SAFE` `BOT_SAFE` `TITLE_RIGHT`

## 画面ごとの役割
| タブ | 中身 |
|---|---|
| ホーム | 「次にやること」を1件だけ大きく表示 + ステータス別一覧 + バックアップ |
| ネタ | 1行入力でストック、型を選んで追加、「台本へ昇格」でテンプレ流し込み |
| 台本 | 見出し/本文/メモ の3段編集、シーン並べ替え、想定尺の自動計算 |
| サムネ | A案/B案、背景選択、プレビュー、PNG書き出し、2案並べて比較 |
| メタ | タイトル候補6種、説明文生成（チャプター自動）、タグ生成、コピー |

チャプターは `scenes` の文字数から累積秒を出して `mm:ss 見出し` を生成する。

## v1でやらないこと（意図的）
- 動画の編集（ffmpeg連携）→ v2
- YouTubeへの投稿（Data API / OAuth）→ v3
- 分析（Analytics API）→ v3

### v3の既知の落とし穴（先に記録）
YouTube Data API の未審査プロジェクトからアップロードした動画は **強制的に private** になる。
当面は「API で private 投稿 → Studioアプリで手動公開」を前提に設計すること。
quota は 1日 10000、`videos.insert` が 1600（＝1日6本まで）。
OAuth は TV/限定入力デバイス用のデバイスコードフローなら外部SDK不要で実装できる。

## 初回push手順
```
cd ~/TubeHQApp
curl -X POST -H "Authorization: token ghp_XXXX" https://api.github.com/user/repos -d '{"name":"TubeHQApp","private":true}'
git init
git add -A
git commit -m "v1.0"
git branch -M main
git remote remove origin
git remote add origin https://Sekiguchi-Takashi:ghp_XXXX@github.com/Sekiguchi-Takashi/TubeHQApp.git
git push -u origin main
```

## 注意（既知の落とし穴）
- ホームディレクトリで `git init` しないこと。GitHub Push Protection (GH013) に何度も引っかかっている
- ZIPは毎回ファイル名を変える（`TubeHQApp_vX.X.zip`）。展開後のトップレベルは `TubeHQApp` 固定
