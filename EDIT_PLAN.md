# EDIT_PLAN.md — TubeDesk / TubeCut 契約

**所有者は TubeCut 側チャット。** TubeDesk 側は読むだけで、変更を提案しない。
BonsaiApp の BONSAI_API.md と同じ運用にする。

- version: `3`
- 置き場所: 両モジュールの直下に同一内容を置く
- 受け渡し: ContentProvider（既定）／共有フォルダ（代替）。詳細は6章

---

## 1. 受け渡しの流れ

```text
Desk  ─── plan_<workId>.json ───→  Cut     台本と想定尺を渡す
Cut   ─── result_<workId>.json ─→  Desk    実尺と確定タイムコードを返す
```

Desk は `status = shoot` の Work について plan を書き出す。
Cut は result を書き出し、Desk は次回起動時に読み込んで status を `edit → publish` に進める。

ファイル名の `<workId>` は Desk の `Work.id`。これが唯一の突合キー。

---

## 2. plan スキーマ（Desk → Cut）

```json
{
  "v": 1,
  "workId": "1754870000000",
  "title": "作品タイトル",
  "type": "talk",
  "vertical": false,
  "scenes": [
    {
      "sceneId": "s1",
      "head": "掴み",
      "body": "喋る内容",
      "note": "画のメモ",
      "estimateSec": 18
    }
  ]
}
```

| フィールド | 意味 |
|---|---|
| `type` | talk / slide / screen。Cut は初期の切り方の判断に使う |
| `vertical` | 縦切り出しを想定するか |
| `estimateSec` | 文字数からの**推定値**。実尺ではない |
| `head` | Segment の label とチャプター名になる |

Cut は `scenes[]` の順序をそのまま Segment の初期並びにする。

---

## 3. result スキーマ（Cut → Desk）

```json
{
  "v": 1,
  "workId": "1754870000000",
  "outputUri": "content://...",
  "totalSec": 512,
  "renderedAt": 1754880000000,
  "segments": [
    {
      "sceneId": "s1",
      "startSec": 0,
      "durationSec": 21
    }
  ]
}
```

| フィールド | 意味 |
|---|---|
| `outputUri` | 完成ファイル。Desk は投稿導線でこれを使う |
| `totalSec` | 実尺。Desk の想定尺表示を上書きする |
| `startSec` | 完成ファイル先頭からの絶対秒。**チャプターはこの値で作り直す** |
| `sceneId` | plan の sceneId と対応。対応しない Segment は `null` |

---

## 4. 決めごと

- **時刻の単位は秒（整数）。** ミリ秒は使わない
- `sceneId` は Desk が採番する。Cut は生成しない
- Cut 側で Segment を分割した場合、`sceneId` は先頭のものだけが引き継ぎ、
  以降は `null`。Desk はチャプターを作る際 `null` を飛ばす
- Cut 側で Segment を削除した場合、その `sceneId` は result に現れない。
  Desk は該当 Scene をチャプターから外す
- **Desk は result を受け取っても Scene の本文を書き換えない。** 台本は台本のまま残す
- 片方のアプリが未インストールでも、もう片方は単体で完結して動くこと

---

## 5. 変更履歴

| version | 日付 | 内容 |
|---|---|---|
| 1 | 2026-08-12 | 初版 |
| 2 | 2026-08-12 | 受け渡し方式（6章）を追加。ContentProvider を既定にした |
| 3 | 2026-08-12 | ContentProvider の実装形を確定。openFile ではなく query の列で JSON を渡す |

---

## 6. 受け渡し方式

### 6.1 ContentProvider（既定）

両アプリは同じ `debug.keystore` で署名されるため、**signature レベルの権限**が使える。
NovelC / NovelD で実績のある方式をそのまま踏襲する。

```text
権限:  com.appathy.tube.PLAN_ACCESS   （protectionLevel="signature"）
       両アプリの manifest が同名で宣言し、双方が uses-permission する

Desk:  content://com.appathy.tubedesk.plan/plans          全件
       content://com.appathy.tubedesk.plan/plans/<workId> 1件
Cut:   content://com.appathy.tubecut.result/results       全件
```

**JSONは `query` の列で渡す。** `openFile` は使わない。

| プロバイダ | 列 |
|---|---|
| Desk plans | `workId` / `title` / `status` / `json` |
| Cut results | `workId` / `json` |

結果の書き戻しは2経路のどちらでもよい。
- Cut → Desk の `insert`（ContentValues に `workId` と `json`）
- Desk → Cut の `query` を Desk 側から引く

Desk は `status = idea` の作品を plans に出さない（台本がないため）。
Cut は `outputUri` が空の編集を results に出さない（未完成のため）。

- 片方が未インストールなら `resolveContentProvider` が null を返す。
  **その場合は静かに 6.2 へ落ちる。** エラーを出さない
- `<queries>` に相手のパッケージを書くこと。Android 11 以降で必須

### 6.2 共有フォルダ（代替）

ContentProvider が使えない場合、および利用者が明示的に選んだ場合。

```text
/sdcard/Download/tube/
  plan_<workId>.json
  result_<workId>.json
  telop_<workId>_<n>.png
```

- 各アプリが初回に `ACTION_OPEN_DOCUMENT_TREE` でフォルダを選び、権限を永続化する
- ffmpeg に渡すファイル（テロップPNG等）は Termux から見える必要があるため、
  **重いレーンを使う場合はこちらが必須**

### 6.3 どちらを使うか

| 状況 | 方式 |
|---|---|
| plan / result の往復のみ | ContentProvider |
| ffmpeg 実行を伴う | 共有フォルダ |
| 片方が未インストール | 共有フォルダ |

Cut は重いレーンを使う時点で共有フォルダを要求する。
Desk は ContentProvider を試し、失敗したら共有フォルダに落ちる。
