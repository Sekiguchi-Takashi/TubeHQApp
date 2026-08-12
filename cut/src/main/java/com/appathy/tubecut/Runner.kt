package com.appathy.tubecut

import android.content.Context
import android.net.Uri

/**
 * 重いレーンの見守り役。
 * Termux で走っている ffmpeg が書き出す3ファイルを2秒おきに読む。
 *
 *   cut_step.txt      「3/7」 段階
 *   cut_progress.txt  ffmpeg -progress の key=value。out_time_ms と progress=end を見る
 *   cut_done.txt      全段階の完了印
 *
 * ffmpeg のプロセスを直接見に行かない。ファイルだけで完結させる。
 * RUN_COMMAND が使えない環境でも同じように動かすための判断。
 */
class Runner(val ctx: Context, val proj: EditProject) {

    var running = false
    var stepNow = 0
    var stepTotal = Cmd.steps(proj)
    var stageMs = 0L
    var lastChange = 0L
    var done = false
    var error = ""

    private var lastSignature = ""

    /** 0〜100。段階数と段階内の進みを合成する */
    fun percent(): Int {
        if (done) return 100
        if (stepTotal <= 0) return 0
        val perStep = 100f / stepTotal
        val base = (stepNow - 1).coerceAtLeast(0) * perStep
        val inner = if (stepNow in 1..proj.used().size) {
            val seg = proj.used().getOrNull(stepNow - 1)
            val d = seg?.durMs() ?: 0L
            if (d > 0) (stageMs.toFloat() / d).coerceIn(0f, 1f) else 0f
        } else {
            val d = proj.usedMs()
            if (d > 0) (stageMs.toFloat() / d).coerceIn(0f, 1f) else 0f
        }
        return (base + inner * perStep).toInt().coerceIn(0, 99)
    }

    fun label(): String = when {
        done -> "完了"
        error.isNotBlank() -> error
        stepNow == 0 -> "開始を待っています（Termuxで実行してください）"
        else -> "$stepNow / $stepTotal 段階  ${percent()}%"
    }

    /** 一度だけ読む。呼び出し側が2秒おきに叩く */
    fun poll(): Boolean {
        val tree = Bridge.treeUri(ctx) ?: return false
        val children = Bridge.listChildren(ctx, tree)

        fun find(name: String): Uri? = children.firstOrNull { it.second == name }?.first

        val doneUri = find(Cmd.F_DONE)
        if (doneUri != null && Bridge.readText(ctx, doneUri).isNotBlank()) {
            done = true
            running = false
            return true
        }

        find(Cmd.F_STEP)?.let { u ->
            val t = Bridge.readText(ctx, u).trim()
            val parts = t.split("/")
            if (parts.size == 2) {
                stepNow = parts[0].toIntOrNull() ?: stepNow
                stepTotal = parts[1].toIntOrNull() ?: stepTotal
            }
        }

        find(Cmd.F_PROGRESS)?.let { u ->
            val t = Bridge.readText(ctx, u)
            var outMs = -1L
            for (line in t.lineSequence()) {
                val i = line.indexOf('=')
                if (i <= 0) continue
                val k = line.substring(0, i).trim()
                val v = line.substring(i + 1).trim()
                if (k == "out_time_ms") outMs = (v.toLongOrNull() ?: 0L) / 1000
                if (k == "out_time_us") outMs = (v.toLongOrNull() ?: 0L) / 1000
            }
            if (outMs >= 0) stageMs = outMs
        }

        val sig = "$stepNow/$stepTotal/$stageMs"
        val now = System.currentTimeMillis()
        if (sig != lastSignature) {
            lastSignature = sig
            lastChange = now
        } else if (stepNow > 0 && lastChange > 0 && now - lastChange > 90_000) {
            error = "90秒動きがありません。Termuxのログを確認してください"
        }

        return true
    }

    fun tail(): String {
        val tree = Bridge.treeUri(ctx) ?: return ""
        val u = Bridge.findChild(ctx, tree, Cmd.F_LOG) ?: return ""
        val t = Bridge.readText(ctx, u).trim()
        if (t.isBlank()) return "エラー出力はありません"
        return t.lines().takeLast(12).joinToString("\n")
    }
}
