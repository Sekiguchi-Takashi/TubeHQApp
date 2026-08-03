package com.appathy.tubehq

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
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
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

    private val tabNames = listOf("ホーム", "ネタ", "台本", "サムネ", "メタ")

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
        tabRow.setBackgroundColor(Color.parseColor("#121820"))
        tabRow.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 8))
        root.addView(tabRow, Ui.lp(-1, -2))

        setContentView(root)
        buildTabs()
        show(0)
    }

    private fun buildHeader(): View {
        val h = LinearLayout(this)
        h.orientation = LinearLayout.VERTICAL
        h.setBackgroundColor(Color.parseColor("#121820"))
        h.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 12))

        val t = TextView(this)
        t.text = "TubeHQ"
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
                t.setTextColor(Color.parseColor("#101418"))
                t.typeface = Typeface.DEFAULT_BOLD
                val g = GradientDrawable()
                g.setColor(Ui.ACC)
                g.cornerRadius = Ui.dp(this, 8).toFloat()
                t.background = g
            } else {
                t.setTextColor(Ui.SUB)
            }
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
            0 -> homeScreen()
            1 -> ideaScreen()
            2 -> Screens.script(this)
            3 -> Screens.thumb(this)
            else -> Screens.meta(this)
        }
        body.addView(v, FrameLayout.LayoutParams(-1, -1))
        val e = editing
        headSub.text = if (e == null) "作品未選択 / 全${store.projects.size}件"
        else "編集中: ${e.title.ifBlank { "無題" }} (${Project.typeLabel(e.type)})"
    }

    fun refresh() = show(tab)

    // ---------------- ホーム ----------------

    private fun homeScreen(): View {
        val c = Ui.col(this, 14)

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
            t.textSize = 24f
            t.typeface = Typeface.DEFAULT_BOLD
            hero.addView(t)
            hero.addView(Ui.label(this, next.title.ifBlank { "無題" }))
            val r = Ui.row(this)
            r.addView(Ui.button(this, "この作品を編集", true) {
                editing = next
                show(2)
            })
            r.addView(Ui.button(this, "次の工程へ") {
                val i = Project.STATUS_ORDER.indexOf(next.status)
                next.status = Project.STATUS_ORDER[Math.min(i + 1, Project.STATUS_ORDER.size - 1)]
                store.save()
                refresh()
            })
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

        if (list.isEmpty()) c.addView(Ui.label(this, "まだ何もありません。ネタタブから追加してください。", true))

        c.addView(Ui.spacer(this, 8))
        val tools = Ui.row(this)
        tools.addView(Ui.button(this, "バックアップ書き出し") { exportBackup() })
        tools.addView(Ui.button(this, "読み込み") { importBackup() })
        c.addView(tools)
        c.addView(Ui.spacer(this, 40))

        return Ui.scroll(this, c)
    }

    private fun statusColor(s: String): Int = when (s) {
        Project.S_IDEA -> Color.parseColor("#7FB3D5")
        Project.S_SCRIPT -> Color.parseColor("#E8C25A")
        Project.S_SHOOT -> Color.parseColor("#E8925A")
        Project.S_EDIT -> Color.parseColor("#B07FD5")
        else -> Color.parseColor("#6FCF97")
    }

    private fun projectCard(p: Project): View {
        val card = Ui.card(this)
        val t = TextView(this)
        t.text = p.title.ifBlank { "無題" }
        t.setTextColor(Ui.TXT)
        t.textSize = 16f
        t.typeface = Typeface.DEFAULT_BOLD
        card.addView(t)

        val info = Ui.row(this)
        info.addView(Ui.chip(this, Project.typeLabel(p.type), Color.parseColor("#5A6B7D")))
        info.addView(Ui.label(this, "想定 ${Ui.mmss(p.seconds())} / ${p.chars()}字", true))
        card.addView(info)

        val r = Ui.row(this)
        r.addView(Ui.button(this, "編集", true) { editing = p; show(2) })
        r.addView(Ui.button(this, "カンペ") {
            editing = p
            startActivity(Intent(this, PrompterActivity::class.java).putExtra("id", p.id))
        })
        r.addView(Ui.button(this, "削除") { confirmDelete(p) })
        card.addView(r)
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

    // ---------------- ネタ帳 ----------------

    private fun ideaScreen(): View {
        val c = Ui.col(this, 14)
        c.addView(Ui.title(this, "ネタ帳"))

        val input = Ui.edit(this, "思いついたことを1行で")
        c.addView(input)

        val memo = Ui.edit(this, "補足メモ（任意）", true, 2)
        c.addView(memo)

        var type = Project.T_TALK
        val typeRow = Ui.row(this)
        val labels = listOf(
            Project.T_TALK to "一人喋り",
            Project.T_SLIDE to "写真スライド",
            Project.T_SCREEN to "画面録画"
        )
        val btns = mutableListOf<TextView>()
        for ((k, v) in labels) {
            val b = Ui.button(this, v, k == type) {
                type = k
                for ((i, bb) in btns.withIndex()) {
                    val on = labels[i].first == type
                    val g = GradientDrawable()
                    g.setColor(if (on) Ui.ACC else Color.parseColor("#222C38"))
                    g.cornerRadius = Ui.dp(this, 8).toFloat()
                    bb.background = g
                    bb.setTextColor(if (on) Color.parseColor("#101418") else Ui.TXT)
                }
            }
            btns.add(b)
            typeRow.addView(b)
        }
        c.addView(Ui.label(this, "型", true))
        c.addView(typeRow)

        c.addView(Ui.button(this, "ネタを追加", true) {
            val s = input.text.toString().trim()
            if (s.isEmpty()) {
                toast("1行入れてください")
            } else {
                val p = Project(title = s, type = type, memo = memo.text.toString())
                store.add(p)
                input.setText("")
                memo.setText("")
                toast("追加しました")
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

    // ---------------- 共通ユーティリティ ----------------

    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    fun copy(text: String, note: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("tubehq", text))
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

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        if (res != RESULT_OK) return
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
        }
    }

    fun loadBitmap(uriStr: String): Bitmap? {
        if (uriStr.isBlank()) return null
        return try {
            val uri = Uri.parse(uriStr)
            val o1 = BitmapFactory.Options()
            o1.inJustDecodeBounds = true
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o1) }
            var s = 1
            while (o1.outWidth / s > 2560 || o1.outHeight / s > 2560) s *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
        } catch (e: Exception) {
            null
        }
    }

    private fun exportBackup() {
        createFile("tubehq_backup.json", "application/json") { uri ->
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
