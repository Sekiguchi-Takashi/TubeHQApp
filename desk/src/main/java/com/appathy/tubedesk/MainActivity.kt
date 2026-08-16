package com.appathy.tubedesk

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    lateinit var store: Store
    var editing: Project? = null

    private lateinit var body: FrameLayout
    private lateinit var tabRow: LinearLayout
    private lateinit var headSub: TextView
    private var tab = 0

    private var pickCb: ((Uri) -> Unit)? = null
    private var saveCb: ((Uri) -> Unit)? = null
    private var treeCb: ((Uri) -> Unit)? = null

    private var cacheKey = ""
    private var cacheBmp: Bitmap? = null

    val ui = Handler(Looper.getMainLooper())

    private val tabNames = listOf("ホーム", "ネタ", "台本", "画像", "メタ", "実績")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        Speed.load(this)
        Channel.load(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)
        root.addView(buildHeader())

        body = FrameLayout(this)
        root.addView(body, Ui.lp(-1, 0, 1f))

        tabRow = LinearLayout(this)
        tabRow.orientation = LinearLayout.HORIZONTAL
        tabRow.setBackgroundColor(Color.parseColor("#121519"))
        tabRow.setPadding(Ui.dp(this, 2), Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 8))
        root.addView(tabRow, Ui.lp(-1, -2))

        setContentView(root)
        buildTabs()
        show(0)
        Bridge.pullResults(this)
    }

    private fun buildHeader(): View {
        val h = LinearLayout(this)
        h.orientation = LinearLayout.VERTICAL
        h.setBackgroundColor(Color.parseColor("#121519"))
        h.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 10))
        val t = TextView(this)
        t.text = "TubeDesk"
        t.setTextColor(Ui.ACC)
        t.textSize = 19f
        t.typeface = Typeface.DEFAULT_BOLD
        h.addView(t)
        headSub = TextView(this)
        headSub.setTextColor(Ui.SUB)
        headSub.textSize = 12f
        h.addView(headSub)
        return h
    }

    private fun buildTabs() {
        tabRow.removeAllViews()
        for (i in tabNames.indices) {
            val t = TextView(this)
            t.text = tabNames[i]
            t.gravity = Gravity.CENTER
            t.textSize = 12f
            t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10))
            if (i == tab) {
                t.setTextColor(Color.WHITE)
                t.typeface = Typeface.DEFAULT_BOLD
                val g = GradientDrawable()
                g.setColor(Ui.ACC)
                g.cornerRadius = Ui.dp(this, 8).toFloat()
                t.background = g
            } else t.setTextColor(Ui.SUB)
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.leftMargin = Ui.dp(this, 2)
            lp.rightMargin = Ui.dp(this, 2)
            t.layoutParams = lp
            val idx = i
            t.setOnClickListener { show(idx) }
            tabRow.addView(t)
        }
    }

    fun show(index: Int) {
        tab = index
        buildTabs()
        body.removeAllViews()
        val v = when (index) {
            0 -> homeScreen()
            1 -> ideaScreen()
            2 -> Screens.script(this)
            3 -> Screens.image(this)
            4 -> Screens.meta(this)
            else -> Screens.record(this)
        }
        body.addView(v, FrameLayout.LayoutParams(-1, -1))
        val e = editing
        headSub.text = if (e == null) "未選択 / 全${store.projects.size}件"
        else "${e.title.ifBlank { "無題" }} ・ ${Project.typeLabel(e.type)}"
    }

    fun refresh() = show(tab)

    fun previewView(spec: ImageSpec): Pair<ImageView, () -> Unit> {
        val iv = ImageView(this)
        iv.adjustViewBounds = true
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.setBackgroundColor(Color.parseColor("#05070A"))
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = Ui.dp(this, 4)
        lp.bottomMargin = Ui.dp(this, 10)
        iv.layoutParams = lp
        val redraw = {
            try {
                iv.setImageBitmap(Frames.render(spec, bgOf(spec), 0.42f))
            } catch (e: Throwable) {
                toast("描画に失敗しました")
            }
        }
        redraw()
        return Pair(iv, redraw)
    }

    fun bgOf(spec: ImageSpec): Bitmap? {
        if (spec.bg.isBlank()) return null
        if (spec.bg == cacheKey && cacheBmp != null) return cacheBmp
        cacheBmp = loadBitmap(spec.bg)
        cacheKey = spec.bg
        return cacheBmp
    }

    fun clearBgCache() {
        cacheKey = ""
        cacheBmp = null
    }

    // ---------------- ホーム ----------------

    private fun homeScreen(): View {
        val c = Ui.col(this, 14)
        if (Bridge.treeUri(this) == null) c.addView(Screens.folderCard(this))
        val list = store.sorted()
        val next = list.firstOrNull { it.status != Project.S_DONE }

        val hero = Ui.card(this)
        hero.addView(Ui.label(this, "次にやること", true))
        if (next == null) {
            val t = Ui.label(this, "ネタを1本入れるところから")
            t.textSize = 22f
            t.typeface = Typeface.DEFAULT_BOLD
            hero.addView(t)
            hero.addView(Ui.button(this, "ネタ帳をひらく", true) { show(1) })
        } else {
            val t = TextView(this)
            t.text = Project.nextAction(next)
            t.setTextColor(Ui.ACC)
            t.textSize = 23f
            t.typeface = Typeface.DEFAULT_BOLD
            hero.addView(t)
            hero.addView(Ui.label(this, next.title.ifBlank { "無題" }))
            val r = Ui.row(this)
            r.addView(Ui.button(this, "この作品を編集", true) { editing = next; show(2) })
            r.addView(Ui.button(this, "次の工程へ") { advance(next) })
            hero.addView(r)
        }
        c.addView(hero)

        c.addView(Ui.title(this, "パイプライン"))
        for (st in Project.STATUS_ORDER) {
            val group = list.filter { it.status == st }
            if (group.isEmpty()) continue
            val head = Ui.row(this)
            head.addView(Ui.chip(this, Project.statusLabel(st), statusColor(st)))
            head.addView(Ui.label(this, "${group.size}件", true))
            head.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 6))
            c.addView(head)
            for (p in group) c.addView(projectCard(p))
        }
        if (list.isEmpty()) c.addView(Ui.label(this, "まだありません。ネタタブから追加してください。", true))

        c.addView(Ui.spacer(this, 8))
        val tools = Ui.row(this)
        tools.addView(Ui.button(this, "バックアップ") { exportBackup() })
        tools.addView(Ui.button(this, "読み込み") { importBackup() })
        tools.addView(Ui.button(this, "受け渡し先") { Bridge.chooseFolder(this) })
        tools.addView(Ui.button(this, "設定を確認") { showSettings() })
        tools.addView(Ui.button(this, "チャンネル") { askChannel() })
        tools.addView(Ui.button(this, "AI接続先") { askHost() })
        tools.addView(Ui.button(this, "Cutの結果を取り込む") { Bridge.pullResults(this, true) })
        c.addView(Ui.scrollH(this, tools))
        c.addView(Ui.spacer(this, 40))
        return Ui.scroll(this, c)
    }

    /**
     * 話速の実測。実際に読んで測る以外に正確な値を得る方法がない。
     * 想定尺とチャプターの精度が全部この係数に乗るので、一度測る価値がある。
     */
    fun measureSpeed(text: String) {
        val body = text.trim()
        if (body.length < 40) {
            toast("40字以上の本文がある作品で測ってください")
            return
        }
        val box = Ui.col(this, 16)
        box.addView(Ui.label(this, "この文を声に出して読んでください", true))

        val tv = TextView(this)
        tv.text = body.take(200)
        tv.setTextColor(Ui.TXT)
        tv.textSize = 17f
        tv.setLineSpacing(0f, 1.4f)
        box.addView(tv)

        val result = TextView(this)
        result.setTextColor(Ui.ACC)
        result.textSize = 18f
        result.typeface = Typeface.DEFAULT_BOLD
        result.text = "現在: " + Speed.label()
        box.addView(result)

        val used = body.take(200)
        var startAt = 0L
        var cps = 0f

        var btn: android.widget.Button? = null
        btn = Ui.button(this, "読み始める", true) {
            val b = btn ?: return@button
            if (startAt == 0L) {
                startAt = System.currentTimeMillis()
                b.text = "読み終えた"
                result.text = "計測中…"
            } else {
                val sec = (System.currentTimeMillis() - startAt) / 1000f
                startAt = 0L
                b.text = "もう一度測る"
                if (sec < 3f) {
                    result.text = "短すぎます。最後まで読んでください"
                } else {
                    cps = used.length / sec
                    result.text = String.format("%.1f 文字/秒（%d字 / %.1f秒）", cps, used.length, sec)
                }
            }
        }
        box.addView(btn)

        AlertDialog.Builder(this)
            .setTitle("話速を測る")
            .setView(Ui.scroll(this, box))
            .setPositiveButton("この値を使う") { _, _ ->
                if (cps <= 0f) toast("測定していません")
                else {
                    Speed.save(this, cps)
                    toast("話速を " + Speed.label() + " にしました")
                    refresh()
                }
            }
            .setNeutralButton("既定に戻す") { _, _ ->
                Speed.reset(this)
                toast("既定値に戻しました")
                refresh()
            }
            .setNegativeButton("やめる", null)
            .show()
    }

    fun askChannel() {
        val name = Ui.edit(this, "@channel")
        name.setText(Channel.name(this))
        val box = Ui.col(this, 16)
        box.addView(Ui.label(this, "チャンネル名", true))
        box.addView(name)
        box.addView(Ui.label(this, "画像タブとメタタブの初期値に使います", true))
        AlertDialog.Builder(this)
            .setTitle("チャンネル設定")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                Channel.save(this, name.text.toString())
                toast("保存しました")
                refresh()
            }
            .setNegativeButton("やめる", null)
            .show()
    }

    private fun showSettings() {
        val path = Bridge.treePath(this)
        val msg = buildString {
            append("受け渡し先: ")
            append(if (Bridge.treeUri(this@MainActivity) == null) "未設定" else (path ?: "設定済み（実パス不明）"))
            append("\n\nAI接続先: ")
            append(Bonsai.host(this@MainActivity))
            append("\n\nチャンネル: ")
            append(Channel.name(this@MainActivity).ifBlank { "未設定" })
            append("\n\n話速: ")
            append(Speed.label())
            append("\n\n作品数: ")
            append(store.projects.size)
        }
        AlertDialog.Builder(this)
            .setTitle("設定")
            .setMessage(msg)
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun askHost() {
        val e = Ui.edit(this, "http://127.0.0.1:8080")
        e.setText(Bonsai.host(this))
        val box = Ui.col(this, 16)
        box.addView(Ui.label(this, "BonsaiApp のアドレス", true))
        box.addView(e)
        AlertDialog.Builder(this)
            .setTitle("AI接続先")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                Bonsai.setHost(this, e.text.toString())
                toast("保存しました")
            }
            .setNegativeButton("やめる", null)
            .show()
    }

    /** Cut が書き出した完成ファイルを外部プレイヤーで開く */
    fun openOutput(p: Project) {
        if (p.outputUri.isBlank()) {
            toast("完成ファイルがありません")
            return
        }
        try {
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(Uri.parse(p.outputUri), "video/*")
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(i)
        } catch (e: Throwable) {
            toast("開けるアプリがありません")
        }
    }

    fun advance(p: Project) {
        val i = Project.STATUS_ORDER.indexOf(p.status)
        p.status = Project.STATUS_ORDER[Math.min(i + 1, Project.STATUS_ORDER.size - 1)]
        store.save()
        refresh()
    }

    private fun statusColor(s: String): Int = when (s) {
        Project.S_IDEA -> Color.parseColor("#5A8FBF")
        Project.S_SCRIPT -> Color.parseColor("#BF9A3A")
        Project.S_SHOOT -> Color.parseColor("#BF6B3A")
        Project.S_EDIT -> Color.parseColor("#7B5ABF")
        Project.S_PUBLISH -> Color.parseColor("#3AA675")
        else -> Color.parseColor("#5A6470")
    }

    private fun projectCard(p: Project): View {
        val card = Ui.card(this)
        val row = Ui.row(this)

        val thumb = ImageView(this)
        thumb.adjustViewBounds = true
        val spec = p.adoptedThumb()
        if (spec != null) {
            try {
                thumb.setImageBitmap(Frames.render(spec, loadBitmap(spec.bg), 0.10f))
            } catch (e: Throwable) {
            }
        }
        row.addView(thumb, LinearLayout.LayoutParams(Ui.dp(this, 76), -2))

        val info = Ui.col(this, 0)
        info.setPadding(Ui.dp(this, 10), 0, 0, 0)
        val t = TextView(this)
        t.text = p.title.ifBlank { "無題" }
        t.setTextColor(Ui.TXT)
        t.textSize = 16f
        t.typeface = Typeface.DEFAULT_BOLD
        info.addView(t)
        val dur = if (p.hasReal()) "実尺 ${Ui.mmss(p.realTotal)}" else "想定 ${Ui.mmss(p.seconds())}"
        info.addView(Ui.label(this, "${Project.typeLabel(p.type)} ・ $dur", true))
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(row)

        val btns = Ui.row(this)
        btns.addView(Ui.button(this, "編集", true) { editing = p; show(2) })
        btns.addView(Ui.button(this, "カンペ") {
            editing = p
            startActivity(Intent(this, PrompterActivity::class.java).putExtra("id", p.id))
        })
        btns.addView(Ui.button(this, "戻す") {
            val i = Project.STATUS_ORDER.indexOf(p.status)
            p.status = Project.STATUS_ORDER[Math.max(i - 1, 0)]
            store.save(); refresh()
        })
        btns.addView(Ui.button(this, "削除") { confirmDelete(p) })
        card.addView(Ui.scrollH(this, btns))
        return card
    }

    private fun confirmDelete(p: Project) {
        AlertDialog.Builder(this)
            .setTitle("削除しますか")
            .setMessage(p.title.ifBlank { "無題" })
            .setPositiveButton("削除") { _, _ ->
                if (editing === p) editing = null
                store.remove(p)
                refresh()
            }
            .setNegativeButton("やめる", null)
            .show()
    }

    // ---------------- ネタ ----------------

    private fun ideaScreen(): View {
        val c = Ui.col(this, 14)
        c.addView(Ui.title(this, "ネタ帳"))

        val input = Ui.edit(this, "思いついたことを1行で")
        c.addView(input)
        val memo = Ui.edit(this, "補足メモ（任意）", true, 2)
        c.addView(memo)

        var type = Project.T_TALK
        val labels = listOf(
            Project.T_TALK to "一人喋り",
            Project.T_SLIDE to "写真スライド",
            Project.T_SCREEN to "画面録画"
        )
        val btns = mutableListOf<TextView>()
        val typeRow = Ui.row(this)
        for ((k, v) in labels) {
            val b = Ui.button(this, v, k == type) {
                type = k
                for ((i, bb) in btns.withIndex()) {
                    val on = labels[i].first == type
                    val g = GradientDrawable()
                    g.setColor(if (on) Ui.ACC else Color.parseColor("#232A33"))
                    g.cornerRadius = Ui.dp(this, 8).toFloat()
                    bb.background = g
                    bb.setTextColor(if (on) Color.WHITE else Ui.TXT)
                }
            }
            btns.add(b)
            typeRow.addView(b)
        }
        c.addView(Ui.label(this, "型", true))
        c.addView(Ui.scrollH(this, typeRow))

        c.addView(Ui.button(this, "ネタを追加", true) {
            val s = input.text.toString().trim()
            if (s.isEmpty()) toast("1行入れてください")
            else {
                store.add(Project(title = s, type = type, memo = memo.text.toString()))
                input.setText(""); memo.setText("")
                refresh()
            }
        })

        c.addView(Ui.spacer(this, 10))
        c.addView(Ui.title(this, "ストック"))
        val ideas = store.sorted().filter { it.status == Project.S_IDEA }
        if (ideas.isEmpty()) c.addView(Ui.label(this, "ストックは空です", true))
        for (p in ideas) {
            val card = Ui.card(this)
            val t = TextView(this)
            t.text = p.title
            t.setTextColor(Ui.TXT)
            t.textSize = 16f
            t.typeface = Typeface.DEFAULT_BOLD
            card.addView(t)
            if (p.memo.isNotBlank()) card.addView(Ui.label(this, p.memo, true))
            val r = Ui.row(this)
            r.addView(Ui.button(this, "台本へ昇格", true) {
                editing = p
                if (p.scenes.isEmpty()) p.scenes = Templates.of(p.type).toMutableList()
                p.status = Project.S_SCRIPT
                store.save()
                show(2)
            })
            r.addView(Ui.button(this, "削除") { confirmDelete(p) })
            card.addView(r)
            c.addView(card)
        }
        c.addView(Ui.spacer(this, 40))
        return Ui.scroll(this, c)
    }

    // ---------------- 共通 ----------------

    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    fun copy(text: String, note: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("tubedesk", text))
        toast("$note をコピーしました")
    }

    fun pickImage(cb: (Uri) -> Unit) {
        pickCb = cb
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        startActivityForResult(i, 11)
    }

    fun createFile(name: String, mime: String, cb: (Uri) -> Unit) {
        saveCb = cb
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = mime
        i.putExtra(Intent.EXTRA_TITLE, name)
        startActivityForResult(i, 12)
    }

    fun openFile(mime: String, cb: (Uri) -> Unit) {
        pickCb = cb
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = mime
        startActivityForResult(i, 13)
    }

    fun pickTree(cb: (Uri) -> Unit) {
        treeCb = cb
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 14)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (res != RESULT_OK) return
        val uri = data?.data ?: return
        when (req) {
            11 -> {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                }
                pickCb?.invoke(uri); pickCb = null
            }
            12 -> { saveCb?.invoke(uri); saveCb = null }
            13 -> { pickCb?.invoke(uri); pickCb = null }
            14 -> {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: Exception) {
                }
                treeCb?.invoke(uri); treeCb = null
            }
        }
    }

    fun loadBitmap(uriStr: String): Bitmap? {
        if (uriStr.isBlank()) return null
        return try {
            val uri = Uri.parse(uriStr)
            val o1 = BitmapFactory.Options()
            o1.inJustDecodeBounds = true
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o1) }
            var sm = 1
            while (o1.outWidth / sm > 2200 || o1.outHeight / sm > 2200) sm *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = sm
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
        } catch (e: Throwable) {
            null
        }
    }

    private fun exportBackup() {
        createFile("tubedesk_backup.json", "application/json") { uri ->
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(store.dump().toByteArray()) }
                toast("書き出しました")
            } catch (e: Exception) {
                toast("失敗: ${e.message}")
            }
        }
    }

    private fun importBackup() {
        openFile("application/json") { uri ->
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val n = store.restore(text)
                if (n < 0) toast("読み込めませんでした") else {
                    editing = null
                    toast("${n}件 読み込みました")
                    refresh()
                }
            } catch (e: Exception) {
                toast("失敗: ${e.message}")
            }
        }
    }
}
