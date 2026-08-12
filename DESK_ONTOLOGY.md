# TubeDesk オントロジー

**動画単品を作ること以外の全部。** 動画ファイルには一切触れない。

package: `com.appathy.tubedesk` ／ module: `desk/`

---

## 1. 何を担当するか

企画を立て、文字を書き、静止画を作り、公開後の数字を見る。
動画が完成するまでの前後を全て持つ。動画そのものの加工だけを `TubeCut` に委ねる。

**時間軸を持たない。** ただし「文字数からの想定尺推定」は文字の話なので担当する。
実尺（ファイルの実際の長さ）は Cut の領分であり、Desk は EditPlan 経由で受け取るだけ。

---

## 2. オントロジー

```text
TubeDesk
│
├── Channel                        チャンネル運営の単位
│   ├── identity                   名前・扱う領域・語り口
│   └── cadence                    投稿頻度の目安
│
├── Work                           作品1本。全ての中心
│   ├── WorkType                   talk | slide | screen
│   ├── WorkStatus                 idea → script → shoot → edit → publish → done
│   ├── Idea                       ネタ。1行 + 補足メモ + 優先度
│   ├── Script                     台本
│   │   └── Scene[]                head / body / note
│   ├── ImageSet                   静止画
│   │   ├── Thumbnail[]            A案 / B案
│   │   └── PromoImage[]           宣伝用
│   ├── Metadata                   タイトル / 説明 / タグ / チャプター
│   └── Publication                公開設定と実績
│
├── Renderer                       静止画描画（旧TubeShotの吸収先）
│   ├── FrameStyle                 thumb | yokoku | shorts | player
│   ├── TextSlots                  title / sub / channel / meta / duration / …
│   ├── Grade                      bright / contrast / sat / fade / blur
│   └── Accent                     アクセント色
│
├── Prompter                       カンペ。Script を全画面で流す
├── Preset                         Vlog / ゲーム / 解説 / 料理 / 旅行 / ニュース / コメディ
├── Suggest                        タイトル候補・タグ・ハッシュタグ
└── Analytics                      公開後の数字（手入力 or API）
```

主要関係:

```text
Channel ─owns→ Work
Work ─hasIdea→ Idea
     ─hasScript→ Script ─contains→ Scene[]
     ─hasImages→ ImageSet
     ─hasMetadata→ Metadata
     ─hasPublication→ Publication
Scene ─estimates→ 想定秒数（文字数 ÷ CPS）
Script ─generates→ Metadata.chapters
Script ─feeds→ Prompter
Script ─exportsTo→ EditPlan（→ TubeCut）
Renderer ─renders→ Thumbnail | PromoImage
Preset ─fills→ TextSlots + Accent + Grade
```

---

## 3. 中核データ

### Work

| フィールド | 内容 |
|---|---|
| `id` / `title` / `created` | 基本 |
| `type` | talk（一人喋り）/ slide（写真スライド）/ screen（画面録画） |
| `status` | idea / script / shoot / edit / publish / done |
| `scenes[]` | 台本の本体 |
| `images[]` | サムネ・宣伝画像 |
| `meta` | タイトル・説明・タグ |
| `pub` | 公開日時・URL・実績値 |

### Scene

```text
Scene {
  head    見出し（チャプター名になる）
  body    喋る内容（想定尺の根拠）
  note    画・小道具・操作のメモ
}
```

**Scene は EditPlan への変換元。** `start` / `duration` / `srcUri` を後付けできる形を維持すること。
ここを崩すと台本と編集が分離して作り直しになる。

### 想定尺の推定

`CPS = 5.3` 文字/秒。日本語の一人喋りの実測に寄せた値。
チャプターのタイムスタンプもこの値から生成する。実尺確定後は EditPlan の値で上書きする。

---

## 4. 静止画レンダラ（旧TubeShotの吸収）

レイアウトは様式ごとに固定。可変は背景画像と文字だけ。

| 様式 | 寸法 | 用途 |
|---|---|---|
| `thumb` | 1280×720 | サムネイル本番 |
| `yokoku` | 1280×720 | 予告風サムネイル |
| `shorts` | 1080×1920 | 宣伝画像（ショート見立て） |
| `player` | 1920×1080 | 宣伝画像（再生画面見立て） |

- 文字は**共通スロット**。様式ごとに使うスロットが違うだけ。様式別に分けないこと
- `Frames.render(spec, bg, scale)` が唯一の描画入口。プレビュー0.42 / 一覧0.10 / 書き出し1.0
- 色補正は ColorMatrix、退色は暖色オーバーレイ、ぼかしは縮小→拡大描画で代用
- **YouTubeのロゴ・ワードマークは描画しない。** 一般的なUI意匠のみ

---

## 5. 画面構成

| タブ | 中身 |
|---|---|
| ホーム | 「次にやること」を1件だけ大きく表示 + ステータス別パイプライン |
| ネタ | 1行入力でストック、型を選んで追加、台本へ昇格 |
| 台本 | 見出し/本文/メモ の3段、並べ替え、想定尺の自動計算、カンペ起動 |
| 画像 | 様式選択、背景、文字、色味、PNG書き出し、A/B比較 |
| メタ | タイトル候補、説明文生成（チャプター自動）、タグ、コピー |

カンペは別Activity（全画面・自動スクロール・速度と文字サイズ可変・画面消灯抑止）。

---

## 6. TubeCut との境界

**Desk が Cut に渡すもの**（EditPlan の初期値）
- Scene の並び、見出し、想定尺
- テロップに使う文言（Scene.head と body の冒頭）
- 縦切り出しを想定するかどうか

**Desk が Cut から受け取るもの**
- 実尺と各シーンの確定タイムコード → チャプターを実測値で作り直す
- 完成ファイルのURI → status を `edit → publish` に進める

詳細は `EDIT_PLAN.md`。**Desk はこの契約を読むだけ。** スキーマ変更を提案しない。

---

## 7. 投稿の既知の落とし穴

- 未審査プロジェクトからAPIアップロードした動画は**強制的に private** になる
  当面は「API で private 投稿 → Studioアプリで手動公開」が現実解
- quota は1日10000、`videos.insert` が1600（＝1日6本）
- OAuth は TV/限定入力デバイス用のデバイスコードフローなら外部SDK不要で実装できる

---

## 8. やらないこと

- 動画ファイルの読み込み・加工・書き出し（全て Cut）
- 音声の処理
- 実尺の自前計測
