package com.appathy.tubecut

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.DocumentsContract
import android.view.View
import org.json.JSONArray
import org.json.JSONObject

/** 波形。全体の見取り図として置く。ここでは操作しない */
class WaveView(ctx: Context) : View(ctx) {

    var rms: FloatArray = FloatArray(0)
    var thresholdDb: Int = -38
    var marks: List<Float> = listOf()

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = Color.parseColor("#0A0E12")
        canvas.drawRect(0f, 0f, w, h, p)
        if (rms.isEmpty() || w <= 0) return

        val cols = w.toInt()
        val per = Math.max(1, rms.size / cols)
        val mid = h / 2f

        for (x in 0 until cols) {
            val from = x * per
            if (from >= rms.size) break
            var peak = 0f
            var i = from
            val to = Math.min(rms.size, from + per)
            while (i < to) {
                if (rms[i] > peak) peak = rms[i]
                i++
            }
            val quiet = Silence.db(peak) < thresholdDb
            p.color = if (quiet) Color.parseColor("#2A3038") else Ui.ACC
            val amp = (peak * 3.2f).coerceAtMost(1f) * (h / 2f - 2f)
            canvas.drawRect(x.toFloat(), mid - amp, x + 1f, mid + amp, p)
        }

        p.color = Color.parseColor("#66FFFFFF")
        for (m in marks) {
            val x = m * w
            canvas.drawRect(x, 0f, x + 1.5f, h, p)
        }
    }
}

/**
 * TubeDesk との受け渡し。EDIT_PLAN.md v2 の共有フォルダ方式。
 */
object Bridge {

    private const val PREF = "bridge"
    private const val KEY_TREE = "tree"

    fun treeUri(ctx: Context): Uri? {
        val s = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_TREE, "") ?: ""
        return if (s.isBlank()) null else Uri.parse(s)
    }

    /**
     * 選ばれたフォルダの実パス。Termux に渡すコマンドで使う。
     * SAF の tree document id は "primary:Download/tube" の形なので、
     * primary なら /sdcard 配下に読み替えられる。
     * 外部SDカードなど判別できない場合は null を返す。
     */
    fun treePath(ctx: Context): String? {
        val tree = treeUri(ctx) ?: return null
        return try {
            val id = DocumentsContract.getTreeDocumentId(tree)
            val i = id.indexOf(':')
            if (i < 0) return null
            val vol = id.substring(0, i)
            val rel = id.substring(i + 1)
            if (vol != "primary") return null
            if (rel.isBlank()) "/sdcard" else "/sdcard/" + rel
        } catch (e: Throwable) {
            null
        }
    }

    fun setTree(ctx: Context, uri: Uri) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_TREE, uri.toString()).apply()
    }

    /** Desk が置いた plan_*.json を読む */
    fun listPlans(ctx: Context): List<Pair<String, String>> {
        val tree = treeUri(ctx) ?: return listOf()
        val out = mutableListOf<Pair<String, String>>()
        for ((uri, name) in listChildren(ctx, tree)) {
            if (!name.startsWith("plan_")) continue
            val text = readText(ctx, uri)
            if (text.isBlank()) continue
            try {
                val o = JSONObject(text)
                out.add(Pair(o.optString("title").ifBlank { name }, text))
            } catch (e: Exception) {
            }
        }
        return out
    }

    fun applyPlan(p: EditProject, text: String) {
        try {
            val o = JSONObject(text)
            p.workId = o.optString("workId")
            p.name = o.optString("title").ifBlank { p.name }
            p.vertical = if (o.optBoolean("vertical")) 1 else 0
            val arr = o.optJSONArray("scenes") ?: return
            p.scenes = mutableListOf()
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                p.scenes.add(Pair(s.optString("sceneId"), s.optString("head")))
            }
        } catch (e: Exception) {
        }
    }

    fun buildResult(p: EditProject): String {
        val arr = JSONArray()
        var acc = 0L
        for (s in p.used()) {
            if (s.sceneId.isNotBlank()) {
                arr.put(
                    JSONObject()
                        .put("sceneId", s.sceneId)
                        .put("startSec", (acc / 1000).toInt())
                        .put("durationSec", (s.durMs() / 1000).toInt())
                )
            }
            acc += s.durMs()
        }
        return JSONObject()
            .put("v", 1)
            .put("workId", p.workId)
            .put("outputUri", p.outputUri)
            .put("totalSec", (p.usedMs() / 1000).toInt())
            .put("renderedAt", System.currentTimeMillis())
            .put("segments", arr)
            .toString(2)
    }

    fun writeResult(ctx: Context, p: EditProject): Boolean {
        val tree = treeUri(ctx) ?: return false
        val name = "result_${p.workId.ifBlank { p.id }}.json"
        return try {
            val docId = DocumentsContract.getTreeDocumentId(tree)
            val dir = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
            val target = findChild(ctx, tree, name) ?: DocumentsContract.createDocument(
                ctx.contentResolver, dir, "application/json", name
            ) ?: return false
            writeText(ctx, target, buildResult(p))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun writeScript(ctx: Context, name: String, text: String): Boolean {
        val tree = treeUri(ctx) ?: return false
        return try {
            val docId = DocumentsContract.getTreeDocumentId(tree)
            val dir = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
            val target = findChild(ctx, tree, name) ?: DocumentsContract.createDocument(
                ctx.contentResolver, dir, "text/plain", name
            ) ?: return false
            writeText(ctx, target, text)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** テロップPNGを受け渡し先に書き出す。ffmpeg が telop_NNN.png として参照する */
    fun writeTelops(ctx: Context, p: EditProject, w: Int, h: Int): Int {
        val tree = treeUri(ctx) ?: return -1
        var n = 0
        try {
            val docId = DocumentsContract.getTreeDocumentId(tree)
            val dir = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
            for ((i, t) in p.telops.withIndex()) {
                val name = String.format("telop_%03d.png", i)
                val target = findChild(ctx, tree, name) ?: DocumentsContract.createDocument(
                    ctx.contentResolver, dir, "image/png", name
                ) ?: continue
                val bmp = TelopDraw.render(t, w, h, p.accent)
                ctx.contentResolver.openOutputStream(target, "wt")?.use {
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bmp.recycle()
                n++
            }
        } catch (e: Throwable) {
        }
        return n
    }

    /** Desk の PlanProvider から台本を読む。未インストールなら空を返す */
    fun plansFromProvider(ctx: Context): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        try {
            if (ctx.packageManager.resolveContentProvider("com.appathy.tubedesk.plan", 0) == null) {
                return out
            }
            val uri = Uri.parse("content://com.appathy.tubedesk.plan/plans")
            ctx.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                val ti = cur.getColumnIndex("title")
                val ji = cur.getColumnIndex("json")
                if (ti < 0 || ji < 0) return out
                while (cur.moveToNext()) {
                    out.add(Pair(cur.getString(ti).ifBlank { "無題" }, cur.getString(ji)))
                }
            }
        } catch (e: Throwable) {
        }
        return out
    }

    /** Desk の PlanProvider へ結果を書き戻す */
    fun pushResultToProvider(ctx: Context, p: EditProject): Boolean {
        return try {
            if (ctx.packageManager.resolveContentProvider("com.appathy.tubedesk.plan", 0) == null) {
                return false
            }
            val v = android.content.ContentValues()
            v.put("workId", p.workId)
            v.put("json", buildResult(p))
            ctx.contentResolver.insert(
                Uri.parse("content://com.appathy.tubedesk.plan/plans"), v
            ) != null
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * 素材を受け渡し先へコピーする。
     * 重いレーンでは ffmpeg が Termux 側から素材を読むため、
     * SAF の URI では届かない。実体を共有フォルダに置く必要がある。
     */
    fun copyInto(ctx: Context, srcUri: Uri, name: String, mime: String): Long {
        val tree = treeUri(ctx) ?: return -1L
        return try {
            val docId = DocumentsContract.getTreeDocumentId(tree)
            val dir = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
            val target = findChild(ctx, tree, name) ?: DocumentsContract.createDocument(
                ctx.contentResolver, dir, mime, name
            ) ?: return -1L
            var total = 0L
            ctx.contentResolver.openInputStream(srcUri)?.use { input ->
                ctx.contentResolver.openOutputStream(target, "wt")?.use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        total += n
                    }
                    out.flush()
                }
            }
            total
        } catch (e: Throwable) {
            -1L
        }
    }

    fun exists(ctx: Context, name: String): Boolean {
        val tree = treeUri(ctx) ?: return false
        return findChild(ctx, tree, name) != null
    }

    fun writeText(ctx: Context, uri: Uri, text: String) {
        try {
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
        } catch (e: Exception) {
        }
    }

    fun readText(ctx: Context, uri: Uri): String = try {
        ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
    } catch (e: Exception) {
        ""
    }

    fun listChildren(ctx: Context, tree: Uri): List<Pair<Uri, String>> {
        val out = mutableListOf<Pair<Uri, String>>()
        try {
            val docId = DocumentsContract.getTreeDocumentId(tree)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            ctx.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            )?.use { cur ->
                while (cur.moveToNext()) {
                    out.add(
                        Pair(
                            DocumentsContract.buildDocumentUriUsingTree(tree, cur.getString(0)),
                            cur.getString(1)
                        )
                    )
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun findChild(ctx: Context, tree: Uri, name: String): Uri? =
        listChildren(ctx, tree).firstOrNull { it.second == name }?.first
}
