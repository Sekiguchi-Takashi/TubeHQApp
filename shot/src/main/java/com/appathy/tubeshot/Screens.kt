package com.appathy.tubeshot

import android.graphics.Bitmap
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout

object Screens {

    fun watch(e: EditText, cb: (String) -> Unit) {
        e.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = cb(s?.toString() ?: "")
        })
    }

    fun empty(act: MainActivity, msg: String): View {
        val c = Ui.col(act, 18)
        c.addView(Ui.title(act, "作品が選ばれていません"))
        c.addView(Ui.label(act, msg, true))
        c.addView(Ui.button(act, "作品一覧へ", true) { act.show(0) })
        return c
    }

    // ==================== 文字 ====================

    fun text(act: MainActivity): View {
        val s = act.editing ?: return empty(act, "作品タブから1枚選んでください。")
        val c = Ui.col(act, 14)

        val (pv, redraw) = act.previewView(s)
        c.addView(pv)

        fun field(label: String, hint: String, get: () -> String, set: (String) -> Unit, multi: Boolean = false) {
            c.addView(Ui.label(act, label, true))
            val e = Ui.edit(act, hint, multi)
            e.setText(get())
            watch(e) { set(it); act.store.save(); redraw() }
            c.addView(e)
        }

        when (s.style) {
            Shot.SHORTS -> {
                field("画面の文字", "動画に焼く大きな文字（改行可）", { s.title }, { s.title = it }, true)
                field("チャンネル名", "@channel", { s.channel }, { s.channel = it })
                field("説明", "下に出る一言（2行まで）", { s.sub }, { s.sub = it }, true)
                field("音源名", "♪ に続く曲名", { s.music }, { s.music = it })
                field("いいね数", "1.4万", { s.likes }, { s.likes = it })
                field("コメント数", "231", { s.comments }, { s.comments = it })
            }
            Shot.PLAYER -> {
                field("タイトル", "上に出る動画タイトル", { s.title }, { s.title = it }, true)
                field("チャンネル名", "@channel", { s.channel }, { s.channel = it })
                field("再生数・日付", "1.2万回視聴・3日前", { s.meta }, { s.meta = it })
                field("総再生時間", "8:42", { s.duration }, { s.duration = it })
            }
            Shot.THUMB -> {
                field("テロップ", "大きな文字（改行可）", { s.title }, { s.title = it }, true)
                field("サブ文字", "アクセント色で出る一言", { s.sub }, { s.sub = it })
                field("チャンネル名", "表示名", { s.channel }, { s.channel = it })
                field("尺バッジ", "8:42", { s.duration }, { s.duration = it })
            }
            else -> {
                field("主題（縦書き）", "改行で列が分かれます", { s.title }, { s.title = it }, true)
                field("副題", "下帯に出る一言", { s.sub }, { s.sub = it }, true)
                field("話数", "第12話", { s.meta }, { s.meta = it })
            }
        }

        c.addView(Ui.spacer(act, 6))
        c.addView(Ui.label(act, "文言候補（タップで主文に入れる）", true))
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        for (cand in Suggest.titles(s.title)) {
            box.addView(Ui.button(act, cand) {
                s.title = cand
                act.store.save()
                act.refresh()
            })
        }
        c.addView(box)

        c.addView(Ui.button(act, "ハッシュタグを作ってコピー") {
            act.copy(Suggest.tags(s), "ハッシュタグ")
        })

        c.addView(Ui.spacer(act, 40))
        return Ui.scroll(act, c)
    }

    // ==================== 見た目 ====================

    fun look(act: MainActivity): View {
        val s = act.editing ?: return empty(act, "作品タブから1枚選んでください。")
        val c = Ui.col(act, 14)

        val (pv, redraw) = act.previewView(s)
        c.addView(pv)

        c.addView(Ui.label(act, "アクセント色", true))
        val sw = LinearLayout(act)
        sw.orientation = LinearLayout.HORIZONTAL
        val colors = listOf(
            "#FF0033", "#FF7A00", "#FFC300", "#00B894",
            "#0A84FF", "#7B2FF2", "#FF4FA3", "#FFFFFF"
        )
        for (hex in colors) {
            val col = Color.parseColor(hex)
            sw.addView(Ui.swatch(act, col, col == s.accent) {
                s.accent = col
                act.store.save()
                act.refresh()
            })
        }
        c.addView(Ui.scrollH(act, sw))

        c.addView(Ui.spacer(act, 6))
        c.addView(Ui.slider(act, "明るさ", s.bright, -50, 50) { s.bright = it; act.store.save(); redraw() })
        c.addView(Ui.slider(act, "コントラスト", s.contrast, -50, 50) { s.contrast = it; act.store.save(); redraw() })
        c.addView(Ui.slider(act, "彩度", s.sat, -100, 100) { s.sat = it; act.store.save(); redraw() })
        c.addView(Ui.slider(act, "退色", s.fade, 0, 100) { s.fade = it; act.store.save(); redraw() })
        c.addView(Ui.slider(act, "ぼかし", s.blur, 0, 60) { s.blur = it; act.store.save(); redraw() })
        c.addView(Ui.slider(act, "再生位置 %", s.progress, 0, 100) { s.progress = it; act.store.save(); redraw() })

        c.addView(Ui.label(act, "再生ボタン（プレイヤー風・サムネ風のみ）", true))
        val pr = Ui.row(act)
        pr.addView(Ui.button(act, "表示", s.showPlay == 1) {
            s.showPlay = 1; act.store.save(); act.refresh()
        })
        pr.addView(Ui.button(act, "隠す", s.showPlay == 0) {
            s.showPlay = 0; act.store.save(); act.refresh()
        })
        c.addView(pr)

        c.addView(Ui.button(act, "効果をリセット") {
            s.bright = 0; s.contrast = 0; s.sat = 0; s.fade = 0; s.blur = 0
            act.store.save(); act.refresh()
        })

        c.addView(Ui.spacer(act, 40))
        return Ui.scroll(act, c)
    }

    // ==================== 書き出し ====================

    fun export(act: MainActivity): View {
        val s = act.editing ?: return empty(act, "作品タブから1枚選んでください。")
        val c = Ui.col(act, 14)

        val (pv, redraw) = act.previewView(s)
        c.addView(pv)

        val sz = Shot.size(s.style)
        c.addView(Ui.label(act, "${Shot.styleLabel(s.style)} ／ ${sz.first}×${sz.second} PNG", true))

        c.addView(Ui.button(act, "PNGで書き出す", true) {
            val base = s.name.ifBlank { s.title.ifBlank { "shot" } }
            val safe = base.replace(Regex("[^\\p{L}\\p{N}_-]"), "").take(16).ifBlank { "shot" }
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

        c.addView(Ui.button(act, "プレビューを作り直す") { redraw() })

        c.addView(Ui.spacer(act, 10))
        c.addView(Ui.label(act, "全様式を一気に見る", true))
        val all = LinearLayout(act)
        all.orientation = LinearLayout.VERTICAL
        c.addView(all)
        c.addView(Ui.button(act, "4様式を並べる") {
            all.removeAllViews()
            val keep = s.style
            for (st in Shot.STYLES) {
                s.style = st
                all.addView(Ui.label(act, Shot.styleLabel(st), true))
                val iv = android.widget.ImageView(act)
                iv.adjustViewBounds = true
                try {
                    iv.setImageBitmap(Frames.render(s, act.bgOf(s), 0.3f))
                } catch (e: Throwable) {
                }
                all.addView(iv, LinearLayout.LayoutParams(-1, -2))
            }
            s.style = keep
            act.store.save()
        })

        c.addView(Ui.spacer(act, 40))
        return Ui.scroll(act, c)
    }
}
