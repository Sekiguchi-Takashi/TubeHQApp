# TubeShot — YouTube風画像作成ツール

元仕様「YouTube風動画作成ツール」から方針変更。
**動画を作らず、YouTube動画に見える静止画を1枚作る**ツールに畳み込んだ改訂版。

---

## 1. 何が変わったか

| 元 | 改訂後 | 理由 |
|---|---|---|
| VideoProject | ShotProject | 出力が1枚のPNG |
| VideoTimeline | LayerStack | 時間軸を持たない |
| VideoClip | BaseImage | 背景画像1枚 |
| AudioLayer / VoiceGenerator / CaptionGenerator | 削除 | 音が無い |
| Transition / 速度 / トリミング | 削除 | 時間軸が無い |
| F710 Render → MP4 | Export → PNG | |
| F900 フィード / SocialInteraction | 削除 | 単体ツールに絞る |
| F601 スタイル推論 | Preset | 文字と色味のセット |
| F610〜F612 タイトル・説明・タグ | Suggest | 文言候補生成 |
| F500 映像エフェクト | Grade | ColorMatrixで実装 |

**残した中心思想**：元仕様の「動画を作るのではなくProjectを作り、素材と文字を同じ土俵で組む」を、
「Shotを作り、背景と文字を固定レイアウトに流し込む」に読み替えた。

---

## 2. 改訂オントロジー

```text
ShotCreationSystem
│
├── ShotProject
│   ├── BaseImage          撮影 / 生成 / 読み込み いずれも同一に扱う
│   ├── FrameStyle         shorts | player | thumb | yokoku
│   ├── TextSlots          title / sub / channel / meta / duration / music / likes / comments
│   ├── Grade              bright / contrast / sat / fade / blur
│   └── Accent             アクセント色
│
├── Preset                 Vlog / ゲーム / 解説 / 料理 / 旅行 / ニュース / コメディ
├── Suggest                文言候補・ハッシュタグ
└── Export                 PNG
```

主要関係:

```text
User ─creates→ ShotProject
ShotProject ─hasBase→ BaseImage
            ─hasStyle→ FrameStyle
            ─hasText→ TextSlots
            ─hasGrade→ Grade
Preset ─fills→ TextSlots + Accent + Grade
FrameStyle ─determines→ Layout（固定）
ShotProject ─renders→ PNG
```

**設計原則（元仕様32章の読み替え）**
撮影画像・AI生成画像・スクショを別扱いにしない。全て BaseImage に集約し、
FrameStyle だけを差し替えることで見た目を切り替える。

---

## 3. 様式（レイアウトは固定・可変は背景と文字だけ）

### shorts / 1080×1920
- 上に「ショート」、中央に大きな焼き文字
- 右レール：アバター＋追加ボタン / ハート / 吹き出し / 共有矢印 / 音源ディスク
- 左下：@チャンネル名 / 説明2行 / ♪音源名
- 最下部：進捗バー（アクセント色）

### player / 1920×1080
- 左上：タイトル2行 / @チャンネル / 再生数・日付
- 中央：再生ボタン（表示切替可）
- 下部：シークバー＋丸ノブ、再生・次へ・音量、経過/総時間、字幕・設定・シアター・全画面
- 経過時間は総時間と再生位置%から自動計算

### thumb / 1280×720
- 左下に大テロップ（白＋黒フチ）＋アクセント色の下線
- サブ文字、チャンネル名、右下に尺バッジ
- 右側に再生ボタン（表示切替可）

### yokoku / 1280×720
- 昔のTVアニメ次回予告。上下黒帯＋金線、左上に「次回」札、主題は右側に縦書き
- 副題は下帯に金文字、全体に走査線とセピア退色
- `ー` `〜` は縦書き時に90度回転

---

## 4. 商標についての注意

YouTubeのロゴ・ワードマーク・公式アイコンは**一切描画していない**。
赤いシークバー、丸ノブ、三角の再生記号といった一般的なUI意匠のみを使っている。
「YouTube」の文字を画面内に入れる機能は意図的に付けていない。
配布・商用利用する場合はこの方針を維持すること。

---

## 5. 今後

### 次にやるなら
- 文字位置・サイズの手動微調整（現在は自動配置のみ）
- テキストの縁取り色・フォント選択
- 複数枚の一括書き出し
- 背景の切り抜き合成

### やらないと決めたこと
- 動画そのものの生成・編集（別アプリの領分）
- 音声まわり全般
- SNS機能・フィード
