# TubeCut オントロジー

**動画編集に特化。** 動画ファイルと時間軸だけを扱う。

package: `com.appathy.tubecut` ／ module: `cut/`

---

## 1. 中核の設計判断

**TubeCut は編集を実行するアプリではない。編集計画を作るアプリである。**

理由は3つ。

1. アプリ内で再エンコードすると10分尺で実時間の1〜3倍かかり、発熱で落ちる
2. 外部依存ゼロ方針と ffmpeg 同梱が両立しない
3. 計画と実行を分ければ、実行を Termux の ffmpeg に任せられる

したがって役割は「素材を見て、どこをどう切るかを決め、`EditPlan` と ffmpeg コマンド列を吐く」まで。
実行は Termux 側。MendanApp の RUN_COMMAND ＋ SAFポーリングの実績パターンをそのまま使う。

**この判断を覆すときは、発熱と実行時間の実測を取ってからにすること。**

---

## 2. オントロジー

```text
TubeCut
│
├── EditProject                    編集案件1件
│   ├── SourceSet                  素材
│   │   └── Source[]               動画・音声・画像ファイル
│   ├── Timeline                   時間軸。Cutだけが持つ
│   │   └── Segment[]              使う区間の並び
│   ├── OverlaySet                 重ねるもの
│   │   ├── Telop[]                テロップ
│   │   └── Insert[]               挿入画像
│   ├── AudioPlan                  音の計画
│   ├── FramePlan                  画角の計画
│   └── OutputSpec                 書き出し設定
│
├── Analyzer                       解析
│   ├── SilenceDetector            無音区間の検出
│   ├── ProbeReader                実尺・codec・解像度・fps
│   └── FrameSampler               サムネ用のフレーム抽出
│
├── EditPlan                       ★ 成果物。JSON
├── CommandBuilder                 EditPlan → ffmpeg コマンド列
└── Runner                         Termux への受け渡しと完了監視
```

主要関係:

```text
EditProject ─hasSources→ Source[]
            ─hasTimeline→ Timeline ─orders→ Segment[]
Source ─probedBy→ ProbeReader → 実尺 / codec / 解像度 / fps
Source ─analyzedBy→ SilenceDetector → 無音区間[]
無音区間 ─suggests→ Segment 境界
Segment ─references→ Source + in点 + out点
Telop ─attachedTo→ Segment（相対時刻）
EditProject ─compilesTo→ EditPlan
EditPlan ─buildsInto→ ffmpegコマンド列
Runner ─executes→ コマンド列 ─produces→ 完成ファイル
EditPlan ─returnsTo→ TubeDesk（実尺と確定タイムコード）
```

---

## 3. 中核データ

### Source

```text
Source {
  uri        SAFのURI
  kind       video | audio | image
  duration   実尺（ProbeReaderが埋める）
  codec      映像codec
  size       解像度
  fps
}
```

### Segment（Timeline の要素）

```text
Segment {
  sourceId
  in         開始秒
  out        終了秒
  order      並び順
  label      Desk の Scene.head を引き継ぐ
}
```

**Desk の `Scene` と `Segment` は 1対1 で対応させる。** これが2アプリを繋ぐ骨格。
Scene が想定尺しか持たないのに対し、Segment は実尺の in/out を持つ。

### Telop

```text
Telop {
  segmentId  どのSegmentに乗るか
  text
  start      Segment内の相対秒
  duration
  position   下 | 中央 | 上
  style      文字サイズ・色・フチ
}
```

日本語テロップは **Canvas でPNGを生成して ffmpeg の overlay で乗せる**。
`drawtext` はフォント指定と改行の扱いが面倒なので使わない。

---

## 4. 機能一覧

### 解析
- 実尺・codec・解像度・fps の取得（MediaMetadataRetriever）
- 無音区間の検出：音声をデコード → RMS 判定 → 閾値と最短長で区間化
- 波形の簡易表示（RMSの配列を Canvas で描く）
- フレーム抽出（プレビューと Desk へのサムネ素材受け渡し）

### 編集の指定
- カット点の指定（無音検出の結果を初期値にして手で詰める）
- 複数テイクの並べ替えと結合
- テロップの文言・時間・位置
- BGM 区間と音量、`loudnorm` の有無
- 縦切り出しの枠（`crop` + `scale`）
- OP / ED の結合

### 出力
- `EditPlan` JSON の書き出し
- ffmpeg コマンド列の生成とクリップボードへのコピー
- Termux への受け渡しと完了監視

---

## 5. 実行方式

```text
TubeCut ──EditPlan.json──→ 共有ディレクトリ
        ──RUN_COMMAND──→ Termux
                          └→ ffmpeg 実行
        ←──SAFポーリング── 完成ファイル + 実尺
```

**無再エンコードで済ませられる条件**（同一 codec / 解像度 / fps）では MediaMuxer で結合する。
爆速で発熱もない。条件を満たすかは ProbeReader の結果で判定し、満たさなければ ffmpeg に回す。

再エンコードが必要な処理はバックグラウンド実行＋通知を前提に設計する。
進捗はログのポーリングで拾う。

---

## 6. 画面構成

| タブ | 中身 |
|---|---|
| 素材 | ファイル取り込み、実尺・codec表示、無再エンコード可否の判定 |
| 解析 | 無音検出の実行、閾値調整、波形表示、カット候補の一覧 |
| 並べ | Segment の並べ替え、in/out の微調整、Desk の Scene との対応付け |
| 重ね | テロップ・挿入画像・BGM |
| 出力 | 画角・書き出し設定、EditPlan 書き出し、コマンド生成、実行 |

---

## 7. TubeDesk との境界

**Cut が Desk から受け取るもの**
- Scene の並び・見出し・想定尺 → Segment の初期値になる
- テロップ候補の文言
- 縦切り出しを想定するかどうか

**Cut が Desk に返すもの**
- 実尺と各 Segment の確定タイムコード → Desk がチャプターを実測値で作り直す
- 完成ファイルの URI

`EDIT_PLAN.md` は **Cut 側が所有する。** スキーマ変更はこのチャットでのみ行い、
変更したら Desk 側チャットに周知する。

---

## 8. やらないこと

- 企画・台本・メタデータ（全て Desk）
- サムネイルのデザイン（フレーム抽出までが Cut、加工は Desk）
- YouTube への投稿
- リアルタイムプレビュー再生（フレーム抽出の静止画で代用）
