package com.appathy.tubehq

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

object Screens {

    private fun watch(e: EditText, cb: (String) -> Unit) {
        e.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = cb(s?.toString() ?: "")
        })
    }

    private fun empty(act: MainActivity, msg: String): View {
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
            meter.text = "想定尺 ${Ui.mmss(p.seconds())} ／ ${p.chars()}字 ／ ${p.scenes.size}シーン"
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
                } else p.scenes.add(Scene(s.head, "", s.note))
            }
            act.store.save()
            act.refresh()
        })
        tools.addView(Ui.button(act, "カンペ", true) {
            act.startActivity(Intent(act, PrompterActivity::class.java).putExtra("id", p.id))
        })
        c.addView(tools)

        val statusRow = Ui.row(act)
        statusRow.addView(Ui.label(act, "状態: ${Project.statusLabel(p.status)}", true))
        statusRow.addView(Ui.button(act, "次の工程へ") {
            val i = Project.STATUS_ORDER.indexOf(p.status)
            p.status = Project.STATUS_ORDER[Math.min(i + 1, Project.STATUS_ORDER.size - 1)]
            act.store.save()
            act.refresh()
        })
        c.addView(statusRow)

        c.addView(Ui.spacer(act, 6))

        for ((idx, sc) in p.scenes.withIndex()) {
            val card = Ui.card(act)

            val headRow = Ui.row(act)
            headRow.addView(Ui.chip(act, "${idx + 1}", Ui.ACC))
            val secs = TextView(act)
            secs.setTextColor(Ui.SUB)
            secs.textSize = 12f
            secs.text = "約${p.sceneSeconds(sc)}秒"
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
                secs.text = "約${p.sceneSeconds(sc)}秒"
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
            card.addView(r)

            c.addView(card)
        }

        c.addView(Ui.button(act, "＋ シーンを追加") {
            p.scenes.add(Scene("", "", ""))
            act.store.save(); act.refresh()
        })
        c.addView(Ui.spacer(act, 40))

        return Ui.scroll(act, c)
    }

    // ==================== サムネ ====================

    fun thumb(act: MainActivity): View {
        val p = act.editing ?: return empty(act, "先に作品を選んでください。")
        val c = Ui.col(act, 14)

        var slot = 0
        var bg: Bitmap? = null

        c.addView(Ui.title(act, "サムネイル（次回予告フォーマット）"))
        c.addView(Ui.label(act, "画角とレイアウトは固定。変えるのは背景と文字だけ。", true))

        val preview = ImageView(act)
        preview.adjustViewBounds = true
        preview.scaleType = ImageView.ScaleType.FIT_CENTER
        val plp = LinearLayout.LayoutParams(-1, -2)
        plp.topMargin = Ui.dp(act, 8)
        plp.bottomMargin = Ui.dp(act, 8)
        preview.layoutParams = plp

        val mainEdit = Ui.edit(act, "主題（縦書き・改行で列を分ける）", true, 2)
        val subEdit = Ui.edit(act, "副題（下の帯）", true, 2)
        val epEdit = Ui.edit(act, "話数など（例 第12話）")

        fun spec() = p.thumbs[slot]

        fun redraw() {
            val s = spec()
            bg = act.loadBitmap(s.bg)
            preview.setImageBitmap(Yokoku.render(bg, s.main, s.sub, s.ep))
        }

        fun loadFields() {
            val s = spec()
            mainEdit.setText(s.main)
            subEdit.setText(s.sub)
            epEdit.setText(s.ep)
            redraw()
        }

        watch(mainEdit) { spec().main = it; act.store.save() }
        watch(subEdit) { spec().sub = it; act.store.save() }
        watch(epEdit) { spec().ep = it; act.store.save() }

        val slotRow = Ui.row(act)
        val slotBtns = mutableListOf<TextView>()
        fun paintSlots() {
            for ((i, b) in slotBtns.withIndex()) {
                val on = i == slot
                val g = GradientDrawable()
                g.setColor(if (on) Ui.ACC else Color.parseColor("#222C38"))
                g.cornerRadius = Ui.dp(act, 8).toFloat()
                b.background = g
                b.setTextColor(if (on) Color.parseColor("#101418") else Ui.TXT)
            }
        }
        for (i in 0..1) {
            val b = Ui.button(act, if (i == 0) "A案" else "B案") {
                slot = i
                paintSlots()
                loadFields()
            }
            slotBtns.add(b)
            slotRow.addView(b)
        }
        c.addView(slotRow)
        paintSlots()

        c.addView(preview)

        c.addView(Ui.label(act, "主題", true))
        c.addView(mainEdit)
        c.addView(Ui.label(act, "副題", true))
        c.addView(subEdit)
        c.addView(Ui.label(act, "話数", true))
        c.addView(epEdit)

        val r1 = Ui.row(act)
        r1.addView(Ui.button(act, "背景を選ぶ") {
            act.pickImage { uri ->
                spec().bg = uri.toString()
                act.store.save()
                redraw()
            }
        })
        r1.addView(Ui.button(act, "プレビュー更新", true) { redraw() })
        c.addView(r1)

        val r2 = Ui.row(act)
        r2.addView(Ui.button(act, "台本から流し込む") {
            val s = spec()
            s.main = p.title
            s.sub = p.scenes.firstOrNull { it.body.isNotBlank() }?.body?.take(28) ?: ""
            act.store.save()
            loadFields()
        })
        r2.addView(Ui.button(act, "A案をB案にコピー") {
            val a = p.thumbs[0]
            p.thumbs[1] = ThumbSpec(a.main, a.sub, a.ep, a.bg)
            act.store.save()
            act.toast("コピーしました")
        })
        c.addView(r2)

        c.addView(Ui.button(act, "PNGで書き出す（1280×720）", true) {
            val s = spec()
            val name = "thumb_${p.title.take(12).ifBlank { "untitled" }}_${if (slot == 0) "A" else "B"}.png"
            act.createFile(name, "image/png") { uri ->
                try {
                    val bmp = Yokoku.render(act.loadBitmap(s.bg), s.main, s.sub, s.ep)
                    act.contentResolver.openOutputStream(uri)?.use {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    act.toast("書き出しました")
                } catch (e: Exception) {
                    act.toast("失敗: ${e.message}")
                }
            }
        })

        c.addView(Ui.label(act, "A案 / B案 くらべ", true))
        val cmp = LinearLayout(act)
        cmp.orientation = LinearLayout.VERTICAL
        c.addView(cmp)
        c.addView(Ui.button(act, "2案を並べて表示") {
            cmp.removeAllViews()
            for (i in 0..1) {
                val s = p.thumbs[i]
                cmp.addView(Ui.label(act, if (i == 0) "A案" else "B案", true))
                val iv = ImageView(act)
                iv.adjustViewBounds = true
                iv.setImageBitmap(Yokoku.render(act.loadBitmap(s.bg), s.main, s.sub, s.ep))
                cmp.addView(iv, LinearLayout.LayoutParams(-1, -2))
            }
        })

        c.addView(Ui.spacer(act, 40))
        loadFields()
        return Ui.scroll(act, c)
    }

    // ==================== メタデータ ====================

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
        for (cand in titleCandidates(p.title)) {
            c.addView(Ui.button(act, cand) {
                titleEdit.setText(cand)
                p.metaTitle = cand
                act.store.save()
            })
        }

        val descEdit = Ui.edit(act, "説明文", true, 8)
        descEdit.setText(p.metaDesc)
        watch(descEdit) { p.metaDesc = it; act.store.save() }
        c.addView(Ui.label(act, "説明文", true))
        c.addView(descEdit)

        val r1 = Ui.row(act)
        r1.addView(Ui.button(act, "説明文を生成", true) {
            val d = buildDesc(p, titleEdit.text.toString())
            descEdit.setText(d)
            p.metaDesc = d
            act.store.save()
        })
        r1.addView(Ui.button(act, "チャプターだけコピー") {
            act.copy(chapters(p), "チャプター")
        })
        c.addView(r1)

        val tagEdit = Ui.edit(act, "タグ（カンマ区切り）", true, 3)
        tagEdit.setText(p.metaTags)
        watch(tagEdit) { p.metaTags = it; act.store.save() }
        c.addView(Ui.label(act, "タグ", true))
        c.addView(tagEdit)

        c.addView(Ui.button(act, "タグを生成") {
            val t = buildTags(p)
            tagEdit.setText(t)
            p.metaTags = t
            act.store.save()
        })

        c.addView(Ui.spacer(act, 8))
        c.addView(Ui.label(act, "YouTube Studioアプリに貼るだけ", true))
        val r2 = Ui.row(act)
        r2.addView(Ui.button(act, "タイトルをコピー", true) {
            act.copy(titleEdit.text.toString(), "タイトル")
        })
        r2.addView(Ui.button(act, "説明文をコピー", true) {
            act.copy(descEdit.text.toString(), "説明文")
        })
        c.addView(r2)
        c.addView(Ui.button(act, "タグをコピー") { act.copy(tagEdit.text.toString(), "タグ") })

        c.addView(Ui.spacer(act, 40))
        return Ui.scroll(act, c)
    }

    private fun titleCandidates(t0: String): List<String> {
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
        var t = 0
        for (s in p.scenes) {
            if (s.head.isBlank() && s.body.isBlank()) continue
            sb.append(Ui.mmss(t)).append(" ").append(s.head.ifBlank { "パート" }).append("\n")
            t += p.sceneSeconds(s)
        }
        return sb.toString().trimEnd()
    }

    private fun buildDesc(p: Project, title: String): String {
        val lead = p.scenes.firstOrNull { it.body.isNotBlank() }?.body?.take(60) ?: ""
        return buildString {
            append(title).append("\n\n")
            if (lead.isNotBlank()) append(lead).append("\n\n")
            append("■ チャプター\n")
            append(chapters(p)).append("\n\n")
            append("■ 関連動画\n\n")
            append("■ 使用機材・環境\n・スマホ1台\n\n")
            append("※内容は個人の見解です。\n")
            append("#").append(p.title.replace(" ", "").take(14))
        }
    }

    private fun buildTags(p: Project): String {
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
