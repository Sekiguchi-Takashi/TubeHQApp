package com.appathy.tubeshot

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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    lateinit var store: Store
    var editing: Shot? = null

    private lateinit var body: FrameLayout
    private lateinit var tabRow: LinearLayout
    private lateinit var headSub: TextView
    private var tab = 0

    private var pickCb: ((Uri) -> Unit)? = null
    private var saveCb: ((Uri) -> Unit)? = null

    private var cacheKey = ""
    private var cacheBmp: Bitmap? = null

    private val tabNames = listOf("作品", "素材", "文字", "見た目", "書き出し")

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
        t.text = "TubeShot"
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
            0 -> listScreen()
            1 -> sourceScreen()
            2 -> Screens.text(this)
            3 -> Screens.look(this)
            else -> Screens.export(this)
        }
        body.addView(v, FrameLayout.LayoutParams(-1, -1))
        val e = editing
        headSub.text = if (e == null) "未選択 / 全${store.shots.size}枚"
        else "${e.name.ifBlank { "無題" }} ・ ${Shot.styleLabel(e.style)}"
    }

    fun refresh() = show(tab)

    // ---------------- プレビュー ----------------

    fun previewView(shot: Shot): Pair<ImageView, () -> Unit> {
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
                iv.setImageBitmap(Frames.render(shot, bgOf(shot), 0.42f))
            } catch (e: Throwable) {
                toast("描画に失敗しました")
            }
        }
        redraw()
        return Pair(iv, redraw)
    }

    fun bgOf(shot: Shot): Bitmap? {
        if (shot.bg.isBlank()) return null
        if (shot.bg == cacheKey && cacheBmp != null) return cacheBmp
        cacheBmp = loadBitmap(shot.bg)
        cacheKey = shot.bg
        return cacheBmp
    }

    // ---------------- 作品一覧 ----------------

    private fun listScreen(): View {
        val c = Ui.col(this, 14)

        c.addView(Ui.button(this, "＋ 新しい1枚を作る", true) {
            val s = Shot(name = "無題")
            store.add(s)
            editing = s
            show(1)
        })

        c.addView(Ui.title(this, "作品"))
        val list = store.sorted()
        if (list.isEmpty()) c.addView(Ui.label(this, "まだありません。上のボタンから作成してください。", true))

        for (s in list) {
            val card = Ui.card(this)
            val row = Ui.row(this)

            val thumb = ImageView(this)
            thumb.adjustViewBounds = true
            thumb.scaleType = ImageView.ScaleType.FIT_CENTER
            try {
                thumb.setImageBitmap(Frames.render(s, loadBitmap(s.bg), 0.10f))
            } catch (e: Throwable) {
            }
            row.addView(thumb, LinearLayout.LayoutParams(Ui.dp(this, 84), -2))

            val info = Ui.col(this, 0)
            info.setPadding(Ui.dp(this, 12), 0, 0, 0)
            val t = TextView(this)
            t.text = s.name.ifBlank { s.title.ifBlank { "無題" } }
            t.setTextColor(Ui.TXT)
            t.textSize = 16f
            t.typeface = Typeface.DEFAULT_BOLD
            info.addView(t)
            val chips = Ui.row(this)
            chips.addView(Ui.chip(this, Shot.styleLabel(s.style), Color.parseColor("#4A5560")))
            val sz = Shot.size(s.style)
            chips.addView(Ui.label(this, "${sz.first}×${sz.second}", true))
            info.addView(chips)
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(row)

            val btns = Ui.row(this)
            btns.addView(Ui.button(this, "編集", true) { editing = s; show(1) })
            btns.addView(Ui.button(this, "複製") {
                store.add(s.copyOf()); refresh()
            })
            btns.addView(Ui.button(this, "削除") { confirmDelete(s) })
            card.addView(btns)
            c.addView(card)
        }

        c.addView(Ui.spacer(this, 8))
        val tools = Ui.row(this)
        tools.addView(Ui.button(this, "バックアップ書き出し") { exportBackup() })
        tools.addView(Ui.button(this, "読み込み") { importBackup() })
        c.addView(tools)
        c.addView(Ui.spacer(this, 40))
        return Ui.scroll(this, c)
    }

    private fun confirmDelete(s: Shot) {
        AlertDialog.Builder(this)
            .setTitle("削除しますか")
            .setMessage(s.name.ifBlank { "無題" })
            .setPositiveButton("削除") { _, _ ->
                if (editing === s) editing = null
                store.remove(s)
                refresh()
            }
            .setNegativeButton("やめる", null)
            .show()
    }

    // ---------------- 素材・様式 ----------------

    private fun sourceScreen(): View {
        val s = editing ?: return Screens.empty(this, "作品タブから1枚選んでください。")
        val c = Ui.col(this, 14)

        val (pv, redraw) = previewView(s)
        c.addView(pv)

        c.addView(Ui.label(this, "名前", true))
        val nameEdit = Ui.edit(this, "作品名")
        nameEdit.setText(s.name)
        Screens.watch(nameEdit) { s.name = it; store.save() }
        c.addView(nameEdit)

        c.addView(Ui.label(this, "様式", true))
        val styleBox = LinearLayout(this)
        styleBox.orientation = LinearLayout.HORIZONTAL
        val sBtns = mutableListOf<TextView>()
        for (st in Shot.STYLES) {
            val b = Ui.button(this, Shot.styleLabel(st), st == s.style) {
                s.style = st
                store.save()
                for ((j, bb) in sBtns.withIndex()) {
                    val on = Shot.STYLES[j] == s.style
                    val g = GradientDrawable()
                    g.setColor(if (on) Ui.ACC else Color.parseColor("#232A33"))
                    g.cornerRadius = Ui.dp(this, 8).toFloat()
                    bb.background = g
                    bb.setTextColor(if (on) Color.WHITE else Ui.TXT)
                }
                redraw()
                headSub.text = "${s.name.ifBlank { "無題" }} ・ ${Shot.styleLabel(s.style)}"
            }
            sBtns.add(b)
            styleBox.addView(b)
        }
        c.addView(Ui.scrollH(this, styleBox))

        c.addView(Ui.label(this, "背景", true))
        val r1 = Ui.row(this)
        r1.addView(Ui.button(this, "画像を選ぶ", true) {
            pickImage { uri ->
                s.bg = uri.toString()
                cacheKey = ""
                store.save()
                redraw()
            }
        })
        r1.addView(Ui.button(this, "背景なし") {
            s.bg = ""; cacheKey = ""; store.save(); redraw()
        })
        c.addView(r1)
        c.addView(Ui.label(this, "撮影した写真でも、生成画像でも、スクショでも構いません。", true))

        c.addView(Ui.spacer(this, 10))
        c.addView(Ui.label(this, "プリセット（文字と色味をまとめて入れる）", true))
        val pBox = LinearLayout(this)
        pBox.orientation = LinearLayout.HORIZONTAL
        for (pr in Presets.ALL) {
            pBox.addView(Ui.button(this, pr.label) {
                Presets.apply(s, pr)
                store.save()
                redraw()
                toast("${pr.label} を適用しました")
            })
        }
        c.addView(Ui.scrollH(this, pBox))

        c.addView(Ui.spacer(this, 40))
        return Ui.scroll(this, c)
    }

    // ---------------- 共通 ----------------

    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    fun copy(text: String, note: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("tubeshot", text))
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
        createFile("tubeshot_backup.json", "application/json") { uri ->
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
                    toast("${n}枚 読み込みました")
                    refresh()
                }
            } catch (e: Exception) {
                toast("失敗: ${e.message}")
            }
        }
    }
}
