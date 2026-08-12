package com.appathy.tubedesk

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * BonsaiApp のローカルLLM。AI_RULES.md v1 に従う。
 *
 * R1 決定的生成が先、推論は後
 * R2 出力は極小（60トークン目標）
 * R3 JSON固定。壊れていたら1回だけ再試行して、それでも駄目なら捨てる
 * R4 自動適用しない
 * R5 生成物には出所を残す
 *
 * 未起動・未インストール・応答なしのいずれでも例外を投げず null を返す。
 * 呼び出し側はエラーを出さずに決定的版へ落ちること。
 */
object Bonsai {

    private const val PREF = "bonsai"
    private const val KEY_HOST = "host"
    private const val DEFAULT_HOST = "http://127.0.0.1:8080"

    private const val SYSTEM =
        "あなたは短い日本語の候補を作る。出力はJSONのみ。前置き・説明・コードフェンスは書かない。"

    fun host(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_HOST, DEFAULT_HOST)
            ?: DEFAULT_HOST

    fun setHost(ctx: Context, v: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_HOST, v.trim().trimEnd('/')).apply()
    }

    /** AI-01 タイトル候補。各28字以内 */
    fun titles(ctx: Context, title: String, type: String, lead: String): List<String>? {
        val user = buildString {
            append("YouTube動画のタイトル候補を3つ作る。各28字以内。\n")
            append("題材: ").append(title).append("\n")
            append("形式の型: ").append(type).append("\n")
            if (lead.isNotBlank()) append("台本冒頭: ").append(lead.take(80)).append("\n")
            append("形式: {\"c\":[\"…\",\"…\",\"…\"]}")
        }
        val o = ask(ctx, user, 60) ?: return null
        val arr = o.optJSONArray("c") ?: return null
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val v = arr.optString(i).trim()
            if (v.isNotBlank() && v.length <= 40) out.add(v)
        }
        return if (out.isEmpty()) null else out
    }

    /** AI-02 説明文の冒頭 */
    fun lead(ctx: Context, title: String, body: String): String? {
        val user = buildString {
            append("動画の説明文の冒頭を2行で書く。全体で70字以内。\n")
            append("タイトル: ").append(title).append("\n")
            append("台本冒頭: ").append(body.take(120)).append("\n")
            append("形式: {\"lead\":\"…\"}")
        }
        val o = ask(ctx, user, 60) ?: return null
        val t = o.optString("lead").trim()
        return if (t.isBlank()) null else t
    }

    /** AI-03 タグ */
    fun tags(ctx: Context, title: String, sub: String, type: String): List<String>? {
        val user = buildString {
            append("YouTube動画のタグを5つ作る。各12字以内。\n")
            append("タイトル: ").append(title).append("\n")
            if (sub.isNotBlank()) append("副題: ").append(sub.take(40)).append("\n")
            append("形式の型: ").append(type).append("\n")
            append("形式: {\"t\":[\"…\",\"…\",\"…\",\"…\",\"…\"]}")
        }
        val o = ask(ctx, user, 45) ?: return null
        val arr = o.optJSONArray("t") ?: return null
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val v = arr.optString(i).trim()
            if (v.isNotBlank() && v.length <= 16) out.add(v)
        }
        return if (out.isEmpty()) null else out
    }

    /** AI-04 サムネの文言 */
    fun thumbText(ctx: Context, title: String, body: String, style: String): Pair<String, String>? {
        val limit = if (style == "yokoku") "主題は8〜12字" else "主題は12字以内"
        val user = buildString {
            append("サムネイルの文言を作る。").append(limit).append("、副題は28字以内。\n")
            append("題材: ").append(title).append("\n")
            if (body.isNotBlank()) append("台本冒頭: ").append(body.take(80)).append("\n")
            append("形式: {\"main\":\"…\",\"sub\":\"…\"}")
        }
        val o = ask(ctx, user, 45) ?: return null
        val m = o.optString("main").trim()
        val s = o.optString("sub").trim()
        return if (m.isBlank()) null else Pair(m, s)
    }

    /** AI-06 シーン見出し。10字以内 */
    fun head(ctx: Context, body: String): String? {
        val user = buildString {
            append("次の内容に10字以内の見出しを付ける。\n")
            append(body.take(60)).append("\n")
            append("形式: {\"h\":\"…\"}")
        }
        val o = ask(ctx, user, 25) ?: return null
        val t = o.optString("h").trim()
        return if (t.isBlank() || t.length > 16) null else t
    }

    // ---------------- 共通 ----------------

    private fun ask(ctx: Context, user: String, maxTokens: Int): JSONObject? {
        val first = call(ctx, user, maxTokens)
        val p1 = parse(first)
        if (p1 != null) return p1
        val second = call(ctx, user, maxTokens)
        return parse(second)
    }

    private fun parse(raw: String?): JSONObject? {
        if (raw.isNullOrBlank()) return null
        var t = raw.trim()
        t = t.replace("```json", "").replace("```", "").trim()
        val s = t.indexOf('{')
        val e = t.lastIndexOf('}')
        if (s < 0 || e <= s) return null
        return try {
            JSONObject(t.substring(s, e + 1))
        } catch (ex: Exception) {
            null
        }
    }

    private fun call(ctx: Context, user: String, maxTokens: Int): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(host(ctx) + "/v1/chat/completions")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 45000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val msgs = JSONArray()
            msgs.put(JSONObject().put("role", "system").put("content", SYSTEM))
            msgs.put(JSONObject().put("role", "user").put("content", user))
            val payload = JSONObject()
                .put("messages", msgs)
                .put("temperature", 0.6)
                .put("max_tokens", (maxTokens * 1.5).toInt())
                .put("stream", false)
                .toString()

            val os: OutputStream = conn.outputStream
            os.write(payload.toByteArray())
            os.flush()
            os.close()

            if (conn.responseCode !in 200..299) return null
            val sb = StringBuilder()
            BufferedReader(InputStreamReader(conn.inputStream)).use { r ->
                var line = r.readLine()
                while (line != null) {
                    sb.append(line)
                    line = r.readLine()
                }
            }
            val o = JSONObject(sb.toString())
            o.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content")
        } catch (e: Throwable) {
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (e: Exception) {
            }
        }
    }
}
