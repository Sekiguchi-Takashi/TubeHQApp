# TubeHQApp — 全体HANDOFF

YouTube制作を2つのアプリで分担する。リポジトリは1つ。

```
TubeHQApp/
├── desk/                TubeDesk  動画単品を作ること以外の全部
│   └── DESK_ONTOLOGY.md
├── cut/                 TubeCut   動画編集に特化
│   └── CUT_ONTOLOGY.md
├── EDIT_PLAN.md         2アプリ間の契約ファイル
├── SCREENS.md           画面設計＋Appathy共通UI規約
├── AI_RULES.md          AI推論ルール（他アプリにも流用）
├── HANDOFF.md           ← このファイル
└── deploy.sh
```

## v3.3 の実装状況（2026-08-12）

| 機能 | 状態 |
|---|---|
| Cut: プローブ・レーン判定 | 実装済み |
| Cut: 無音検出（RMS）・波形 | 実装済み |
| Cut: 区間リスト・±0.1秒調整・キーフレーム吸着警告 | 実装済み |
| Cut: MediaMuxer 無劣化カット／結合 | 実装済み |
| Cut: ffmpeg スクリプト生成 | 実装済み（実行は Termux） |
| Cut: 重いレーンの進捗表示 | 実装済み（Runner.kt / ファイルポーリング） |
| Cut: 複数素材にまたがる無音検出 | 実装済み（素材ごとにRMSを保持） |
| Cut: 縦切り出しプレビュー | 実装済み（CropView.kt / 枠計算は cropRect に集約） |
| Cut: テロップPNGの自動生成 | 実装済み（TelopDraw.kt / 4様式・全画面キャンバス） |
| Desk: 台本・カンペ・メタ・実績 | 実装済み |
| Desk: 静止画レンダラ吸収（4様式・A/B・採用） | 実装済み |
| 受け渡し（共有フォルダ方式） | 実装済み |
| 受け渡し（ContentProvider方式） | 実装済み（PlanProvider / ResultProvider・signature権限） |
| AI推論（Bonsai連携） | 実装済み（AI-01〜06。決定的版が既定、AIは上乗せ） |

### テロップの実装メモ
`TelopDraw.render` は**動画と同じ解像度の全画面キャンバス**に描き、位置決めもそこで済ませる。
ffmpeg 側は `overlay=0:0` で乗せるだけ。座標計算を2箇所に分散させないための判断。
様式は 太 / 細 / 白抜 / 帯 の4種。帯の色は EditProject.accent。

### AI連携のメモ
`Bonsai.kt` は Desk と Cut の両方に置いてある（内容はタスク定義部分だけ違う）。
接続先は各アプリの「AI接続先」から設定。既定は `http://127.0.0.1:8080`。
**BonsaiApp が動いていなくてもアプリは平然と動く。** エラーダイアログは出さない。
AI_RULES.md の R1〜R5 を崩さないこと。

### 重いレーンの進捗（Runner.kt）
**ffmpeg のプロセスを直接見に行かない。ファイルだけで完結させる。**
RUN_COMMAND が使えない環境でも同じように動かすための判断。

スクリプトが受け渡し先に書き出す4ファイルを2秒おきに読む。

| ファイル | 内容 |
|---|---|
| `cut_step.txt` | `3/7` 段階。各 ffmpeg の直前に書かれる |
| `cut_progress.txt` | `ffmpeg -progress` の出力。`out_time_ms` を見る |
| `cut_done.txt` | 全段階の完了印 |
| `cut_log.txt` | stderr。エラー時に末尾12行を出す |

割合は「段階の進み」と「段階内の進み」を合成する。
`Cmd.steps()` と `Runner.percent()` の段階数は**必ず一致させること**。
90秒動きがなければ警告を出す（ffmpeg が落ちている可能性が高い）。

### 複数素材の無音検出
`MainActivity.rmsBySrc` に素材ごとの RMS を持つ。
波形は全素材を連結して表示し、区間は `srcIndex` を持ったまま並ぶ。
閾値を変えたときに前回の採用状態とラベルを `srcIndex + inMs` で引き継ぐ。

### 縦切り出し（CropView.kt）
枠の計算は `EditProject.cropRect(w, h)` に集約した。
**プレビューと ffmpeg コマンドは必ずこの1箇所を使うこと。** 2箇所で計算するとズレる。

- 9:16 が入る最大幅を取り、横位置だけ `verticalOffset`（0〜100%）で動かす
- 左/中央/右 のプリセットは offset を 0/50/100 に設定するだけ
- 抽出フレームは最初の採用区間から1枚だけ遅延取得する（フレーム抽出は100〜300ms かかるため）
- 回転情報が 90/270 の素材では、ffmpeg の crop が保存時の座標系で効くため
  プレビューとズレることがある。その場合は画面に警告を出している

### 次にやること
1. Desk の実績グラフ化は不要（数値の羅列で足りる、と判断済み）
2. APPATHY_LINK.md の連携規約に合わせたカタログ登録
   - Desk⇄Cut は**パターン2（相互往復）**に相当する
   - 既に PlanProvider / ResultProvider が signature 権限で実装済み
   - 登録するには APPATHY_LINK.md と LINK_CATALOG.md の現物が要る。
     連携ハブのチャットから配布された ZIP をこのチャットに渡すこと

## 分担の線引き

**動画そのものを触るか否か**で切っている。この一線だけで判断すること。

| | TubeDesk | TubeCut |
|---|---|---|
| 扱う対象 | 企画・文字・静止画・数字 | 動画ファイルと時間軸 |
| 時間軸 | 持たない（想定尺の推定のみ） | 持つ |
| 主な出力 | 台本・サムネPNG・メタ文言 | EditPlan JSON・ffmpegコマンド |
| 実行の重さ | 軽い。アプリ内で完結 | 重い。実行はTermuxに投げる |

境界に迷ったときの判定：
- サムネイル画像 → 動画ではない → **Desk**
- ショート用の縦切り出し枠の指定 → 動画を切る → **Cut**
- 切り出した後のサムネ抽出 → 静止画 → **Desk**
- 想定尺の計算（文字数から） → 文字の話 → **Desk**
- 実尺の取得（ファイルから） → 動画の話 → **Cut**

## 前身との関係

- 旧 `app/`（TubeHQ）→ `desk/` に改称・再定義
- 旧 `shot/`（TubeShot）→ 独立アプリとしては畳み、描画エンジンを `desk/` に吸収
  - 4様式のうち サムネ風・予告風 → サムネイル生成機能
  - ショート風・プレイヤー風 → 宣伝画像生成機能

## ビルド構成（Appathy共通）

- AGP 8.5.2 / Kotlin 1.9.24 / Gradle 8.9（Actionsで直接インストール、wrapperなし）
- minSdk 26 / targetSdk 34 / compileSdk 34 / Java 17
- 外部依存ゼロ。androidx不使用、`android.app.Activity` 直系
- XMLレイアウトなし。UIは全てKotlinから生成
- `debug.keystore` はリポジトリ直下。両モジュールが `file('../debug.keystore')` で参照
- applicationId: `com.appathy.tubedesk` / `com.appathy.tubecut`（別アプリとして同時インストール可）
- CI artifact `TubeHQApp-apk` に `TubeDesk.apk` と `TubeCut.apk` の2本

## チャット分担（BonsaiApp方式を踏襲）

- `EDIT_PLAN.md` の**所有者は TubeCut 側チャット**。スキーマ変更はそこでのみ行う
- TubeDesk 側チャットは EDIT_PLAN.md を**読むだけ**。サーバ側にあたる変更を提案しない
- 新しいチャットは `EDIT_PLAN.md` と該当アプリの ONTOLOGY.md を読むところから始める

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
- YouTubeのロゴ・ワードマークは描画しない（商標）。一般的なUI意匠のみ
- **シェルスクリプトを組む文字列で `$` を裸で書かない。** Kotlin が文字列テンプレートと解釈して
  「未解決の参照」でコンパイルが落ちる。`"$f"` ではなく `"\$f"` と書くこと。
  v3.0〜v3.2 のビルド失敗は全てこれが原因（`Cmd.kt` の concat.txt 生成行）
- 同様に、生成した文字列の中に `${'$'}{...}` が残っていないか確認する。
  これは**コンパイルは通るが、値ではなく文字通り `${...}` と表示される**ので発見が遅れる
