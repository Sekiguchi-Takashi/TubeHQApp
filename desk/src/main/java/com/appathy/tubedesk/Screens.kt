package com.appathy.tubedesk

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

object Screens {

    fun watch(e: EditText, cb: (String) -> Unit) {
        e.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = cb(s?.toString() ?: "")
        })
    }


    /**
     * AI_RULES.md R1/R4。決定的版が既に出ている前提で、押した人だけが待つ。
     * 経過秒数を出す。進捗が読めないためプログレスバーは使わない。
     */
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
                val s = (System.currentTimeMillis() - started) / 1000
                btn.text = "生成中… ${s}秒"
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

    /**
     * 受け渡し先フォルダの案内。未設定だと Cut との受け渡しが動かないので、
     * ホームの最上部に常設する。
     */
    fun folderCard(act: MainActivity): View {
        val card = Ui.card(act)
        val set = Bridge.treeUri(act) != null

        val t = TextView(act)
        t.textSize = 16f
        t.typeface = Typeface.DEFAULT_BOLD
        t.setTextColor(if (set) Ui.ACC else Color.parseColor("#C94A3A"))
        t.text = if (set) "受け渡し先フォルダ" else "受け渡し先フォルダが未設定です"
        card.addView(t)

        val d = TextView(act)
        d.setTextColor(Ui.SUB)
        d.textSize = 13f
        d.text = if (set) {
            Bridge.treePath(act) ?: "設定済み"
        } else {
            "TubeCut との台本・結果の受け渡しに使います。\n" +
                "内部ストレージに空フォルダを1つ作って選んでください（例: Download/tube）\n" +
                "※TubeCut 側でも同じフォルダを選ぶこと"
        }
        card.addView(d)

        card.addView(Ui.button(act, if (set) "フォルダを変更" else "フォルダを選ぶ", !set) {
            act.pickTree { uri ->
                Bridge.setTree(act, uri)
                act.toast("受け渡し先を設定しました")
                act.refresh()
            }
        })
        return card
    }

    fun empty(act: MainActivity, msg: String): View {
        val c = Ui.col(act, 18)
        c.addView(Ui.title(act, "作品が選ばれていません"))
        c.addView(Ui.label(act, msg, true))
        c.addView(Ui.button(act, "ホームへ", true) { act.show(0) })
        return c
    }

    // ==================== 台本 ====================

    fun script(act: MainActivity): View {
        val p = act.editing ?: return empty(act, "ホームかネタ帳から作品を選んでください。")
        val c = Ui.col(act, 14)
        c.addView(Ui.title(act, "台本"))

        val titleEdit = Ui.edit(act, "作品タイトル")
        titleEdit.setText(p.title)
        watch(titleEdit) { p.title = it; act.store.save() }
        c.addView(titleEdit)

        val meter = TextView(act)
        meter.setTextColor(Ui.ACC)
        meter.textSize = 16f
        meter.typeface = Typeface.DEFAULT_BOLD
        fun updateMeter() {
            meter.text = if (p.hasReal())
                "実尺 ${Ui.mmss(p.realTotal)}（推定 ${Ui.mmss(p.seconds())}）／ ${p.scenes.size}シーン"
            else
                "想定尺 ${Ui.mmss(p.seconds())} ／ ${p.chars()}字 ／ ${p.scenes.size}シーン"
        }
        updateMeter()
        c.addView(meter)

        val tools = Ui.row(act)
        tools.addView(Ui.button(act, "テンプレを流し込む") {
            val t = Templates.of(p.type)
            for ((i, s) in t.withIndex()) {
                if (i < p.scenes.size) {
                    if (p.scenes[i].head.isBlank()) p.scenes[i].head = s.head
                    if (p.scenes[i].note.isBlank()) p.scenes[i].note = s.note
                } else p.scenes.add(Scene(head = s.head, note = s.note))
            }
            act.store.save(); act.refresh()
        })
        tools.addView(Ui.button(act, "カンペ", true) {
            act.startActivity(Intent(act, PrompterActivity::class.java).putExtra("id", p.id))
        })
        c.addView(Ui.scrollH(act, tools))

        val statusRow = Ui.row(act)
        statusRow.addView(Ui.label(act, "状態: ${Project.statusLabel(p.status)}", true))
        statusRow.addView(Ui.button(act, "次の工程へ") { act.advance(p) })
        c.addView(statusRow)

        c.addView(Ui.spacer(act, 6))

        for ((idx, sc) in p.scenes.withIndex()) {
            val card = Ui.card(act)
            val headRow = Ui.row(act)
            headRow.addView(Ui.chip(act, "${idx + 1}", Ui.ACC))
            val secs = TextView(act)
            secs.setTextColor(if (sc.realDur > 0) Ui.ACC else Ui.SUB)
            secs.textSize = 12f
            secs.text = (if (sc.realDur > 0) "実 " else "約") + "${p.sceneSeconds(sc)}秒"
            headRow.addView(secs)
            card.addView(headRow)

            val h = Ui.edit(act, "見出し")
            h.setText(sc.head)
            watch(h) { sc.head = it; act.store.save() }
            card.addView(h)

            val b = Ui.edit(act, "喋る内容", true, 4)
            b.setText(sc.body)
            watch(b) {
                sc.body = it
                if (sc.realDur <= 0) secs.text = "約${p.sceneSeconds(sc)}秒"
                updateMeter()
                act.store.save()
            }
            card.addView(b)

            val noteHint = when (p.type) {
                Project.T_SLIDE -> "使う写真メモ"
                Project.T_SCREEN -> "画面・操作メモ"
                else -> "画・小道具メモ"
            }
            val n = Ui.edit(act, noteHint, true, 2)
            n.setText(sc.note)
            watch(n) { sc.note = it; act.store.save() }
            card.addView(n)

            val r = Ui.row(act)
            r.addView(Ui.button(act, "↑") {
                if (idx > 0) {
                    val tmp = p.scenes[idx - 1]; p.scenes[idx - 1] = p.scenes[idx]; p.scenes[idx] = tmp
                    act.store.save(); act.refresh()
                }
            })
            r.addView(Ui.button(act, "↓") {
                if (idx < p.scenes.size - 1) {
                    val tmp = p.scenes[idx + 1]; p.scenes[idx + 1] = p.scenes[idx]; p.scenes[idx] = tmp
                    act.store.save(); act.refresh()
                }
            })
            r.addView(Ui.button(act, "削除") {
                p.scenes.removeAt(idx); act.store.save(); act.refresh()
            })
            var aiHead: android.widget.Button? = null
            aiHead = Ui.button(act, "見出しAI") {
                val bb = aiHead ?: return@button
                if (sc.body.isBlank()) {
                    act.toast("本文を書いてからにしてください")
                } else {
                    aiRun(act, bb, "見出しAI", { Bonsai.head(act, sc.body) }) { res ->
                        if (res == null) act.toast("生成できませんでした")
                        else {
                            sc.head = res
                            h.setText(res)
                            act.store.save()
                        }
                    }
                }
            }
            r.addView(aiHead)
            card.addView(Ui.scrollH(act, r))
            c.addView(card)
        }

        c.addView(Ui.button(act, "＋ シーンを追加") {
            p.scenes.add(Scene())
            act.store.save(); act.refresh()
        })

        c.addView(Ui.spacer(act, 10))
        c.addView(Ui.button(act, "Cutへ送る", true) { Bridge.pushPlan(act, p) })
        c.addView(Ui.button(act, "Cutの結果を取り込む") { Bridge.pullResults(act, true) })

        c.addView(Ui.spacer(act, 40))
        return Ui.scroll(act, c)
    }

    // ==================== 画像 ====================

    fun image(act: MainActivity): View {
        val p = act.editing ?: return empty(act, "先に作品を選んでください。")
        val c = Ui.col(act, 14)

        var idx = p.images.indexOfFirst { it.adopted == 1 }
        if (idx < 0) idx = 0
        if (p.images.isEmpty()) p.images.add(ImageSpec())

        fun spec() = p.images[idx.coerceIn(0, p.images.size - 1)]

        val (pv, redraw0) = act.previewView(spec())
        val redraw = {
            act.clearBgCache()
            redraw0()
        }

        val slotRow = Ui.row(act)
        for ((i, im) in p.images.withIndex()) {
            val name = (if (im.role == ImageSpec.ROLE_THUMB) "サムネ" else "宣伝") +
                (i + 1) + (if (im.adopted == 1) " ★" else "")
            slotRow.addView(Ui.button(act, name, i == idx) {
                idx = i
                act.refresh()
            })
        }
        slotRow.addView(Ui.button(act, "＋") {
            p.images.add(ImageSpec())
            act.store.save(); act.refresh()
        })
        c.addView(Ui.scrollH(act, slotRow))

        c.addView(pv)

        c.addView(Ui.label(act, "様式", true))
        val styleRow = Ui.row(act)
        for (st in ImageSpec.STYLES) {
            styleRow.addView(Ui.button(act, ImageSpec.styleLabel(st), st == spec().style) {
                spec().style = st
                spec().role = ImageSpec.defaultRole(st)
                act.store.save(); act.refresh()
            })
        }
        c.addView(Ui.scrollH(act, styleRow))

        val bgRow = Ui.row(act)
        bgRow.addView(Ui.button(act, "背景を選ぶ", true) {
            act.pickImage { uri ->
                spec().bg = uri.toString()
                act.store.save(); redraw()
            }
        })
        bgRow.addView(Ui.button(act, "背景なし") {
            spec().bg = ""; act.store.save(); redraw()
        })
        c.addView(Ui.scrollH(act, bgRow))

        fun field(label: String, hint: String, get: () -> String, set: (String) -> Unit, multi: Boolean = false) {
            c.addView(Ui.label(act, label, true))
            val e = Ui.edit(act, hint, multi)
            e.setText(get())
            watch(e) { set(it); act.store.save(); redraw0() }
            c.addView(e)
        }

        val s = spec()
        when (s.style) {
            ImageSpec.SHORTS -> {
                field("画面の文字", "大きな焼き文字（改行可）", { s.title }, { s.title = it }, true)
                field("チャンネル名", "@channel", { s.channel }, { s.channel = it })
                field("説明", "下に出る一言", { s.sub }, { s.sub = it }, true)
                field("音源名", "曲名", { s.music }, { s.music = it })
                field("いいね数", "1.4万", { s.likes }, { s.likes = it })
                field("コメント数", "231", { s.comments }, { s.comments = it })
            }
            ImageSpec.PLAYER -> {
                field("タイトル", "動画タイトル", { s.title }, { s.title = it }, true)
                field("チャンネル名", "@channel", { s.channel }, { s.channel = it })
                field("再生数・日付", "1.2万回視聴・3日前", { s.meta }, { s.meta = it })
                field("総再生時間", "8:42", { s.duration }, { s.duration = it })
            }
            ImageSpec.THUMB -> {
                field("テロップ", "大きな文字（改行可）", { s.title }, { s.title = it }, true)
                field("サブ文字", "アクセント色の一言", { s.sub }, { s.sub = it })
                field("チャンネル名", "表示名", { s.channel }, { s.channel = it })
                field("尺バッジ", "8:42", { s.duration }, { s.duration = it })
            }
            else -> {
                field("主題（縦書き）", "改行で列が分かれます", { s.title }, { s.title = it }, true)
                field("副題", "下帯に出る一言", { s.sub }, { s.sub = it }, true)
                field("話数", "第12話", { s.meta }, { s.meta = it })
            }
        }

        c.addView(Ui.label(act, "アクセント色", true))
        val sw = Ui.row(act)
        for (hex in listOf("#FF0033", "#FF7A00", "#FFC300", "#00B894", "#0A84FF", "#7B2FF2", "#FF4FA3", "#FFFFFF")) {
            val col = Color.parseColor(hex)
            sw.addView(Ui.swatch(act, col, col == s.accent) {
                s.accent = col; act.store.save(); act.refresh()
            })
        }
        c.addView(Ui.scrollH(act, sw))

        c.addView(Ui.stepper(act, "明るさ", s.bright, -50, 50, 5) { s.bright = it; act.store.save(); redraw0() })
        c.addView(Ui.stepper(act, "コントラスト", s.contrast, -50, 50, 5) { s.contrast = it; act.store.save(); redraw0() })
        c.addView(Ui.stepper(act, "彩度", s.sat, -100, 100, 10) { s.sat = it; act.store.save(); redraw0() })
        c.addView(Ui.stepper(act, "退色", s.fade, 0, 100, 5) { s.fade = it; act.store.save(); redraw0() })
        c.addView(Ui.stepper(act, "ぼかし", s.blur, 0, 60, 5) { s.blur = it; act.store.save(); redraw() })

        val actRow = Ui.row(act)
        actRow.addView(Ui.button(act, "台本から流し込む") {
            s.title = p.title
            s.sub = p.scenes.firstOrNull { it.body.isNotBlank() }?.body?.take(28) ?: ""
            if (p.hasReal()) s.duration = Ui.mmss(p.realTotal)
            s.origin = "template"
            act.store.save(); act.refresh()
        })
        var aiThumb: android.widget.Button? = null
        aiThumb = Ui.button(act, "文言をAIで") {
            val b = aiThumb ?: return@button
            val body = p.scenes.firstOrNull { it.body.isNotBlank() }?.body ?: ""
            aiRun(act, b, "文言をAIで", { Bonsai.thumbText(act, p.title, body, s.style) }) { r ->
                if (r == null) act.toast("生成できませんでした")
                else {
                    s.title = r.first
                    if (r.second.isNotBlank()) s.sub = r.second
                    s.origin = "ai"
                    act.store.save(); act.refresh()
                }
            }
        }
        actRow.addView(aiThumb)
        actRow.addView(Ui.button(act, "この案を採用") {
            for (im in p.images) if (im.role == s.role) im.adopted = 0
            s.adopted = 1
            act.store.save(); act.refresh()
        })
        actRow.addView(Ui.button(act, "削除") {
            if (p.images.size > 1) {
                p.images.removeAt(idx); act.store.save(); act.refresh()
            } else act.toast("最後の1枚は消せません")
        })
        c.addView(Ui.scrollH(act, actRow))

        val sz = ImageSpec.size(s.style)
        c.addView(Ui.button(act, "PNGで書き出す（${sz.first}×${sz.second}）", true) {
            val base = p.title.ifBlank { "image" }
            val safe = base.replace(Regex("[^\\p{L}\\p{N}_-]"), "").take(16).ifBlank { "image" }
            act.createFile("${safe}_${s.style}.png", "image/png") { uri ->
                try {
                    val bmp = Frames.render(s, act.bgOf(s), 1f)
                    act.contentResolver.openOutputStream(uri)?.use {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    act.toast("書き出しました")
                } catch (e: Throwable) {
                    act.toast("失敗: ${e.message}")
                }
            }
        })

        c.addView(Ui.spacer(act, 40))
        return Ui.scroll(act, c)
    }

    // ==================== メタ ====================

    fun meta(act: MainActivity): View {
        val p = act.editing ?: return empty(act, "先に作品を選んでください。")
        val c = Ui.col(act, 14)
        c.addView(Ui.title(act, "メタデータ"))

        val titleEdit = Ui.edit(act, "動画タイトル", true, 2)
        titleEdit.setText(p.metaTitle.ifBlank { p.title })
        watch(titleEdit) { p.metaTitle = it; act.store.save() }
        c.addView(Ui.label(act, "タイトル", true))
        c.addView(titleEdit)

        c.addView(Ui.label(act, "候補（タップで採用）", true))
        val candBox = LinearLayout(act)
        candBox.orientation = LinearLayout.VERTICAL
        for (cand in Suggest.titles(p.title)) {
            candBox.addView(Ui.button(act, cand) {
                titleEdit.setText(cand)
                p.metaTitle = cand
                act.store.save()
            })
        }
        c.addView(candBox)

        var aiTitle: android.widget.Button? = null
        aiTitle = Ui.button(act, "AIで作り直す") {
            val b = aiTitle ?: return@button
            val lead = p.scenes.firstOrNull { it.body.isNotBlank() }?.body ?: ""
            aiRun(act, b, "AIで作り直す", {
                Bonsai.titles(act, p.title, Project.typeLabel(p.type), lead)
            }) { r ->
                if (r == null) act.toast("候補は増えませんでした")
                else {
                    for (v in r.reversed()) {
                        candBox.addView(Ui.button(act, v) {
                            titleEdit.setText(v); p.metaTitle = v; act.store.save()
                        }, 0)
                    }
                }
            }
        }
        c.addView(aiTitle)

        val descEdit = Ui.edit(act, "説明文", true, 8)
        descEdit.setText(p.metaDesc)
        watch(descEdit) { p.metaDesc = it; act.store.save() }
        c.addView(Ui.label(act, "説明文", true))
        c.addView(descEdit)

        c.addView(Ui.label(
            act,
            if (p.hasReal()) "チャプターは実尺から生成します" else "チャプターは想定尺から生成します（未編集）",
            true
        ))

        val r1 = Ui.row(act)
        r1.addView(Ui.button(act, "説明文を生成", true) {
            val d = Suggest.desc(p, titleEdit.text.toString())
            descEdit.setText(d); p.metaDesc = d; act.store.save()
        })
        r1.addView(Ui.button(act, "チャプターのみコピー") { act.copy(Suggest.chapters(p), "チャプター") })
        var aiLead: android.widget.Button? = null
        aiLead = Ui.button(act, "冒頭をAIで") {
            val b = aiLead ?: return@button
            val body = p.scenes.firstOrNull { it.body.isNotBlank() }?.body ?: ""
            aiRun(act, b, "冒頭をAIで", { Bonsai.lead(act, titleEdit.text.toString(), body) }) { r ->
                if (r == null) act.toast("生成できませんでした")
                else {
                    val d = Suggest.desc(p, titleEdit.text.toString(), r)
                    descEdit.setText(d)
                    p.metaDesc = d
                    act.store.save()
                }
            }
        }
        r1.addView(aiLead)
        c.addView(Ui.scrollH(act, r1))

        val tagEdit = Ui.edit(act, "タグ（カンマ区切り）", true, 3)
        tagEdit.setText(p.metaTags)
        watch(tagEdit) { p.metaTags = it; act.store.save() }
        c.addView(Ui.label(act, "タグ", true))
        c.addView(tagEdit)
        val tagRow = Ui.row(act)
        tagRow.addView(Ui.button(act, "タグを生成") {
            val t = Suggest.tags(p)
            tagEdit.setText(t); p.metaTags = t; act.store.save()
        })
        var aiTag: android.widget.Button? = null
        aiTag = Ui.button(act, "AIで足す") {
            val b = aiTag ?: return@button
            aiRun(act, b, "AIで足す", {
                Bonsai.tags(act, p.title, p.metaTitle, Project.typeLabel(p.type))
            }) { r ->
                if (r == null) act.toast("生成できませんでした")
                else {
                    val cur = tagEdit.text.toString().split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val merged = (cur + r).distinct().joinToString(",")
                    tagEdit.setText(merged); p.metaTags = merged; act.store.save()
                }
            }
        }
        tagRow.addView(aiTag)
        c.addView(Ui.scrollH(act, tagRow))

        c.addView(Ui.spacer(act, 8))
        val r2 = Ui.row(act)
        r2.addView(Ui.button(act, "タイトルをコピー", true) { act.copy(titleEdit.text.toString(), "タイトル") })
        r2.addView(Ui.button(act, "説明文をコピー", true) { act.copy(descEdit.text.toString(), "説明文") })
        r2.addView(Ui.button(act, "タグをコピー") { act.copy(tagEdit.text.toString(), "タグ") })
        c.addView(Ui.scrollH(act, r2))

        c.addView(Ui.spacer(act, 40))
        return Ui.scroll(act, c)
    }

    // ==================== 実績 ====================

    fun record(act: MainActivity): View {
        val p = act.editing ?: return empty(act, "先に作品を選んでください。")
        val c = Ui.col(act, 14)
        c.addView(Ui.title(act, "実績"))

        val url = Ui.edit(act, "公開URL")
        url.setText(p.pubUrl)
        watch(url) { p.pubUrl = it; act.store.save() }
        c.addView(Ui.label(act, "公開", true))
        c.addView(url)

        val at = Ui.edit(act, "公開日時（2026-08-12 20:00）")
        at.setText(p.pubAt)
        watch(at) { p.pubAt = it; act.store.save() }
        c.addView(at)

        if (p.outputUri.isNotBlank()) {
            c.addView(Ui.label(act, "完成ファイルあり（Cutから受領）", true))
            val orow = Ui.row(act)
            orow.addView(Ui.button(act, "完成ファイルを開く", true) { act.openOutput(p) })
            orow.addView(Ui.button(act, "リンクをコピー") { act.copy(p.outputUri, "完成ファイルの場所") })
            c.addView(Ui.scrollH(act, orow))
            if (p.hasReal()) {
                c.addView(Ui.label(act, "実尺 ${Ui.mmss(p.realTotal)}", true))
            }
        } else {
            c.addView(Ui.label(act, "完成ファイルは未受領です（Cutで書き出して「Deskへ結果を返す」）", true))
        }

        c.addView(Ui.label(act, "数字（手入力）", true))
        val v = Ui.edit(act, "再生数")
        val g = Ui.edit(act, "高評価")
        val cm = Ui.edit(act, "コメント")
        val rt = Ui.edit(act, "視聴維持率 %")
        c.addView(v); c.addView(g); c.addView(cm); c.addView(rt)

        c.addView(Ui.button(act, "記録する", true) {
            val line = listOf(
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.JAPAN)
                    .format(java.util.Date()),
                v.text.toString().ifBlank { "-" },
                g.text.toString().ifBlank { "-" },
                cm.text.toString().ifBlank { "-" },
                rt.text.toString().ifBlank { "-" }
            ).joinToString(" / ")
            p.records.add(line)
            act.store.save()
            act.toast("記録しました")
            act.refresh()
        })

        c.addView(Ui.label(act, "履歴（日付 / 再生 / 高評価 / コメント / 維持率）", true))
        if (p.records.isEmpty()) c.addView(Ui.label(act, "まだありません", true))
        for (line in p.records.reversed()) {
            val t = TextView(act)
            t.text = line
            t.setTextColor(Ui.TXT)
            t.textSize = 14f
            t.setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 6))
            c.addView(t)
        }

        c.addView(Ui.spacer(act, 40))
        return Ui.scroll(act, c)
    }
}

object Suggest {
    fun titles(t0: String): List<String> {
        val t = t0.ifBlank { "テーマ" }
        return listOf(
            "【保存版】$t",
            "${t}を3分で",
            "知らないと損する$t",
            "$t｜最初にやるべき5つのこと",
            "初心者向け：${t}の始め方",
            "【実録】${t}をやってみた結果"
        )
    }

    fun chapters(p: Project): String {
        val sb = StringBuilder()
        if (p.hasReal()) {
            for (s in p.scenes) {
                if (s.realStart < 0) continue
                sb.append(Ui.mmss(s.realStart)).append(" ")
                    .append(s.head.ifBlank { "パート" }).append("\n")
            }
            if (sb.isNotEmpty()) return sb.toString().trimEnd()
        }
        var t = 0
        for (s in p.scenes) {
            if (s.head.isBlank() && s.body.isBlank()) continue
            sb.append(Ui.mmss(t)).append(" ").append(s.head.ifBlank { "パート" }).append("\n")
            t += p.sceneSeconds(s)
        }
        return sb.toString().trimEnd()
    }

    fun desc(p: Project, title: String, leadOverride: String? = null): String {
        val lead = leadOverride
            ?: (p.scenes.firstOrNull { it.body.isNotBlank() }?.body?.take(60) ?: "")
        return buildString {
            append(title).append("\n\n")
            if (lead.isNotBlank()) append(lead).append("\n\n")
            append("■ チャプター\n")
            append(chapters(p)).append("\n\n")
            append("■ 関連動画\n\n")
            append("■ 使用機材・環境\n・スマホ1台\n\n")
            append("#").append(p.title.replace(" ", "").take(14))
        }
    }

    fun tags(p: Project): String {
        val base = when (p.type) {
            Project.T_TALK -> listOf("解説", "一人語り", "初心者向け")
            Project.T_SLIDE -> listOf("写真", "スライド", "まとめ")
            else -> listOf("画面録画", "使い方", "チュートリアル")
        }
        val words = p.title.split(" ", "　", "・", "、", "｜", "|")
            .map { it.trim() }.filter { it.length >= 2 }
        return (words + base).distinct().joinToString(",")
    }
}
