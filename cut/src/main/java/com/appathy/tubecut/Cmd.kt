package com.appathy.tubecut

/**
 * 重いレーン。EditPlan から ffmpeg のシェルスクリプトを組む。
 * 実行は Termux 側。アプリは ffmpeg を同梱しない。
 *
 * 進捗を拾えるように、各 ffmpeg に `-progress cut_progress.txt` を付け、
 * 段階の切り替わりで `cut_step.txt` に `現在/全体` を書き出す。
 * アプリ側（Runner）はこの2ファイルをポーリングする。
 */
object Cmd {

    const val F_PROGRESS = "cut_progress.txt"
    const val F_STEP = "cut_step.txt"
    const val F_DONE = "cut_done.txt"
    const val F_LOG = "cut_log.txt"

    /** 段階数。Runner の割合計算と一致させること */
    fun steps(p: EditProject): Int {
        var n = p.used().size + 1
        if (p.telops.isNotEmpty()) n++
        if (p.vertical == 1) n++
        if (p.bgmUri.isNotBlank() || p.loudnorm == 1) n++
        return n
    }

    /**
     * 素材のファイル名は Termux から見えるパスに置かれている前提。
     * SAF の URI は使えないため、共有フォルダ配下の相対名で書く。
     */
    fun script(p: EditProject, dir: String, outName: String): String {
        val sb = StringBuilder()
        val total = steps(p)
        var step = 0

        fun mark(): String {
            step++
            return "printf '$step/$total\\n' > $F_STEP\n"
        }

        val ff = "ffmpeg -y -hide_banner -loglevel error -progress $F_PROGRESS -nostats"

        sb.append("#!/data/data/com.termux/files/usr/bin/bash\n")
        sb.append("cd \"").append(dir).append("\" || exit 1\n")
        sb.append("set -e\n")
        sb.append("rm -f $F_DONE $F_STEP $F_PROGRESS $F_LOG\n")
        sb.append("exec 2> $F_LOG\n\n")

        val segs = p.used()
        if (segs.isEmpty()) return sb.append("# 区間がありません\n").toString()

        sb.append("rm -f part_*.mp4 concat.txt\n\n")

        for ((i, s) in segs.withIndex()) {
            val src = p.sources.getOrNull(s.srcIndex) ?: continue
            val part = String.format("part_%03d.mp4", i)
            sb.append(mark())
            sb.append(ff).append(" -ss ").append(sec(s.inMs))
                .append(" -to ").append(sec(s.outMs))
                .append(" -i \"").append(src.name).append("\"")
                .append(" -c:v libx264 -preset veryfast -crf 20 -c:a aac -b:a 128k")
                .append(" \"").append(part).append("\"\n")
        }
        sb.append("\n")

        sb.append(mark())
        sb.append("for f in part_*.mp4; do printf \"file '%s'\\n\" \"$f\" >> concat.txt; done\n")
        sb.append(ff).append(" -f concat -safe 0 -i concat.txt -c copy joined.mp4\n\n")

        var cur = "joined.mp4"

        if (p.telops.isNotEmpty()) {
            val filters = StringBuilder()
            val inputs = StringBuilder()
            var idx = 1
            var chain = "[0:v]"
            for ((n, t) in p.telops.withIndex()) {
                val png = "telop_%03d.png".format(n)
                inputs.append(" -i \"").append(png).append("\"")
                val abs = absStart(p, t)
                val next = "[v$idx]"
                filters.append(chain).append("[").append(idx).append(":v]overlay=0:0")
                    .append(":enable='between(t,").append(sec(abs)).append(",")
                    .append(sec(abs + t.durMs)).append(")'").append(next).append(";")
                chain = next
                idx++
            }
            val f = filters.toString().trimEnd(';')
            sb.append(mark())
            sb.append(ff).append(" -i ").append(cur).append(inputs)
                .append(" -filter_complex \"").append(f).append("\"")
                .append(" -map \"").append(chain).append("\" -map 0:a?")
                .append(" -c:v libx264 -preset veryfast -crf 20 -c:a copy telop.mp4\n\n")
            cur = "telop.mp4"
        }

        if (p.vertical == 1) {
            val src = p.sources.firstOrNull { it.probed == 1 }
            val w = src?.width ?: 1920
            val h = src?.height ?: 1080
            val cropW = Math.min(w, (h * 9 / 16))
            val x = when (p.verticalPos) {
                "left" -> "0"
                "right" -> "${w - cropW}"
                else -> "${(w - cropW) / 2}"
            }
            sb.append(mark())
            sb.append(ff).append(" -i ").append(cur)
                .append(" -vf \"crop=").append(cropW).append(":").append(h)
                .append(":").append(x).append(":0,scale=1080:1920\"")
                .append(" -c:v libx264 -preset veryfast -crf 20 -c:a copy vertical.mp4\n\n")
            cur = "vertical.mp4"
        }

        if (p.bgmUri.isNotBlank()) {
            val vol = p.bgmVolume / 100f
            sb.append(mark())
            sb.append(ff).append(" -i ").append(cur).append(" -i \"bgm.m4a\"")
                .append(" -filter_complex \"[1:a]volume=").append(String.format("%.2f", vol))
                .append(",afade=t=in:st=0:d=").append(p.bgmFadeSec)
                .append("[bg];[0:a][bg]amix=inputs=2:duration=first")
            if (p.loudnorm == 1) sb.append(",loudnorm=I=-16:TP=-1.5:LRA=11")
            sb.append("[a]\" -map 0:v -map \"[a]\" -c:v copy -c:a aac -b:a 192k mixed.mp4\n\n")
            cur = "mixed.mp4"
        } else if (p.loudnorm == 1) {
            sb.append(mark())
            sb.append(ff).append(" -i ").append(cur)
                .append(" -af loudnorm=I=-16:TP=-1.5:LRA=11")
                .append(" -c:v copy -c:a aac -b:a 192k normed.mp4\n\n")
            cur = "normed.mp4"
        }

        sb.append("mv -f ").append(cur).append(" \"").append(outName).append("\"\n")
        sb.append("rm -f part_*.mp4 concat.txt joined.mp4 telop.mp4 vertical.mp4 normed.mp4 mixed.mp4\n")
        sb.append("printf 'done\\n' > $F_DONE\n")
        return sb.toString()
    }

    private fun absStart(p: EditProject, t: Telop): Long {
        val segs = p.used()
        var acc = 0L
        for ((i, s) in segs.withIndex()) {
            if (i == t.segIndex) return acc + t.startMs
            acc += s.durMs()
        }
        return t.startMs
    }

    private fun sec(ms: Long): String = String.format("%.2f", ms / 1000f)

    fun runLine(dir: String, name: String): String = "bash $dir/$name"
}
