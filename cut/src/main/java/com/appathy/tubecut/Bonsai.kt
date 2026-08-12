package com.appathy.tubecut

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

    /** AI-05 テロップ文言。16字以内 */
    fun telop(ctx: Context, label: String, body: String): String? {
        val user = buildString {
            append("次の内容を画面テロップの短い一文にする。16字以内。\n")
            if (label.isNotBlank()) append("見出し: ").append(label).append("\n")
            append("本文: ").append(body.take(80)).append("\n")
            append("形式: {\"t\":\"…\"}")
        }
        val o = ask(ctx, user, 40) ?: return null
        val t = o.optString("t").trim()
        return if (t.isBlank() || t.length > 24) null else t
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
