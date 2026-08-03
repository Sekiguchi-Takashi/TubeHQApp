package com.appathy.tubehq

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Scene(
    var head: String = "",
    var body: String = "",
    var note: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("head", head).put("body", body).put("note", note)

    companion object {
        fun from(o: JSONObject) = Scene(
            o.optString("head"), o.optString("body"), o.optString("note")
        )
    }
}

class ThumbSpec(
    var main: String = "",
    var sub: String = "",
    var ep: String = "",
    var bg: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("main", main).put("sub", sub).put("ep", ep).put("bg", bg)

    companion object {
        fun from(o: JSONObject) = ThumbSpec(
            o.optString("main"), o.optString("sub"), o.optString("ep"), o.optString("bg")
        )
    }
}

class Project(
    var id: String = System.currentTimeMillis().toString(),
    var title: String = "",
    var type: String = T_TALK,
    var status: String = S_IDEA,
    var memo: String = "",
    var scenes: MutableList<Scene> = mutableListOf(),
    var thumbs: MutableList<ThumbSpec> = mutableListOf(ThumbSpec(), ThumbSpec()),
    var metaTitle: String = "",
    var metaDesc: String = "",
    var metaTags: String = "",
    var created: Long = System.currentTimeMillis()
) {
    fun chars(): Int = scenes.sumOf { it.body.length }

    fun seconds(): Int = Math.max(1, Math.round(chars() / CPS).toInt())

    fun sceneSeconds(s: Scene): Int =
        Math.max(3, Math.round(s.body.length / CPS).toInt())

    fun toJson(): JSONObject {
        val sa = JSONArray()
        scenes.forEach { sa.put(it.toJson()) }
        val ta = JSONArray()
        thumbs.forEach { ta.put(it.toJson()) }
        return JSONObject()
            .put("id", id).put("title", title).put("type", type)
            .put("status", status).put("memo", memo)
            .put("scenes", sa).put("thumbs", ta)
            .put("metaTitle", metaTitle).put("metaDesc", metaDesc)
            .put("metaTags", metaTags).put("created", created)
    }

    companion object {
        const val T_TALK = "talk"
        const val T_SLIDE = "slide"
        const val T_SCREEN = "screen"

        const val S_IDEA = "idea"
        const val S_SCRIPT = "script"
        const val S_SHOOT = "shoot"
        const val S_EDIT = "edit"
        const val S_DONE = "done"

        const val CPS = 5.3f

        val STATUS_ORDER = listOf(S_IDEA, S_SCRIPT, S_SHOOT, S_EDIT, S_DONE)

        fun statusLabel(s: String) = when (s) {
            S_IDEA -> "ネタ"
            S_SCRIPT -> "台本済"
            S_SHOOT -> "撮影待ち"
            S_EDIT -> "素材あり"
            else -> "投稿済"
        }

        fun typeLabel(t: String) = when (t) {
            T_TALK -> "一人喋り"
            T_SLIDE -> "写真スライド"
            else -> "画面録画"
        }

        fun nextAction(p: Project) = when (p.status) {
            S_IDEA -> "台本を書く"
            S_SCRIPT -> "カンペを見ながら撮影"
            S_SHOOT -> "素材をDownloadに置く"
            S_EDIT -> "サムネとメタデータを作る"
            else -> "分析・次のネタへ"
        }

        fun from(o: JSONObject): Project {
            val p = Project()
            p.id = o.optString("id", System.currentTimeMillis().toString())
            p.title = o.optString("title")
            p.type = o.optString("type", T_TALK)
            p.status = o.optString("status", S_IDEA)
            p.memo = o.optString("memo")
            p.metaTitle = o.optString("metaTitle")
            p.metaDesc = o.optString("metaDesc")
            p.metaTags = o.optString("metaTags")
            p.created = o.optLong("created", System.currentTimeMillis())
            val sa = o.optJSONArray("scenes")
            p.scenes = mutableListOf()
            if (sa != null) for (i in 0 until sa.length()) p.scenes.add(Scene.from(sa.getJSONObject(i)))
            val ta = o.optJSONArray("thumbs")
            p.thumbs = mutableListOf()
            if (ta != null) for (i in 0 until ta.length()) p.thumbs.add(ThumbSpec.from(ta.getJSONObject(i)))
            while (p.thumbs.size < 2) p.thumbs.add(ThumbSpec())
            return p
        }
    }
}

object Templates {
    fun of(type: String): List<Scene> = when (type) {
        Project.T_TALK -> listOf(
            Scene("掴み（15秒）", "", "結論を先に匂わせる。質問で始める"),
            Scene("結論", "", ""),
            Scene("理由①", "", ""),
            Scene("理由②", "", ""),
            Scene("理由③", "", ""),
            Scene("実例・体験談", "", ""),
            Scene("まとめ", "", ""),
            Scene("CTA", "", "チャンネル登録・次の動画へ誘導")
        )
        Project.T_SLIDE -> listOf(
            Scene("タイトルカット", "", "使う写真のメモ"),
            Scene("シーン1", "", ""),
            Scene("シーン2", "", ""),
            Scene("シーン3", "", ""),
            Scene("シーン4", "", ""),
            Scene("エンディング", "", "")
        )
        else -> listOf(
            Scene("導入・完成形の提示", "", "画面：完成画面"),
            Scene("手順1", "", "画面：操作メモ"),
            Scene("手順2", "", "画面：操作メモ"),
            Scene("手順3", "", "画面：操作メモ"),
            Scene("つまずきポイント", "", ""),
            Scene("まとめ", "", "")
        )
    }
}

class Store(val ctx: Context) {

    val projects = mutableListOf<Project>()

    private val file: File get() = File(ctx.filesDir, "tubehq.json")

    init {
        load()
    }

    fun load() {
        projects.clear()
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("projects") ?: return
            for (i in 0 until arr.length()) projects.add(Project.from(arr.getJSONObject(i)))
        } catch (e: Exception) {
        }
    }

    fun save() {
        try {
            val arr = JSONArray()
            projects.forEach { arr.put(it.toJson()) }
            file.writeText(JSONObject().put("v", 1).put("projects", arr).toString())
        } catch (e: Exception) {
        }
    }

    fun dump(): String {
        val arr = JSONArray()
        projects.forEach { arr.put(it.toJson()) }
        return JSONObject().put("v", 1).put("projects", arr).toString(2)
    }

    fun restore(text: String): Int {
        return try {
            val arr = JSONObject(text).optJSONArray("projects") ?: return 0
            projects.clear()
            for (i in 0 until arr.length()) projects.add(Project.from(arr.getJSONObject(i)))
            save()
            projects.size
        } catch (e: Exception) {
            -1
        }
    }

    fun add(p: Project) {
        projects.add(0, p)
        save()
    }

    fun remove(p: Project) {
        projects.remove(p)
        save()
    }

    fun sorted(): List<Project> =
        projects.sortedWith(compareBy({ Project.STATUS_ORDER.indexOf(it.status) }, { -it.created }))
}
