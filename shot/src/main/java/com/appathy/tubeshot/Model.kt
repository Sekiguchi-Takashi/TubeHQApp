package com.appathy.tubeshot

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * ShotProject: 1枚のYouTube風画像。
 * 元の動画オントロジーから時間軸と音声を落とし、LayerStackを静止画に畳んだもの。
 */
class Shot(
    var id: String = System.currentTimeMillis().toString(),
    var name: String = "",
    var style: String = SHORTS,
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
    var created: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("style", style).put("bg", bg)
        .put("title", title).put("sub", sub).put("channel", channel)
        .put("meta", meta).put("duration", duration).put("music", music)
        .put("likes", likes).put("comments", comments).put("progress", progress)
        .put("bright", bright).put("contrast", contrast).put("sat", sat)
        .put("fade", fade).put("blur", blur).put("accent", accent)
        .put("showPlay", showPlay).put("created", created)

    fun copyOf(): Shot {
        val s = Shot.from(toJson())
        s.id = System.currentTimeMillis().toString()
        s.name = name + " 複製"
        s.created = System.currentTimeMillis()
        return s
    }

    companion object {
        const val SHORTS = "shorts"
        const val PLAYER = "player"
        const val THUMB = "thumb"
        const val YOKOKU = "yokoku"

        val STYLES = listOf(SHORTS, PLAYER, THUMB, YOKOKU)

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

        fun from(o: JSONObject): Shot {
            val s = Shot()
            s.id = o.optString("id", System.currentTimeMillis().toString())
            s.name = o.optString("name")
            s.style = o.optString("style", SHORTS)
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
            s.created = o.optLong("created", System.currentTimeMillis())
            return s
        }
    }
}

/** F601 スタイル推論の静止画版。文字の型と色味だけを決める */
class Preset(
    val key: String,
    val label: String,
    val accent: String,
    val title: String,
    val sub: String,
    val channel: String,
    val meta: String,
    val duration: String,
    val music: String,
    val likes: String,
    val comments: String,
    val fade: Int,
    val sat: Int
)

object Presets {
    val ALL = listOf(
        Preset(
            "vlog", "Vlog", "#FF0033",
            "休日の過ごし方", "何もしない一日をそのまま撮りました",
            "@daily_room", "1.2万回視聴・3日前", "8:42", "静かな午後 - Lo-fi",
            "1.4万", "231", 10, 5
        ),
        Preset(
            "game", "ゲーム", "#7B2FF2",
            "最終ステージ突破", "3時間かけた結果がこれ",
            "@stage_clear", "8.3万回視聴・1日前", "24:07", "Boss Theme",
            "6.2万", "1892", 0, 20
        ),
        Preset(
            "howto", "解説", "#0A84FF",
            "5分で分かる仕組み", "図解でいちばん短い説明",
            "@kaisetsu_note", "3.7万回視聴・1週間前", "5:12", "",
            "2.1万", "486", 0, -10
        ),
        Preset(
            "cook", "料理", "#FF7A00",
            "包丁いらずの一品", "材料は3つだけ",
            "@daidokoro", "2.5万回視聴・4日前", "6:38", "Kitchen Swing",
            "1.9万", "312", 8, 15
        ),
        Preset(
            "travel", "旅行", "#00B894",
            "始発で行く離島", "帰りの便は考えていない",
            "@ryoko_log", "5.1万回視聴・2週間前", "14:20", "Sea Breeze",
            "3.8万", "704", 18, 12
        ),
        Preset(
            "news", "ニュース", "#C1121F",
            "速報", "現地から中継でお伝えします",
            "@news_line", "12万回視聴・2時間前", "3:04", "",
            "8420", "1204", 0, -20
        ),
        Preset(
            "comedy", "コメディ", "#FFC300",
            "そうはならんやろ", "なっとるやろがい",
            "@warai_ch", "43万回視聴・5日前", "0:58", "Funny Walk",
            "21万", "5301", 0, 25
        )
    )

    fun apply(shot: Shot, p: Preset) {
        shot.accent = Color.parseColor(p.accent)
        shot.title = p.title
        shot.sub = p.sub
        shot.channel = p.channel
        shot.meta = p.meta
        shot.duration = p.duration
        shot.music = p.music
        shot.likes = p.likes
        shot.comments = p.comments
        shot.fade = p.fade
        shot.sat = p.sat
    }
}

/** F612 の静止画版。タイトルからハッシュタグ候補を作る */
object Suggest {
    fun titles(base: String): List<String> {
        val t = base.ifBlank { "テーマ" }
        return listOf(
            t,
            "【衝撃】$t",
            "${t}、やってみた",
            "誰も言わない${t}の話",
            "$t｜3分でわかる",
            "もう$t で迷わない"
        )
    }

    fun tags(shot: Shot): String {
        val words = (shot.title + " " + shot.sub)
            .split(" ", "　", "、", "。", "・", "｜", "|", "【", "】")
            .map { it.trim() }.filter { it.length in 2..12 }.distinct().take(5)
        return words.joinToString(" ") { "#$it" }
    }
}

class Store(val ctx: Context) {

    val shots = mutableListOf<Shot>()

    private val file: File get() = File(ctx.filesDir, "tubeshot.json")

    init {
        load()
    }

    fun load() {
        shots.clear()
        if (!file.exists()) return
        try {
            val arr = JSONObject(file.readText()).optJSONArray("shots") ?: return
            for (i in 0 until arr.length()) shots.add(Shot.from(arr.getJSONObject(i)))
        } catch (e: Exception) {
        }
    }

    fun save() {
        try {
            val arr = JSONArray()
            shots.forEach { arr.put(it.toJson()) }
            file.writeText(JSONObject().put("v", 1).put("shots", arr).toString())
        } catch (e: Exception) {
        }
    }

    fun dump(): String {
        val arr = JSONArray()
        shots.forEach { arr.put(it.toJson()) }
        return JSONObject().put("v", 1).put("shots", arr).toString(2)
    }

    fun restore(text: String): Int = try {
        val arr = JSONObject(text).optJSONArray("shots") ?: JSONArray()
        shots.clear()
        for (i in 0 until arr.length()) shots.add(Shot.from(arr.getJSONObject(i)))
        save()
        shots.size
    } catch (e: Exception) {
        -1
    }

    fun add(s: Shot) {
        shots.add(0, s)
        save()
    }

    fun remove(s: Shot) {
        shots.remove(s)
        save()
    }

    fun sorted(): List<Shot> = shots.sortedByDescending { it.created }
}
