package com.appathy.tubedesk

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject

/**
 * TubeCut との受け渡し。EDIT_PLAN.md v2 の共有フォルダ方式。
 * ContentProvider 方式は未実装（Cut側の実装後に追加する）。
 */
object Bridge {

    private const val PREF = "bridge"
    private const val KEY_TREE = "tree"

    fun treeUri(ctx: Context): Uri? {
        val s = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_TREE, "") ?: ""
        return if (s.isBlank()) null else Uri.parse(s)
    }

    fun setTree(ctx: Context, uri: Uri) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_TREE, uri.toString()).apply()
    }

    /**
     * 選ばれたフォルダの実パス。表示用。
     * SAF の tree document id は "primary:Download/tube" の形。
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

    fun chooseFolder(act: MainActivity) {
        act.pickTree { uri ->
            setTree(act, uri)
            act.toast("受け渡し先を設定しました")
            act.refresh()
        }
    }

    /** Desk → Cut。plan_<workId>.json を書き出し、status を撮影待ちへ */
    fun pushPlan(act: MainActivity, p: Project) {
        val json = buildPlan(p)
        val name = "plan_${p.id}.json"
        val tree = treeUri(act)
        if (tree == null) {
            act.createFile(name, "application/json") { uri ->
                writeText(act, uri, json)
                afterPush(act, p)
            }
            return
        }
        try {
            val docId = DocumentsContract.getTreeDocumentId(tree)
            val dir = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
            val existing = findChild(act, tree, name)
            val target = existing ?: DocumentsContract.createDocument(
                act.contentResolver, dir, "application/json", name
            )
            if (target == null) {
                act.toast("書き出せませんでした")
                return
            }
            writeText(act, target, json)
            afterPush(act, p)
        } catch (e: Exception) {
            act.toast("失敗: ${e.message}")
        }
    }

    private fun afterPush(act: MainActivity, p: Project) {
        if (p.status == Project.S_SCRIPT) p.status = Project.S_SHOOT
        act.store.save()
        act.toast("Cutへ送りました")
        act.refresh()
    }

    fun buildPlan(p: Project): String {
        val arr = JSONArray()
        for (s in p.scenes) {
            arr.put(
                JSONObject()
                    .put("sceneId", s.id)
                    .put("head", s.head)
                    .put("body", s.body)
                    .put("note", s.note)
                    .put("estimateSec", p.sceneSeconds(s))
            )
        }
        return JSONObject()
            .put("v", 1)
            .put("workId", p.id)
            .put("title", p.title)
            .put("type", p.type)
            .put("vertical", false)
            .put("scenes", arr)
            .toString(2)
    }

    /** Cut → Desk。result_*.json を取り込む */
    fun pullResults(act: MainActivity, verbose: Boolean = false) {
        val viaProvider = pullFromProvider(act)
        if (viaProvider > 0) {
            act.toast("${viaProvider}件 取り込みました")
            act.refresh()
            return
        }
        val tree = treeUri(act)
        if (tree == null) {
            if (verbose) {
                act.openFile("application/json") { uri ->
                    val n = applyResult(act, readText(act, uri))
                    act.toast(if (n) "取り込みました" else "対応する作品がありません")
                    act.refresh()
                }
            }
            return
        }
        try {
            var count = 0
            for (child in listChildren(act, tree)) {
                if (!child.second.startsWith("result_")) continue
                if (applyResult(act, readText(act, child.first))) count++
            }
            if (count > 0) {
                act.store.save()
                act.toast("${count}件 取り込みました")
                act.refresh()
            } else if (verbose) {
                act.toast("新しい結果はありません")
            }
        } catch (e: Exception) {
            if (verbose) act.toast("失敗: ${e.message}")
        }
    }

    private fun applyResult(act: MainActivity, text: String): Boolean =
        applyResultTo(act.store, text)

    /** Store だけで完結する版。ContentProvider から呼ぶ */
    fun applyResultTo(store: Store, text: String): Boolean {
        if (text.isBlank()) return false
        return try {
            val o = JSONObject(text)
            val workId = o.optString("workId")
            val p = store.projects.firstOrNull { it.id == workId } ?: return false
            p.realTotal = o.optInt("totalSec", -1)
            p.outputUri = o.optString("outputUri")
            val segs = o.optJSONArray("segments")
            for (s in p.scenes) {
                s.realStart = -1
                s.realDur = -1
            }
            if (segs != null) {
                for (i in 0 until segs.length()) {
                    val g = segs.getJSONObject(i)
                    val sid = g.optString("sceneId")
                    if (sid.isBlank() || sid == "null") continue
                    val sc = p.scenes.firstOrNull { it.id == sid } ?: continue
                    sc.realStart = g.optInt("startSec", -1)
                    sc.realDur = g.optInt("durationSec", -1)
                }
            }
            if (p.status == Project.S_SHOOT || p.status == Project.S_EDIT) {
                p.status = Project.S_PUBLISH
            }
            store.save()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Cut の ResultProvider から結果を取り込む。未インストールなら静かに0を返す */
    fun pullFromProvider(act: MainActivity): Int {
        return try {
            val uri = Uri.parse("content://com.appathy.tubecut.result/results")
            if (act.packageManager.resolveContentProvider("com.appathy.tubecut.result", 0) == null) {
                return 0
            }
            var n = 0
            act.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                val ji = cur.getColumnIndex("json")
                if (ji < 0) return 0
                while (cur.moveToNext()) {
                    if (applyResultTo(act.store, cur.getString(ji))) n++
                }
            }
            n
        } catch (e: Throwable) {
            0
        }
    }

    // ---------------- SAF ユーティリティ ----------------

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
                    val id = cur.getString(0)
                    val name = cur.getString(1)
                    out.add(Pair(DocumentsContract.buildDocumentUriUsingTree(tree, id), name))
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun findChild(ctx: Context, tree: Uri, name: String): Uri? =
        listChildren(ctx, tree).firstOrNull { it.second == name }?.first
}
