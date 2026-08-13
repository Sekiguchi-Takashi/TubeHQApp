package com.appathy.tubecut

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Source(
    var uri: String = "",
    var name: String = "",
    var sharedName: String = "",
    var copied: Int = 0,
    var durationMs: Long = 0,
    var vCodec: String = "",
    var aCodec: String = "",
    var width: Int = 0,
    var height: Int = 0,
    var fps: Int = 0,
    var rotation: Int = 0,
    var probed: Int = 0
) {
    fun label(): String =
        if (probed == 0) "未解析"
        else "${Fmt.ms(durationMs)}  ${vCodec.substringAfter('/')}  ${width}x$height  ${fps}fps"

    fun key(): String = "$vCodec|$aCodec|$width|$height|$fps"

    /** Termux から見える名前。コピー時に決まる */
    fun outName(): String = sharedName.ifBlank { safeName() }

    /** 記号を落とした安全なファイル名。拡張子は残す */
    fun safeName(): String {
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ".mp4"
        val cleaned = stem.replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').take(24)
        return (if (cleaned.isBlank()) "src" else cleaned) + ext.lowercase()
    }

    fun toJson(): JSONObject = JSONObject()
        .put("uri", uri).put("name", name)
        .put("sharedName", sharedName).put("copied", copied)
        .put("durationMs", durationMs)
        .put("vCodec", vCodec).put("aCodec", aCodec)
        .put("width", width).put("height", height).put("fps", fps)
        .put("rotation", rotation).put("probed", probed)

    companion object {
        fun from(o: JSONObject) = Source(
            o.optString("uri"), o.optString("name"),
            o.optString("sharedName"), o.optInt("copied"),
            o.optLong("durationMs"),
            o.optString("vCodec"), o.optString("aCodec"),
            o.optInt("width"), o.optInt("height"), o.optInt("fps"),
            o.optInt("rotation"), o.optInt("probed")
        )
    }
}

class Segment(
    var srcIndex: Int = 0,
    var inMs: Long = 0,
    var outMs: Long = 0,
    var use: Int = 1,
    var silent: Int = 0,
    var label: String = "",
    var sceneId: String = "",
    var snapMs: Long = 0
) {
    fun durMs(): Long = Math.max(0L, outMs - inMs)

    fun toJson(): JSONObject = JSONObject()
        .put("srcIndex", srcIndex).put("inMs", inMs).put("outMs", outMs)
        .put("use", use).put("silent", silent).put("label", label)
        .put("sceneId", sceneId).put("snapMs", snapMs)

    companion object {
        fun from(o: JSONObject) = Segment(
            o.optInt("srcIndex"), o.optLong("inMs"), o.optLong("outMs"),
            o.optInt("use", 1), o.optInt("silent"), o.optString("label"),
            o.optString("sceneId"), o.optLong("snapMs")
        )
    }
}

class Telop(
    var segIndex: Int = 0,
    var text: String = "",
    var startMs: Long = 500,
    var durMs: Long = 3000,
    var pos: String = "bottom",
    var style: String = "bold"
) {
    fun toJson(): JSONObject = JSONObject()
        .put("segIndex", segIndex).put("text", text)
        .put("startMs", startMs).put("durMs", durMs)
        .put("pos", pos).put("style", style)

    companion object {
        fun from(o: JSONObject) = Telop(
            o.optInt("segIndex"), o.optString("text"),
            o.optLong("startMs", 500), o.optLong("durMs", 3000),
            o.optString("pos", "bottom"), o.optString("style", "bold")
        )
    }
}

class EditProject(
    var id: String = System.currentTimeMillis().toString(),
    var name: String = "",
    var workId: String = "",
    var sources: MutableList<Source> = mutableListOf(),
    var segments: MutableList<Segment> = mutableListOf(),
    var telops: MutableList<Telop> = mutableListOf(),
    var scenes: MutableList<Pair<String, String>> = mutableListOf(),
    var bgmUri: String = "",
    var bgmVolume: Int = 18,
    var bgmFadeSec: Int = 2,
    var loudnorm: Int = 1,
    var vertical: Int = 0,
    var verticalPos: String = "center",
    var verticalOffset: Int = 50,
    var thresholdDb: Int = -38,
    var minSilenceMs: Int = 600,
    var padHeadMs: Int = 150,
    var padTailMs: Int = 100,
    var accent: Int = 0xFFFF0033.toInt(),
    var analyzed: Int = 0,
    var outputUri: String = "",
    var created: Long = System.currentTimeMillis()
) {
    fun used(): List<Segment> = segments.filter { it.use == 1 }

    fun usedMs(): Long = used().sumOf { it.durMs() }

    fun totalMs(): Long = sources.sumOf { it.durationMs }

    /** 重いレーンが必要になる条件 */
    fun needsFfmpeg(): Boolean =
        telops.isNotEmpty() || bgmUri.isNotBlank() || vertical == 1 ||
            (loudnorm == 1 && bgmUri.isNotBlank()) || !uniform()

    fun uniform(): Boolean {
        val probed = sources.filter { it.probed == 1 }
        if (probed.isEmpty()) return false
        return probed.map { it.key() }.distinct().size == 1
    }

    /**
     * 音声トラックの有無が素材間で食い違うと、
     * 先頭素材の構成で作ったトラックに書けず無音区間や欠落が起きる。
     */
    fun audioMixed(): Boolean {
        val probed = sources.filter { it.probed == 1 }
        if (probed.size < 2) return false
        return probed.map { it.aCodec.isBlank() }.distinct().size > 1
    }

    /** 一致しない項目を名指しする */
    fun mismatch(): String {
        val probed = sources.filter { it.probed == 1 }
        if (probed.size < 2) return ""
        val base = probed[0]
        val sb = StringBuilder()
        for (s in probed.drop(1)) {
            if (s.vCodec != base.vCodec) sb.append("${s.name} の映像codecが ${s.vCodec.substringAfter('/')}（他は ${base.vCodec.substringAfter('/')}）\n")
            if (s.width != base.width || s.height != base.height) sb.append("${s.name} の解像度が ${s.width}x${s.height}（他は ${base.width}x${base.height}）\n")
            if (s.fps != base.fps) sb.append("${s.name} の fps が ${s.fps}（他は ${base.fps}）\n")
            if (s.aCodec != base.aCodec) sb.append("${s.name} の音声codecが ${s.aCodec.substringAfter('/')}（他は ${base.aCodec.substringAfter('/')}）\n")
        }
        return sb.toString().trim()
    }

    /**
     * 縦切り出しの枠。9:16 を入る最大で取り、横位置だけ動かす。
     * プレビューと ffmpeg コマンドで**必ず同じ計算を使う**こと。
     */
    fun cropRect(w: Int, h: Int): IntArray {
        val cw = Math.min(w, h * 9 / 16)
        val room = Math.max(0, w - cw)
        val x = (room * verticalOffset / 100).coerceIn(0, room)
        return intArrayOf(x, 0, cw, h)
    }

    /** 所要時間の見積り（秒） */
    fun estimateSec(): Int =
        if (needsFfmpeg()) Math.max(60, (usedMs() / 1000 * 1.6).toInt())
        else Math.max(3, (usedMs() / 1000 / 40).toInt() + 3)

    fun toJson(): JSONObject {
        val sa = JSONArray(); sources.forEach { sa.put(it.toJson()) }
        val ga = JSONArray(); segments.forEach { ga.put(it.toJson()) }
        val ta = JSONArray(); telops.forEach { ta.put(it.toJson()) }
        val ca = JSONArray()
        scenes.forEach { ca.put(JSONObject().put("id", it.first).put("head", it.second)) }
        return JSONObject()
            .put("id", id).put("name", name).put("workId", workId)
            .put("sources", sa).put("segments", ga).put("telops", ta).put("scenes", ca)
            .put("bgmUri", bgmUri).put("bgmVolume", bgmVolume).put("bgmFadeSec", bgmFadeSec)
            .put("loudnorm", loudnorm).put("vertical", vertical).put("verticalPos", verticalPos)
            .put("verticalOffset", verticalOffset)
            .put("thresholdDb", thresholdDb).put("minSilenceMs", minSilenceMs)
            .put("padHeadMs", padHeadMs).put("padTailMs", padTailMs)
            .put("accent", accent)
            .put("analyzed", analyzed).put("outputUri", outputUri).put("created", created)
    }

    companion object {
        fun from(o: JSONObject): EditProject {
            val p = EditProject()
            p.id = o.optString("id", System.currentTimeMillis().toString())
            p.name = o.optString("name")
            p.workId = o.optString("workId")
            p.bgmUri = o.optString("bgmUri")
            p.bgmVolume = o.optInt("bgmVolume", 18)
            p.bgmFadeSec = o.optInt("bgmFadeSec", 2)
            p.loudnorm = o.optInt("loudnorm", 1)
            p.vertical = o.optInt("vertical", 0)
            p.verticalPos = o.optString("verticalPos", "center")
            p.verticalOffset = o.optInt("verticalOffset", 50)
            p.thresholdDb = o.optInt("thresholdDb", -38)
            p.minSilenceMs = o.optInt("minSilenceMs", 600)
            p.padHeadMs = o.optInt("padHeadMs", 150)
            p.padTailMs = o.optInt("padTailMs", 100)
            p.accent = o.optInt("accent", 0xFFFF0033.toInt())
            p.analyzed = o.optInt("analyzed", 0)
            p.outputUri = o.optString("outputUri")
            p.created = o.optLong("created", System.currentTimeMillis())

            val sa = o.optJSONArray("sources")
            p.sources = mutableListOf()
            if (sa != null) for (i in 0 until sa.length()) p.sources.add(Source.from(sa.getJSONObject(i)))
            val ga = o.optJSONArray("segments")
            p.segments = mutableListOf()
            if (ga != null) for (i in 0 until ga.length()) p.segments.add(Segment.from(ga.getJSONObject(i)))
            val ta = o.optJSONArray("telops")
            p.telops = mutableListOf()
            if (ta != null) for (i in 0 until ta.length()) p.telops.add(Telop.from(ta.getJSONObject(i)))
            val ca = o.optJSONArray("scenes")
            p.scenes = mutableListOf()
            if (ca != null) for (i in 0 until ca.length()) {
                val g = ca.getJSONObject(i)
                p.scenes.add(Pair(g.optString("id"), g.optString("head")))
            }
            return p
        }
    }
}

object Fmt {
    fun ms(v: Long): String {
        val t = v / 1000
        return String.format("%d:%02d", t / 60, t % 60)
    }

    fun msDot(v: Long): String {
        val t = v / 1000
        val d = (v % 1000) / 100
        return String.format("%d:%02d.%d", t / 60, t % 60, d)
    }

    fun sec(v: Long): String = String.format("%.1f秒", v / 1000f)
}

class Store(val ctx: Context) {

    val projects = mutableListOf<EditProject>()

    private val file: File get() = File(ctx.filesDir, "tubecut.json")

    init {
        load()
    }

    fun load() {
        projects.clear()
        if (!file.exists()) return
        try {
            val arr = JSONObject(file.readText()).optJSONArray("projects") ?: return
            for (i in 0 until arr.length()) projects.add(EditProject.from(arr.getJSONObject(i)))
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

    fun add(p: EditProject) {
        projects.add(0, p)
        save()
    }

    fun remove(p: EditProject) {
        projects.remove(p)
        save()
    }

    fun sorted(): List<EditProject> = projects.sortedByDescending { it.created }
}
