package com.appathy.tubecut

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
    var editing: EditProject? = null

    var rms: FloatArray = FloatArray(0)
    var rmsFor: String = ""
    /** 素材ごとの RMS。複数素材にまたがる検出のため */
    var rmsBySrc: HashMap<String, FloatArray> = HashMap()
    var keyCache: HashMap<String, LongArray> = HashMap()
    private val keyScanning = HashSet<String>()

    private lateinit var body: FrameLayout
    private lateinit var tabRow: LinearLayout
    private lateinit var headSub: TextView
    private var tab = 0

    val ui = Handler(Looper.getMainLooper())

    var runner: Runner? = null
    private var watchTick: Runnable? = null

    /** 重いレーンの見守り。2秒おきにファイルを読む */
    fun startWatch(p: EditProject, onUpdate: (Runner) -> Unit) {
        stopWatch()
        val r = Runner(this, p)
        r.running = true
        runner = r
        val tick = object : Runnable {
            override fun run() {
                val cur = runner ?: return
                Thread {
                    cur.poll()
                    ui.post {
                        if (runner !== cur) return@post
                        onUpdate(cur)
                        if (cur.done) {
                            runner = null
                        } else {
                            ui.postDelayed(this, 2000)
                        }
                    }
                }.start()
            }
        }
        watchTick = tick
        ui.post(tick)
    }

    fun stopWatch() {
        watchTick?.let { ui.removeCallbacks(it) }
        watchTick = null
        runner = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWatch()
    }

    private var pickCb: ((Uri) -> Unit)? = null
    private var saveCb: ((Uri) -> Unit)? = null
    private var treeCb: ((Uri) -> Unit)? = null

    private val tabNames = listOf("素材", "解析", "区間", "重ね", "出力")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)
        root.addView(buildHeader())

        body = FrameLayout(this)
        root.addView(body, Ui.lp(-1, 0, 1f))

        tabRow = LinearLayout(this)
        tabRow.orientation = LinearLayout.HORIZONTAL
        tabRow.setBackgroundColor(Color.parseColor("#121519"))
        tabRow.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 8))
        root.addView(tabRow, Ui.lp(-1, -2))

        setContentView(root)
        buildTabs()
        show(0)
    }

    private fun buildHeader(): View {
        val h = LinearLayout(this)
        h.orientation = LinearLayout.VERTICAL
        h.setBackgroundColor(Color.parseColor("#121519"))
        h.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 10))
        val t = TextView(this)
        t.text = "TubeCut"
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
            t.textSize = 13f
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
            lp.leftMargin = Ui.dp(this, 3)
            lp.rightMargin = Ui.dp(this, 3)
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
            0 -> sourceScreen()
            1 -> Screens.analyze(this)
            2 -> Screens.segments(this)
            3 -> Screens.overlay(this)
            else -> Screens.output(this)
        }
        body.addView(v, FrameLayout.LayoutParams(-1, -1))
        val e = editing
        headSub.text = if (e == null) "未選択 / 全${store.projects.size}件"
        else "${e.name.ifBlank { "無題" }} ・ ${e.sources.size}ファイル"
    }

    fun refresh() = show(tab)

    // ---------------- 素材 ----------------

    private fun sourceScreen(): View {
        val c = Ui.col(this, 14)

        if (editing == null) {
            if (Bridge.treeUri(this) == null) c.addView(Screens.folderCard(this))
            c.addView(Ui.button(this, "＋ 新しい編集を作る", true) {
                val p = EditProject(name = "無題")
                store.add(p)
                editing = p
                refresh()
            })
            c.addView(Ui.button(this, "Deskの台本から始める") { importPlan() })
            c.addView(Ui.title(this, "編集"))
            val list = store.sorted()
            if (list.isEmpty()) c.addView(Ui.label(this, "まだありません", true))
            for (p in list) {
                val card = Ui.card(this)
                val t = TextView(this)
                t.text = p.name.ifBlank { "無題" }
                t.setTextColor(Ui.TXT)
                t.textSize = 16f
                t.typeface = Typeface.DEFAULT_BOLD
                card.addView(t)
                card.addView(
                    Ui.label(
                        this,
                        "${p.sources.size}ファイル ・ ${p.used().size}区間 ・ ${Fmt.ms(p.usedMs())}",
                        true
                    )
                )
                val r = Ui.row(this)
                r.addView(Ui.button(this, "開く", true) { editing = p; refresh() })
                r.addView(Ui.button(this, "削除") { confirmDelete(p) })
                card.addView(r)
                c.addView(card)
            }
            c.addView(Ui.spacer(this, 8))
            c.addView(Screens.folderCard(this))
            c.addView(Ui.button(this, "AI接続先") { askHost() })
            c.addView(Ui.spacer(this, 40))
            return Ui.scroll(this, c)
        }

        val p = editing!!

        c.addView(Screens.folderCard(this))

        val nameEdit = Ui.edit(this, "編集の名前")
        nameEdit.setText(p.name)
        Screens.watch(nameEdit) { p.name = it; store.save() }
        c.addView(nameEdit)

        val top = Ui.row(this)
        top.addView(Ui.button(this, "ファイルを追加", true) { addSource() })
        top.addView(Ui.button(this, "Deskの台本を読む") { importPlan() })
        top.addView(Ui.button(this, "一覧へ") { editing = null; refresh() })
        c.addView(Ui.scrollH(this, top))

        if (p.workId.isNotBlank()) {
            c.addView(Ui.label(this, "Desk連携中（${p.scenes.size}シーン）", true))
        }

        for ((i, s) in p.sources.withIndex()) {
            val card = Ui.card(this)
            val row = Ui.row(this)
            val iv = ImageView(this)
            iv.adjustViewBounds = true
            row.addView(iv, LinearLayout.LayoutParams(Ui.dp(this, 76), -2))
            Thread {
                val bmp = Probe.frameAt(this, s.uri, Math.max(0L, s.durationMs / 3))
                if (bmp != null) ui.post { iv.setImageBitmap(scaleDown(bmp)) }
            }.start()

            val info = Ui.col(this, 0)
            info.setPadding(Ui.dp(this, 10), 0, 0, 0)
            val t = TextView(this)
            t.text = s.name
            t.setTextColor(Ui.TXT)
            t.textSize = 15f
            t.typeface = Typeface.DEFAULT_BOLD
            info.addView(t)
            info.addView(Ui.label(this, s.label(), true))
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(row)

            val r = Ui.row(this)
            r.addView(Ui.button(this, "↑") {
                if (i > 0) {
                    val tmp = p.sources[i - 1]; p.sources[i - 1] = p.sources[i]; p.sources[i] = tmp
                    store.save(); refresh()
                }
            })
            r.addView(Ui.button(this, "↓") {
                if (i < p.sources.size - 1) {
                    val tmp = p.sources[i + 1]; p.sources[i + 1] = p.sources[i]; p.sources[i] = tmp
                    store.save(); refresh()
                }
            })
            r.addView(Ui.button(this, "全体を1区間に") {
                p.segments.add(Segment(srcIndex = i, inMs = 0, outMs = s.durationMs, use = 1))
                store.save(); toast("区間を追加しました")
            })
            r.addView(Ui.button(this, "削除") {
                p.sources.removeAt(i)
                p.segments.removeAll { it.srcIndex == i }
                store.save(); refresh()
            })
            card.addView(Ui.scrollH(this, r))
            c.addView(card)
        }

        c.addView(laneCard(p))
        c.addView(Ui.spacer(this, 40))
        return Ui.scroll(this, c)
    }

    fun laneCard(p: EditProject): View {
        val card = Ui.card(this)
        val ok = !p.needsFfmpeg()
        val t = TextView(this)
        t.textSize = 17f
        t.typeface = Typeface.DEFAULT_BOLD
        t.setTextColor(if (ok) Ui.ACC else Ui.WARN)
        t.text = if (ok) "速いレーンが使えます" else "ffmpegが必要です"
        card.addView(t)

        val d = TextView(this)
        d.setTextColor(Ui.SUB)
        d.textSize = 13f
        d.text = if (ok) {
            "codec・解像度・fps が一致しています\n無劣化・再エンコードなし\n見積り 約${p.estimateSec()}秒"
        } else {
            val m = p.mismatch()
            val reasons = mutableListOf<String>()
            if (p.telops.isNotEmpty()) reasons.add("テロップ ${p.telops.size}件")
            if (p.bgmUri.isNotBlank()) reasons.add("BGM")
            if (p.vertical == 1) reasons.add("縦切り出し")
            if (m.isNotBlank()) reasons.add("素材の不一致")
            (if (reasons.isEmpty()) "素材が未解析です" else reasons.joinToString("・")) +
                (if (m.isNotBlank()) "\n$m" else "") +
                "\n見積り 約${p.estimateSec() / 60}分${p.estimateSec() % 60}秒"
        }
        card.addView(d)
        return card
    }

    private fun scaleDown(b: Bitmap): Bitmap =
        try {
            val w = 240
            val h = Math.max(1, b.height * w / Math.max(1, b.width))
            Bitmap.createScaledBitmap(b, w, h, true)
        } catch (e: Throwable) {
            b
        }

    private fun addSource() {
        val p = editing ?: return
        pickMedia { uri ->
            val s = Source(uri = uri.toString(), name = Probe.displayName(this, uri))
            p.sources.add(s)
            store.save()
            refresh()
            Thread {
                Probe.probe(this, s)
                ui.post {
                    store.save()
                    refresh()
                }
            }.start()
        }
    }

    private fun importPlan() {
        var plans = Bridge.plansFromProvider(this)
        if (plans.isEmpty()) plans = Bridge.listPlans(this)
        if (plans.isEmpty()) {
            toast("台本が見つかりません（Deskで作るか受け渡し先を設定してください）")
            return
        }
        val names = plans.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("台本を選ぶ")
            .setItems(names) { _, which ->
                val p = editing ?: EditProject().also { store.add(it); editing = it }
                Bridge.applyPlan(p, plans[which].second)
                store.save()
                toast("読み込みました")
                refresh()
            }
            .setNegativeButton("やめる", null)
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

    private fun confirmDelete(p: EditProject) {
        AlertDialog.Builder(this)
            .setTitle("削除しますか")
            .setMessage(p.name.ifBlank { "無題" })
            .setPositiveButton("削除") { _, _ ->
                if (editing === p) editing = null
                store.remove(p)
                refresh()
            }
            .setNegativeButton("やめる", null)
            .show()
    }

    // ---------------- 共通 ----------------

    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    fun copy(text: String, note: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("tubecut", text))
        toast("$note をコピーしました")
    }

    /**
     * キーフレーム走査を裏で始める。
     * 区間タブの吸着警告は「調べる」を押さなくても出したいが、
     * 走査はファイル全体を舐めるので UI を止めない。
     */
    fun scanKeyframes(sources: List<Source>, onDone: () -> Unit) {
        val todo = sources.filter { it.probed == 1 && !keyCache.containsKey(it.uri) && !keyScanning.contains(it.uri) }
        if (todo.isEmpty()) return
        todo.forEach { keyScanning.add(it.uri) }
        Thread {
            for (s in todo) {
                val k = Probe.keyframes(this, s)
                keyCache[s.uri] = k
                keyScanning.remove(s.uri)
            }
            ui.post { onDone() }
        }.start()
    }

    fun keyScanBusy(): Boolean = keyScanning.isNotEmpty()

    fun keyframesOf(s: Source): LongArray {
        val cached = keyCache[s.uri]
        if (cached != null) return cached
        val k = Probe.keyframes(this, s)
        keyCache[s.uri] = k
        return k
    }

    fun pickMedia(cb: (Uri) -> Unit) {
        pickCb = cb
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "video/*"
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        startActivityForResult(i, 11)
    }

    fun pickAudio(cb: (Uri) -> Unit) {
        pickCb = cb
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "audio/*"
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
}
