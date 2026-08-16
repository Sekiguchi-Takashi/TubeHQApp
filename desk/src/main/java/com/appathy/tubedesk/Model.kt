package com.appathy.tubedesk

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Scene(
    var id: String = "s" + System.nanoTime().toString().takeLast(6),
    var head: String = "",
    var body: String = "",
    var note: String = "",
    var realStart: Int = -1,
    var realDur: Int = -1
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("head", head).put("body", body).put("note", note)
        .put("realStart", realStart).put("realDur", realDur)

    companion object {
        fun from(o: JSONObject) = Scene(
            o.optString("id", "s" + System.nanoTime().toString().takeLast(6)),
            o.optString("head"), o.optString("body"), o.optString("note"),
            o.optInt("realStart", -1), o.optInt("realDur", -1)
        )
    }
}

/** 静止画1枚。旧TubeShotのShotをProject配下に取り込んだもの */
class ImageSpec(
    var role: String = ROLE_THUMB,
    var style: String = THUMB,
    var adopted: Int = 0,
    var bg: String = "",
    var title: String = "",
    var sub: String = "",
    var channel: String = "",
    var meta: String = "",
    var duration: String = "",
    var music: String = "",
    var likes: String = "",
    var comments: String = "",
    var progress: Int = 34,
    var bright: Int = 0,
    var contrast: Int = 0,
    var sat: Int = 0,
    var fade: Int = 0,
    var blur: Int = 0,
    var accent: Int = Color.parseColor("#FF0033"),
    var showPlay: Int = 1,
    var origin: String = "manual"
) {
    fun toJson(): JSONObject = JSONObject()
        .put("role", role).put("style", style).put("adopted", adopted).put("bg", bg)
        .put("title", title).put("sub", sub).put("channel", channel)
        .put("meta", meta).put("duration", duration).put("music", music)
        .put("likes", likes).put("comments", comments).put("progress", progress)
        .put("bright", bright).put("contrast", contrast).put("sat", sat)
        .put("fade", fade).put("blur", blur).put("accent", accent)
        .put("showPlay", showPlay).put("origin", origin)

    companion object {
        const val ROLE_THUMB = "thumbnail"
        const val ROLE_PROMO = "promo"

        const val SHORTS = "shorts"
        const val PLAYER = "player"
        const val THUMB = "thumb"
        const val YOKOKU = "yokoku"

        val STYLES = listOf(THUMB, YOKOKU, SHORTS, PLAYER)

        fun styleLabel(s: String) = when (s) {
            SHORTS -> "ショート風"
            PLAYER -> "プレイヤー風"
            THUMB -> "サムネ風"
            else -> "予告風"
        }

        fun size(s: String): Pair<Int, Int> = when (s) {
            SHORTS -> 1080 to 1920
            PLAYER -> 1920 to 1080
            else -> 1280 to 720
        }

        fun defaultRole(style: String) =
            if (style == SHORTS || style == PLAYER) ROLE_PROMO else ROLE_THUMB

        fun from(o: JSONObject): ImageSpec {
            val s = ImageSpec()
            s.role = o.optString("role", ROLE_THUMB)
            s.style = o.optString("style", THUMB)
            s.adopted = o.optInt("adopted", 0)
            s.bg = o.optString("bg")
            s.title = o.optString("title")
            s.sub = o.optString("sub")
            s.channel = o.optString("channel")
            s.meta = o.optString("meta")
            s.duration = o.optString("duration")
            s.music = o.optString("music")
            s.likes = o.optString("likes")
            s.comments = o.optString("comments")
            s.progress = o.optInt("progress", 34)
            s.bright = o.optInt("bright", 0)
            s.contrast = o.optInt("contrast", 0)
            s.sat = o.optInt("sat", 0)
            s.fade = o.optInt("fade", 0)
            s.blur = o.optInt("blur", 0)
            s.accent = o.optInt("accent", Color.parseColor("#FF0033"))
            s.showPlay = o.optInt("showPlay", 1)
            s.origin = o.optString("origin", "manual")
            return s
        }
    }
}

class Project(
    var id: String = System.currentTimeMillis().toString(),
    var title: String = "",
    var type: String = T_TALK,
    var status: String = S_IDEA,
    var memo: String = "",
    var scenes: MutableList<Scene> = mutableListOf(),
    var images: MutableList<ImageSpec> = mutableListOf(ImageSpec(), ImageSpec()),
    var metaTitle: String = "",
    var metaDesc: String = "",
    var metaTags: String = "",
    var pubUrl: String = "",
    var pubAt: String = "",
    var records: MutableList<String> = mutableListOf(),
    var realTotal: Int = -1,
    var outputUri: String = "",
    var created: Long = System.currentTimeMillis()
) {
    fun chars(): Int = scenes.sumOf { it.body.length }

    fun seconds(): Int = Math.max(1, Math.round(chars() / Speed.cps).toInt())

    fun sceneSeconds(s: Scene): Int =
        if (s.realDur > 0) s.realDur
        else Math.max(3, Math.round(s.body.length / Speed.cps).toInt())

    fun hasReal(): Boolean = realTotal > 0

    fun thumbs(): List<ImageSpec> = images.filter { it.role == ImageSpec.ROLE_THUMB }

    fun adoptedThumb(): ImageSpec? =
        thumbs().firstOrNull { it.adopted == 1 } ?: thumbs().firstOrNull()

    fun toJson(): JSONObject {
        val sa = JSONArray()
        scenes.forEach { sa.put(it.toJson()) }
        val ia = JSONArray()
        images.forEach { ia.put(it.toJson()) }
        val ra = JSONArray()
        records.forEach { ra.put(it) }
        return JSONObject()
            .put("id", id).put("title", title).put("type", type)
            .put("status", status).put("memo", memo)
            .put("scenes", sa).put("images", ia).put("records", ra)
            .put("metaTitle", metaTitle).put("metaDesc", metaDesc)
            .put("metaTags", metaTags).put("pubUrl", pubUrl).put("pubAt", pubAt)
            .put("realTotal", realTotal).put("outputUri", outputUri)
            .put("created", created)
    }

    companion object {
        const val T_TALK = "talk"
        const val T_SLIDE = "slide"
        const val T_SCREEN = "screen"

        const val S_IDEA = "idea"
        const val S_SCRIPT = "script"
        const val S_SHOOT = "shoot"
        const val S_EDIT = "edit"
        const val S_PUBLISH = "publish"
        const val S_DONE = "done"

        /** 既定値。実測で上書きできる（Speed参照） */
        const val CPS_DEFAULT = 5.3f

        val STATUS_ORDER = listOf(S_IDEA, S_SCRIPT, S_SHOOT, S_EDIT, S_PUBLISH, S_DONE)

        fun statusLabel(s: String) = when (s) {
            S_IDEA -> "ネタ"
            S_SCRIPT -> "台本済"
            S_SHOOT -> "撮影待ち"
            S_EDIT -> "素材あり"
            S_PUBLISH -> "投稿待ち"
            else -> "完了"
        }

        fun typeLabel(t: String) = when (t) {
            T_TALK -> "一人喋り"
            T_SLIDE -> "写真スライド"
            else -> "画面録画"
        }

        fun nextAction(p: Project) = when (p.status) {
            S_IDEA -> "台本を書く"
            S_SCRIPT -> "カンペを見ながら撮影"
            S_SHOOT -> "素材をCutに送る"
            S_EDIT -> "Cutの結果を取り込む"
            S_PUBLISH -> "サムネとメタを仕上げて投稿"
            else -> "実績を記録する"
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
            p.pubUrl = o.optString("pubUrl")
            p.pubAt = o.optString("pubAt")
            p.realTotal = o.optInt("realTotal", -1)
            p.outputUri = o.optString("outputUri")
            p.created = o.optLong("created", System.currentTimeMillis())

            val sa = o.optJSONArray("scenes")
            p.scenes = mutableListOf()
            if (sa != null) for (i in 0 until sa.length()) p.scenes.add(Scene.from(sa.getJSONObject(i)))

            val ia = o.optJSONArray("images") ?: o.optJSONArray("thumbs")
            p.images = mutableListOf()
            if (ia != null) for (i in 0 until ia.length()) p.images.add(ImageSpec.from(ia.getJSONObject(i)))
            while (p.thumbs().size < 2) p.images.add(ImageSpec())
            return p
        }
    }
}

/**
 * 話速。文字数から尺を推定する係数（文字/秒）。
 * 既定 5.3 は日本語の一人喋りの一般値だが、人によって 4.0〜7.0 と幅がある。
 * カンペで実際に読んで測り直せる。想定尺とチャプターの精度が全部これに乗る。
 */
object Speed {

    private const val PREF = "speed"
    private const val KEY = "cps"

    var cps: Float = Project.CPS_DEFAULT
        private set

    var measured: Boolean = false
        private set

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val v = p.getFloat(KEY, -1f)
        if (v > 0f) {
            cps = v
            measured = true
        } else {
            cps = Project.CPS_DEFAULT
            measured = false
        }
    }

    fun save(ctx: Context, v: Float) {
        val clamped = v.coerceIn(2.5f, 12f)
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putFloat(KEY, clamped).apply()
        cps = clamped
        measured = true
    }

    fun reset(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        cps = Project.CPS_DEFAULT
        measured = false
    }

    fun label(): String =
        String.format("%.1f 文字/秒", cps) + (if (measured) "（実測）" else "（既定）")
}

/** チャンネル名。画像とメタで毎回打ち直す手間を省く */
object Channel {

    private const val PREF = "channel"
    private const val KEY = "name"

    private var cached: String = ""

    fun load(ctx: Context) {
        cached = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
    }

    fun name(ctx: Context): String {
        if (cached.isBlank()) load(ctx)
        return cached
    }

    fun save(ctx: Context, v: String) {
        val t = v.trim()
        val fixed = if (t.isBlank() || t.startsWith("@")) t else "@" + t
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, fixed).apply()
        cached = fixed
    }
}

object Templates {
    fun of(type: String): List<Scene> = when (type) {
        Project.T_TALK -> listOf(
            Scene(head = "掴み（15秒）", note = "結論を先に匂わせる。質問で始める"),
            Scene(head = "結論"),
            Scene(head = "理由①"),
            Scene(head = "理由②"),
            Scene(head = "理由③"),
            Scene(head = "実例・体験談"),
            Scene(head = "まとめ"),
            Scene(head = "CTA", note = "チャンネル登録・次の動画へ誘導")
        )
        Project.T_SLIDE -> listOf(
            Scene(head = "タイトルカット", note = "使う写真のメモ"),
            Scene(head = "シーン1"),
            Scene(head = "シーン2"),
            Scene(head = "シーン3"),
            Scene(head = "シーン4"),
            Scene(head = "エンディング")
        )
        else -> listOf(
            Scene(head = "導入・完成形の提示", note = "画面：完成画面"),
            Scene(head = "手順1", note = "画面：操作メモ"),
            Scene(head = "手順2", note = "画面：操作メモ"),
            Scene(head = "手順3", note = "画面：操作メモ"),
            Scene(head = "つまずきポイント"),
            Scene(head = "まとめ")
        )
    }
}

class Store(val ctx: Context) {

    val projects = mutableListOf<Project>()

    private val file: File get() = File(ctx.filesDir, "tubedesk.json")
    private val legacy: File get() = File(ctx.filesDir, "tubehq.json")

    init {
        load()
    }

    fun load() {
        projects.clear()
        val src = if (file.exists()) file else legacy
        if (!src.exists()) return
        try {
            val arr = JSONObject(src.readText()).optJSONArray("projects") ?: return
            for (i in 0 until arr.length()) projects.add(Project.from(arr.getJSONObject(i)))
            if (src === legacy) save()
        } catch (e: Exception) {
        }
    }

    fun save() {
        try {
            val arr = JSONArray()
            projects.forEach { arr.put(it.toJson()) }
            file.writeText(JSONObject().put("v", 2).put("projects", arr).toString())
        } catch (e: Exception) {
        }
    }

    fun dump(): String {
        val arr = JSONArray()
        projects.forEach { arr.put(it.toJson()) }
        return JSONObject().put("v", 2).put("projects", arr).toString(2)
    }

    fun restore(text: String): Int = try {
        val arr = JSONObject(text).optJSONArray("projects") ?: JSONArray()
        projects.clear()
        for (i in 0 until arr.length()) projects.add(Project.from(arr.getJSONObject(i)))
        save()
        projects.size
    } catch (e: Exception) {
        -1
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
