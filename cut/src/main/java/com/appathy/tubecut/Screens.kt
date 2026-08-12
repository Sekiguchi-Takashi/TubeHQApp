package com.appathy.tubecut

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

object Screens {

    fun watch(e: EditText, cb: (String) -> Unit) {
        e.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = cb(s?.toString() ?: "")
        })
    }


    /** AI_RULES.md R1/R4。決定的版が既にある前提で、押した人だけが待つ */
    fun <T> aiRun(
        act: MainActivity, btn: android.widget.Button, label: String,
        work: () -> T?, done: (T?) -> Unit
    ) {
        if (btn.tag == "busy") {
            btn.tag = "cancel"
            btn.text = label
            return
        }
        btn.tag = "busy"
        val started = System.currentTimeMillis()
        val tick = object : Runnable {
            override fun run() {
                if (btn.tag != "busy") return
                btn.text = "生成中… ${(System.currentTimeMillis() - started) / 1000}秒"
                act.ui.postDelayed(this, 1000)
            }
        }
        act.ui.post(tick)
        Thread {
            val r = try {
                work()
            } catch (e: Throwable) {
                null
            }
            act.ui.post {
                val cancelled = btn.tag == "cancel"
                btn.tag = ""
                btn.text = label
                if (!cancelled) done(r)
            }
        }.start()
    }

    private fun empty(act: MainActivity, msg: String): View {
        val c = Ui.col(act, 18)
        c.addView(Ui.title(act, "編集が選ばれていません"))
        c.addView(Ui.label(act, msg, true))
        c.addView(Ui.button(act, "素材タブへ", true) { act.show(0) })
        return c
    }

    // ==================== 解析 ====================

    fun analyze(act: MainActivity): View {
        val p = act.editing ?: return empty(act, "素材タブで編集を開いてください。")
        val c = Ui.col(act, 14)

        if (p.sources.isEmpty()) {
            c.addView(Ui.label(act, "先に素材を追加してください。", true))
            return Ui.scroll(act, c)
        }

        c.addView(Ui.title(act, "無音検出"))
        c.addView(Ui.label(act, "対象: 全${p.sources.size}ファイル（順に解析します）", true))

        val wave = WaveView(act)
        wave.thresholdDb = p.thresholdDb
        val wlp = LinearLayout.LayoutParams(-1, Ui.dp(act, 90))
        wlp.topMargin = Ui.dp(act, 8)
        wlp.bottomMargin = Ui.dp(act, 8)
        wave.layoutParams = wlp

        val summary = TextView(act)
        summary.setTextColor(Ui.ACC)
        summary.textSize = 15f
        summary.typeface = Typeface.DEFAULT_BOLD

        fun recompute() {
            if (act.rmsBySrc.isEmpty()) {
                summary.text = "未解析"
                return
            }
            val prev = p.segments.toList()
            val segs = mutableListOf<Segment>()
            for ((si, src) in p.sources.withIndex()) {
                val r = act.rmsBySrc[src.uri] ?: continue
                segs.addAll(
                    Silence.segments(
                        r, si, p.thresholdDb, p.minSilenceMs, p.padHeadMs, p.padTailMs
                    )
                )
            }
            // 前回の採用状態とラベルを引き継ぐ
            for (s in segs) {
                val old = prev.firstOrNull { it.srcIndex == s.srcIndex && it.inMs == s.inMs }
                if (old != null) {
                    s.label = old.label
                    s.sceneId = old.sceneId
                    s.use = old.use
                }
            }
            p.segments = segs
            p.analyzed = 1
            act.store.save()

            val silent = segs.count { it.silent == 1 }
            val analyzedSrc = p.sources.count { act.rmsBySrc.containsKey(it.uri) }
            summary.text = "${segs.size}区間 / うち無音 ${silent}区間 / ${analyzedSrc}ファイル\n" +
                "採用後の想定尺 ${Fmt.ms(p.usedMs())}（元 ${Fmt.ms(p.totalMs())}）"

            // 波形は全素材を連結して表示する
            val joined = ArrayList<Float>()
            for (src in p.sources) act.rmsBySrc[src.uri]?.let { joined.addAll(it.toList()) }
            wave.rms = FloatArray(joined.size) { joined[it] }
            wave.thresholdDb = p.thresholdDb
            val total = Math.max(1L, p.totalMs())
            var base = 0L
            val marks = mutableListOf<Float>()
            for ((si, src) in p.sources.withIndex()) {
                for (s in segs) if (s.srcIndex == si && s.use == 1) {
                    marks.add((base + s.inMs).toFloat() / total)
                }
                base += src.durationMs
            }
            wave.marks = marks
            wave.invalidate()
        }

        c.addView(Ui.stepper(act, "閾値 dB", p.thresholdDb, -60, -10, 2) {
            p.thresholdDb = it; act.store.save(); recompute()
        })
        c.addView(Ui.stepper(act, "最短無音長 ms", p.minSilenceMs, 200, 2000, 100) {
            p.minSilenceMs = it; act.store.save(); recompute()
        })
        c.addView(Ui.stepper(act, "前マージン ms", p.padHeadMs, 0, 500, 50) {
            p.padHeadMs = it; act.store.save(); recompute()
        })
        c.addView(Ui.stepper(act, "後マージン ms", p.padTailMs, 0, 500, 50) {
            p.padTailMs = it; act.store.save(); recompute()
        })

        val bar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        bar.visibility = View.GONE
        c.addView(bar, LinearLayout.LayoutParams(-1, -2))

        val status = Ui.label(act, "", true)
        c.addView(status)

        c.addView(Ui.button(act, "検出を実行", true) {
            bar.visibility = View.VISIBLE
            bar.progress = 0
            val list = p.sources.toList()
            status.text = "音声を解析しています…"
            Thread {
                var ok = 0
                for ((i, src) in list.withIndex()) {
                    act.ui.post { status.text = "${i + 1} / ${list.size} ファイル  ${src.name}" }
                    val r = Silence.analyze(act, src.uri) { pct ->
                        act.ui.post { bar.progress = (i * 100 + pct) / Math.max(1, list.size) }
                    }
                    if (r != null) {
                        act.rmsBySrc[src.uri] = r.rms
                        ok++
                    }
                }
                act.ui.post {
                    bar.visibility = View.GONE
                    if (ok == 0) {
                        status.text = "解析できませんでした"
                    } else {
                        act.rms = act.rmsBySrc[list[0].uri] ?: FloatArray(0)
                        act.rmsFor = list[0].uri
                        status.text = if (ok == list.size) "完了"
                        else "${ok} / ${list.size} ファイルのみ解析できました"
                        recompute()
                        act.toast("区間タブで確認してください")
                    }
                }
            }.start()
        })

        c.addView(wave)
        c.addView(summary)
        recompute()

        c.addView(Ui.label(act, "波形は全体の見取り図です。操作は区間タブで行います。", true))
        c.addView(Ui.spacer(act, 40))
        return Ui.scroll(act, c)
    }

    // ==================== 区間（主UI） ====================

    fun segments(act: MainActivity): View {
        val p = act.editing ?: return empty(act, "素材タブで編集を開いてください。")
        val c = Ui.col(act, 12)

        if (p.segments.isEmpty()) {
            c.addView(Ui.label(act, "区間がありません。解析タブで検出するか、素材タブで「全体を1区間に」を押してください。", true))
            return Ui.scroll(act, c)
        }

        val head = TextView(act)
        head.setTextColor(Ui.ACC)
        head.textSize = 15f
        head.typeface = Typeface.DEFAULT_BOLD
        head.text = "${p.segments.size}区間 / 採用${p.used().size} / ${Fmt.ms(p.usedMs())}"
        c.addView(head)

        val tools = Ui.row(act)
        tools.addView(Ui.button(act, "無音を全て外す") {
            for (s in p.segments) if (s.silent == 1) s.use = 0
            act.store.save(); act.refresh()
        })
        tools.addView(Ui.button(act, "全て採用") {
            for (s in p.segments) s.use = 1
            act.store.save(); act.refresh()
        })
        tools.addView(Ui.button(act, "シーンを割当") { assignScenes(act, p) })
        c.addView(Ui.scrollH(act, tools))

        for ((i, s) in p.segments.withIndex()) {
            val card = Ui.card(act)

            val row = Ui.row(act)
            val check = Ui.button(act, if (s.use == 1) "☑" else "☐", s.use == 1) {
                s.use = 1 - s.use
                act.store.save(); act.refresh()
            }
            row.addView(check)

            val info = Ui.col(act, 0)
            info.setPadding(Ui.dp(act, 8), 0, 0, 0)
            val t = TextView(act)
            t.setTextColor(if (s.silent == 1) Ui.SUB else Ui.TXT)
            t.textSize = 15f
            t.typeface = Typeface.DEFAULT_BOLD
            t.text = "${Fmt.msDot(s.inMs)} - ${Fmt.msDot(s.outMs)}   ${Fmt.sec(s.durMs())}" +
                (if (s.silent == 1) "  無音" else "")
            info.addView(t)
            if (s.label.isNotBlank()) info.addView(Ui.label(act, s.label, true))
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(row)

            val detail = Ui.col(act, 0)
            detail.visibility = View.GONE
            card.addView(detail)

            var built = false
            row.setOnClickListener {
                if (!built) {
                    buildDetail(act, p, s, i, detail)
                    built = true
                }
                detail.visibility = if (detail.visibility == View.GONE) View.VISIBLE else View.GONE
            }

            c.addView(card)
        }

        c.addView(Ui.spacer(act, 40))
        return Ui.scroll(act, c)
    }

    private fun buildDetail(
        act: MainActivity, p: EditProject, s: Segment, idx: Int, box: LinearLayout
    ) {
        val src = p.sources.getOrNull(s.srcIndex)

        val iv = ImageView(act)
        iv.adjustViewBounds = true
        box.addView(iv, LinearLayout.LayoutParams(-1, Ui.dp(act, 150)))
        if (src != null) {
            Thread {
                val b = Probe.frameAt(act, src.uri, s.inMs)
                if (b != null) act.ui.post { iv.setImageBitmap(b) }
            }.start()
        }

        val warn = TextView(act)
        warn.setTextColor(Ui.WARN)
        warn.textSize = 12f
        warn.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 4))

        fun updateWarn() {
            if (src == null) return
            val keys = act.keyCache[src.uri]
            if (keys == null || keys.isEmpty()) {
                warn.text = ""
                return
            }
            val snap = Probe.snapBefore(keys, s.inMs)
            s.snapMs = snap
            val diff = s.inMs - snap
            warn.text = if (diff > 60) "⚠ 無劣化で切ると開始が ${diff / 1000f}秒 手前にズレます" else ""
        }

        box.addView(Ui.stepper(act, "開始 ×0.1秒", (s.inMs / 100).toInt(), 0, 999999, 1) {
            s.inMs = it * 100L
            act.store.save(); updateWarn()
        })
        box.addView(Ui.stepper(act, "終了 ×0.1秒", (s.outMs / 100).toInt(), 0, 999999, 1) {
            s.outMs = it * 100L
            act.store.save()
        })

        box.addView(warn)
        box.addView(Ui.button(act, "キーフレームを調べる") {
            if (src == null) return@button
            act.toast("走査しています…")
            Thread {
                val k = act.keyframesOf(src)
                act.ui.post {
                    updateWarn()
                    act.toast("キーフレーム ${k.size}箇所")
                }
            }.start()
        })

        val label = Ui.edit(act, "ラベル")
        label.setText(s.label)
        watch(label) { s.label = it; act.store.save() }
        box.addView(label)

        val r = Ui.row(act)
        r.addView(Ui.button(act, "↑") {
            if (idx > 0) {
                val tmp = p.segments[idx - 1]; p.segments[idx - 1] = p.segments[idx]; p.segments[idx] = tmp
                act.store.save(); act.refresh()
            }
        })
        r.addView(Ui.button(act, "↓") {
            if (idx < p.segments.size - 1) {
                val tmp = p.segments[idx + 1]; p.segments[idx + 1] = p.segments[idx]; p.segments[idx] = tmp
                act.store.save(); act.refresh()
            }
        })
        r.addView(Ui.button(act, "分割") {
            val mid = (s.inMs + s.outMs) / 2
            val nw = Segment(s.srcIndex, mid, s.outMs, s.use, s.silent, "", "")
            s.outMs = mid
            p.segments.add(idx + 1, nw)
            act.store.save(); act.refresh()
        })
        r.addView(Ui.button(act, "削除") {
            p.segments.removeAt(idx)
            act.store.save(); act.refresh()
        })
        box.addView(Ui.scrollH(act, r))

        updateWarn()
    }

    private fun assignScenes(act: MainActivity, p: EditProject) {
        if (p.scenes.isEmpty()) {
            act.toast("Deskの台本が読み込まれていません")
            return
        }
        val used = p.used()
        for ((i, s) in used.withIndex()) {
            val sc = p.scenes.getOrNull(i)
            if (sc != null) {
                s.sceneId = sc.first
                if (s.label.isBlank()) s.label = sc.second
            } else s.sceneId = ""
        }
        act.store.save()
        act.toast("${Math.min(used.size, p.scenes.size)}件を対応付けました")
        act.refresh()
    }

    // ==================== 重ね ====================

    fun overlay(act: MainActivity): View {
        val p = act.editing ?: return empty(act, "素材タブで編集を開いてください。")
        val c = Ui.col(act, 14)

        c.addView(Ui.title(act, "テロップ"))
        for ((i, t) in p.telops.withIndex()) {
            val card = Ui.card(act)
            card.addView(Ui.label(act, "区間 ${t.segIndex + 1} に乗せる", true))
            card.addView(Ui.stepper(act, "対象区間", t.segIndex + 1, 1, Math.max(1, p.used().size), 1) {
                t.segIndex = it - 1; act.store.save()
            })
            val e = Ui.edit(act, "文言（16字以内が読みやすい）")
            e.setText(t.text)
            watch(e) { t.text = it; act.store.save() }
            card.addView(e)
            card.addView(Ui.stepper(act, "開始 ×0.1秒", (t.startMs / 100).toInt(), 0, 6000, 1) {
                t.startMs = it * 100L; act.store.save()
            })
            card.addView(Ui.stepper(act, "表示 ×0.1秒", (t.durMs / 100).toInt(), 5, 600, 5) {
                t.durMs = it * 100L; act.store.save()
            })
            val pr = Ui.row(act)
            for ((k, v) in listOf("bottom" to "下", "center" to "中", "top" to "上")) {
                pr.addView(Ui.button(act, v, t.pos == k) { t.pos = k; act.store.save(); act.refresh() })
            }
            card.addView(pr)
            val sr = Ui.row(act)
            for ((k, v) in listOf("bold" to "太", "thin" to "細", "outline" to "白抜", "band" to "帯")) {
                sr.addView(Ui.button(act, v, t.style == k) { t.style = k; act.store.save(); act.refresh() })
            }
            card.addView(Ui.scrollH(act, sr))
            val br2 = Ui.row(act)
            var aiT: android.widget.Button? = null
            aiT = Ui.button(act, "文言をAIで") {
                val bb = aiT ?: return@button
                val seg = p.used().getOrNull(t.segIndex)
                val body = seg?.label ?: ""
                if (body.isBlank()) {
                    act.toast("区間にラベルを付けてからにしてください")
                } else {
                    aiRun(act, bb, "文言をAIで", { Bonsai.telop(act, body, body) }) { r ->
                        if (r == null) act.toast("生成できませんでした")
                        else {
                            t.text = r
                            e.setText(r)
                            act.store.save()
                        }
                    }
                }
            }
            br2.addView(aiT)
            br2.addView(Ui.button(act, "見た目を確認") { previewTelop(act, p, t) })
            br2.addView(Ui.button(act, "削除") {
                p.telops.removeAt(i); act.store.save(); act.refresh()
            })
            card.addView(Ui.scrollH(act, br2))
            c.addView(card)
        }
        c.addView(Ui.label(act, "テロップの帯・強調色", true))
        val tsw = Ui.row(act)
        for (hex in listOf("#FF0033", "#FF7A00", "#FFC300", "#00B894", "#0A84FF", "#7B2FF2")) {
            val col = android.graphics.Color.parseColor(hex)
            tsw.addView(Ui.swatch(act, col, col == p.accent) {
                p.accent = col; act.store.save(); act.refresh()
            })
        }
        c.addView(Ui.scrollH(act, tsw))

        c.addView(Ui.button(act, "＋ テロップを追加") {
            val sug = p.used().getOrNull(p.telops.size)?.label ?: ""
            p.telops.add(Telop(segIndex = p.telops.size, text = sug))
            act.store.save(); act.refresh()
        })

        c.addView(Ui.spacer(act, 8))
        c.addView(Ui.title(act, "音"))
        val br = Ui.row(act)
        br.addView(Ui.button(act, if (p.bgmUri.isBlank()) "BGMを選ぶ" else "BGMを変更") {
            act.pickAudio { uri -> p.bgmUri = uri.toString(); act.store.save(); act.refresh() }
        })
        if (p.bgmUri.isNotBlank()) {
            br.addView(Ui.button(act, "BGMなし") { p.bgmUri = ""; act.store.save(); act.refresh() })
        }
        c.addView(Ui.scrollH(act, br))
        if (p.bgmUri.isNotBlank()) {
            c.addView(Ui.stepper(act, "音量 %", p.bgmVolume, 0, 100, 2) { p.bgmVolume = it; act.store.save() })
            c.addView(Ui.stepper(act, "フェードイン 秒", p.bgmFadeSec, 0, 10, 1) { p.bgmFadeSec = it; act.store.save() })
        }
        val lr = Ui.row(act)
        lr.addView(Ui.label(act, "音量の自動調整", true))
        lr.addView(Ui.button(act, "オン", p.loudnorm == 1) { p.loudnorm = 1; act.store.save(); act.refresh() })
        lr.addView(Ui.button(act, "オフ", p.loudnorm == 0) { p.loudnorm = 0; act.store.save(); act.refresh() })
        c.addView(lr)

        c.addView(Ui.spacer(act, 8))
        c.addView(Ui.title(act, "画角"))
        val vr = Ui.row(act)
        vr.addView(Ui.label(act, "縦切り出し", true))
        vr.addView(Ui.button(act, "しない", p.vertical == 0) { p.vertical = 0; act.store.save(); act.refresh() })
        vr.addView(Ui.button(act, "する", p.vertical == 1) { p.vertical = 1; act.store.save(); act.refresh() })
        c.addView(vr)
        if (p.vertical == 1) {
            val pr = Ui.row(act)
            for ((k, v) in listOf("center" to "中央", "left" to "左", "right" to "右")) {
                pr.addView(Ui.button(act, v, p.verticalPos == k) {
                    p.verticalPos = k; act.store.save(); act.refresh()
                })
            }
            c.addView(pr)
        }

        c.addView(Ui.spacer(act, 10))
        val warn = Ui.card(act)
        val wt = TextView(act)
        wt.setTextColor(if (p.needsFfmpeg()) Ui.WARN else Ui.SUB)
        wt.textSize = 13f
        wt.text = if (p.needsFfmpeg())
            "⚠ この画面の設定により ffmpeg が必要になります。\n設定を全て外せば速いレーンに戻れます。"
        else
            "この画面では何も設定していません。速いレーンのままです。"
        warn.addView(wt)
        c.addView(warn)

        c.addView(Ui.spacer(act, 40))
        return Ui.scroll(act, c)
    }

    // ==================== 出力 ====================

    fun output(act: MainActivity): View {
        val p = act.editing ?: return empty(act, "素材タブで編集を開いてください。")
        val c = Ui.col(act, 14)

        c.addView(act.laneCard(p))

        val detail = Ui.card(act)
        detail.addView(Ui.label(act, "内訳", true))
        detail.addView(Ui.label(act, "採用 ${p.used().size}区間 / ${Fmt.ms(p.usedMs())}"))
        detail.addView(Ui.label(act, "テロップ ${p.telops.size}件"))
        detail.addView(Ui.label(act, "BGM " + if (p.bgmUri.isBlank()) "なし" else "あり"))
        detail.addView(Ui.label(act, "縦切り出し " + if (p.vertical == 1) "する" else "なし"))
        c.addView(detail)

        val bar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        bar.visibility = View.GONE
        c.addView(bar, LinearLayout.LayoutParams(-1, -2))
        val status = Ui.label(act, "", true)
        c.addView(status)

        if (!p.needsFfmpeg()) {
            c.addView(Ui.button(act, "書き出す（無劣化）", true) {
                val name = safeName(p) + ".mp4"
                act.createFile(name, "video/mp4") { uri ->
                    runMuxer(act, p, uri, bar, status)
                }
            })
        } else {
            c.addView(Ui.label(act, "重いレーンの手順", true))
            c.addView(Ui.label(act, "1. 素材とBGMを受け渡し先フォルダにコピーしておく\n2. スクリプトを書き出す\n3. Termuxでコマンドを貼って実行", true))
            c.addView(Ui.button(act, "スクリプトを書き出す", true) {
                val name = "run_${p.id}.sh"
                val text = Cmd.script(p, "/sdcard/Download/tube", safeName(p) + ".mp4")
                if (Bridge.writeScript(act, name, text)) {
                    act.toast("書き出しました")
                } else {
                    act.createFile(name, "text/plain") { uri ->
                        Bridge.writeText(act, uri, text)
                        act.toast("書き出しました")
                    }
                }
            })
            c.addView(Ui.button(act, "テロップPNGを書き出す") {
                if (p.telops.isEmpty()) {
                    act.toast("テロップがありません")
                } else {
                    val src = p.sources.firstOrNull { it.probed == 1 }
                    val w = src?.width ?: 1920
                    val h = src?.height ?: 1080
                    val n = Bridge.writeTelops(act, p, w, h)
                    when {
                        n < 0 -> act.toast("先に受け渡し先フォルダを選んでください")
                        n == 0 -> act.toast("書き出せませんでした")
                        else -> act.toast("${'$'}{n}枚 書き出しました")
                    }
                }
            })
            c.addView(Ui.button(act, "コマンドをコピー") {
                act.copy(Cmd.runLine("/sdcard/Download/tube", "run_${p.id}.sh"), "コマンド")
            })
            c.addView(Ui.button(act, "スクリプトの中身を見る") {
                AlertDialog.Builder(act)
                    .setTitle("run_${p.id}.sh")
                    .setMessage(Cmd.script(p, "/sdcard/Download/tube", safeName(p) + ".mp4"))
                    .setPositiveButton("閉じる", null)
                    .show()
            })
            val watchBar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal)
            watchBar.max = 100
            watchBar.visibility = View.GONE
            c.addView(watchBar, LinearLayout.LayoutParams(-1, -2))
            val watchText = Ui.label(act, "", true)
            c.addView(watchText)

            var watchBtn: android.widget.Button? = null
            watchBtn = Ui.button(act, "実行を見守る", true) {
                val b = watchBtn ?: return@button
                if (act.runner != null) {
                    act.stopWatch()
                    b.text = "実行を見守る"
                    watchBar.visibility = View.GONE
                    watchText.text = "見守りを止めました"
                } else {
                    if (Bridge.treeUri(act) == null) {
                        act.toast("先に受け渡し先フォルダを選んでください")
                    } else {
                        b.text = "見守りを止める"
                        watchBar.visibility = View.VISIBLE
                        act.startWatch(p) { r ->
                            watchBar.progress = r.percent()
                            watchText.text = r.label()
                            if (r.done) {
                                watchBar.progress = 100
                                b.text = "実行を見守る"
                                act.toast("完成しました。ファイルを指定してください")
                            }
                        }
                    }
                }
            }
            c.addView(watchBtn)
            c.addView(Ui.button(act, "エラー出力を見る") {
                val r = act.runner ?: Runner(act, p)
                AlertDialog.Builder(act)
                    .setTitle(Cmd.F_LOG)
                    .setMessage(r.tail())
                    .setPositiveButton("閉じる", null)
                    .show()
            })
            c.addView(Ui.button(act, "完成ファイルを指定") {
                act.pickMedia { uri ->
                    p.outputUri = uri.toString()
                    act.store.save()
                    act.toast("完成ファイルを記録しました")
                }
            })
        }

        c.addView(Ui.spacer(act, 10))
        c.addView(Ui.label(act, "Desk連携", true))
        c.addView(Ui.button(act, "Deskへ結果を返す") {
            if (p.workId.isBlank()) {
                act.toast("Deskの台本が読み込まれていません")
            } else if (Bridge.pushResultToProvider(act, p)) {
                act.toast("Deskに返しました")
            } else if (Bridge.writeResult(act, p)) {
                act.toast("返しました")
            } else {
                act.createFile("result_${p.workId}.json", "application/json") { uri ->
                    Bridge.writeText(act, uri, Bridge.buildResult(p))
                    act.toast("書き出しました")
                }
            }
        })
        c.addView(Ui.button(act, "EditPlanを書き出す") {
            act.createFile("editplan_${p.id}.json", "application/json") { uri ->
                Bridge.writeText(act, uri, p.toJson().toString(2))
                act.toast("書き出しました")
            }
        })

        c.addView(Ui.spacer(act, 40))
        return Ui.scroll(act, c)
    }

    private fun previewTelop(act: MainActivity, p: EditProject, t: Telop) {
        val src = p.sources.firstOrNull { it.probed == 1 }
        val w = src?.width ?: 1920
        val h = src?.height ?: 1080
        val iv = ImageView(act)
        iv.adjustViewBounds = true
        iv.setBackgroundColor(android.graphics.Color.parseColor("#202830"))
        try {
            val small = TelopDraw.render(t, w / 3, h / 3, p.accent)
            iv.setImageBitmap(small)
        } catch (e: Throwable) {
        }
        AlertDialog.Builder(act)
            .setTitle("テロップの見た目")
            .setView(iv)
            .setPositiveButton("閉じる", null)
            .show()
    }

    private fun safeName(p: EditProject): String =
        p.name.replace(Regex("[^\\p{L}\\p{N}_-]"), "").take(16).ifBlank { "cut" }

    private fun runMuxer(
        act: MainActivity, p: EditProject, uri: Uri,
        bar: ProgressBar, status: TextView
    ) {
        bar.visibility = View.VISIBLE
        bar.progress = 0
        status.text = "書き出しています…"
        val prog = Muxer.Progress(0, p.used().size)
        Thread {
            val err = Muxer.run(act, p, uri, prog) { done, total ->
                act.ui.post {
                    bar.progress = done * 100 / Math.max(1, total)
                    status.text = "$done / $total 区間"
                }
            }
            act.ui.post {
                bar.visibility = View.GONE
                if (err.isBlank()) {
                    p.outputUri = uri.toString()
                    act.store.save()
                    status.text = "完了"
                    act.toast("書き出しました")
                } else {
                    status.text = err
                    act.toast(err)
                }
            }
        }.start()
    }
}
