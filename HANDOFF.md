# TubeHQApp HANDOFF

## このリポジトリの構成
**1リポジトリ・2アプリ**。NovelC/NovelD と同じ「同一リポジトリに複数モジュール」方式。

```
TubeHQApp/
├── app/    TubeHQ    制作パイプライン管理（旧方針・v1.0）
├── shot/   TubeShot  YouTube風の静止画1枚を作る（新方針・v1.0）
├── deploy.sh
├── HANDOFF.md          ← このファイル
└── SPEC_TubeShot.md    TubeShot の改訂オントロジー
```

- 2つは **別アプリとして同時にインストールできる**（applicationId が別）
  - `com.appathy.tubehq` / ラベル TubeHQ
  - `com.appathy.tubeshot` / ラベル TubeShot
- `gradle assembleRelease` で両方ビルドされる
- CI artifact は `TubeHQApp-apk` ひとつ。中に `TubeHQ.apk` と `TubeShot.apk` が入る
- データは互いに独立（`tubehq.json` / `tubeshot.json`）。現時点で連携なし

### 分けた理由
旧方針（動画制作の工程管理）と新方針（YouTube風画像の生成）は目的が違うが、
どちらも「YouTube向けの制作物を作る」点で同じ領域にあり、素材や文言を将来やり取りする余地がある。
別リポジトリにすると往復が増えるため、同一リポジトリの別モジュールとした。

## ビルド構成（Appathy共通）
- AGP 8.5.2 / Kotlin 1.9.24 / Gradle 8.9（Actionsで直接インストール、wrapperなし）
- minSdk 26 / targetSdk 34 / compileSdk 34 / Java 17
- 外部依存ゼロ（androidxも不使用、`android.app.Activity` 直系）
- XMLレイアウトなし。UIは全てKotlinから生成
- `debug.keystore` をリポジトリ直下に同梱。両モジュールが `file('../debug.keystore')` で参照

---

# app/ — TubeHQ（旧方針）

制作の工程管理。撮影以外をスマホ1台で回すための司令塔。

```
app/src/main/java/com/appathy/tubehq/
  MainActivity.kt      タブナビ + ホーム(パイプライン) + ネタ帳
  Screens.kt           台本 / サムネ / メタデータ
  PrompterActivity.kt  全画面カンペ（自動スクロール）
  Yokoku.kt            次回予告フォーマットのサムネ描画
  Model.kt             Project / Scene / ThumbSpec / Store / Templates
  Ui.kt                UIヘルパー
```

- 保存先: `filesDir/tubehq.json`
- `Project.type`: talk / slide / screen
- `Project.status`: idea → script → shoot → edit → done
- `scenes[]`: head / body / note
- 尺の推定: `Project.CPS = 5.3` 文字/秒。チャプター生成の根拠でもある

### 設計上の伏線（壊さないこと）
`scenes[]` はそのまま編集指示に変換できる形にしてある。
将来ffmpeg連携する際は各Sceneに `start` / `duration` / `srcUri` を後付けして concat リストに落とす。

### 未着手（意図的）
- 動画編集（ffmpeg連携）
- YouTube投稿（Data API / OAuth）
- 分析（Analytics API）

**投稿の既知の落とし穴**: 未審査プロジェクトからAPIアップロードした動画は強制的に private になる。
「API で private 投稿 → Studioアプリで手動公開」が現実解。quota は1日10000、`videos.insert` が1600（1日6本）。
OAuth は TV/限定入力デバイス用のデバイスコードフローなら外部SDK不要。

---

# shot/ — TubeShot（新方針）

YouTube動画に見える**静止画1枚**を作る。動画は作らない。
元仕様（動画作成ツールのオントロジー）から時間軸と音声を落として畳んだもの。
**設計の判断根拠は `SPEC_TubeShot.md` を先に読むこと。**

```
shot/src/main/java/com/appathy/tubeshot/
  MainActivity.kt  タブナビ + 作品一覧 + 素材/様式/プリセット
  Screens.kt       文字 / 見た目 / 書き出し
  Frames.kt        shorts・player・thumb の描画とUI部品（全てPath描画）
  Yokoku.kt        予告風の描画
  Model.kt         Shot / Preset / Suggest / Store
  Ui.kt            UIヘルパー（スライダー・色見本を含む）
```

- 保存先: `filesDir/tubeshot.json`
- `Shot` 1件 = 画像1枚。`style` で4様式を切替
  - shorts 1080×1920 / player 1920×1080 / thumb 1280×720 / yokoku 1280×720
- 文字は**共通スロット**（title / sub / channel / meta / duration / music / likes / comments）。
  様式ごとに使うスロットが違うだけ。**ここを様式別に分けないこと**（同じ素材で様式を比較できなくなる）
- `Frames.render(shot, bg, scale)` が唯一の描画入口。プレビュー 0.42 / 一覧 0.10 / 書き出し 1.0
- 色補正は ColorMatrix、退色は暖色オーバーレイ、ぼかしは縮小→拡大描画で代用

### 商標
YouTubeのロゴ・ワードマーク・公式アイコンは**一切描画していない**。
赤いシークバー、丸ノブ、三角の再生記号といった一般的なUI意匠のみ。この方針を崩さないこと。

---

## 実行手順
```
cd ~
cp /sdcard/Download/TubeHQApp_vX.X.zip .
unzip -o TubeHQApp_vX.X.zip
bash ~/TubeHQApp/deploy.sh "vX.X 要約"
```

## 既知の落とし穴
- ホームディレクトリで `git init` しないこと（GH013 に何度も引っかかっている）
- push が HTTP 502 で切れても実際は届いていることがある。`git ls-remote origin` で確認する。
  失敗したpushの追跡refが残ると `Everything up-to-date` と誤判定するので、
  その時は `git update-ref -d refs/remotes/origin/main` してから `git fetch --prune`
- ZIPは毎回ファイル名を変える。展開後のトップレベルは `TubeHQApp` 固定
