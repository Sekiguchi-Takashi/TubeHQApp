package com.appathy.tubedesk

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/**
 * 昔のTVアニメ「次回予告」風。画角とレイアウトは固定、可変は背景と文字だけ。
 * 主題 = title（縦書き）／副題 = sub（下帯）／話数 = meta（左上）
 */
object Yokoku {

    private val SERIF_B: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)

    private const val BAR = 26f
    private const val TOP_SAFE = 156f
    private const val BOT_SAFE = 196f

    fun draw(c: Canvas, s: ImageSpec, w: Int, h: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        grade(c, p, w, h)
        scanlines(c, p, w, h)
        frame(c, p, w, h)
        tag(c, p, s, h)
        title(c, p, s, w, h)
        sub(c, p, s, w, h)
    }

    private fun grade(c: Canvas, p: Paint, w: Int, h: Int) {
        p.color = Color.argb(0x2E, 0xC8, 0x8A, 0x3C)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.color = Color.argb(0x4D, 0, 0, 0)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)

        p.shader = LinearGradient(
            w * 0.30f, 0f, w.toFloat(), 0f,
            Color.argb(0, 0, 0, 0), Color.argb(0xCC, 0, 0, 0), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null

        p.shader = LinearGradient(
            0f, h * 0.52f, 0f, h.toFloat(),
            Color.argb(0, 0, 0, 0), Color.argb(0xE6, 0, 0, 0), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, h * 0.52f, w.toFloat(), h.toFloat(), p)
        p.shader = null

        p.shader = LinearGradient(
            0f, 0f, 0f, h * 0.30f,
            Color.argb(0xA0, 0, 0, 0), Color.argb(0, 0, 0, 0), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w.toFloat(), h * 0.30f, p)
        p.shader = null
    }

    private fun scanlines(c: Canvas, p: Paint, w: Int, h: Int) {
        p.color = Color.argb(0x1F, 0, 0, 0)
        var y = 0f
        while (y < h) {
            c.drawRect(0f, y, w.toFloat(), y + 2f, p)
            y += 4f
        }
    }

    private fun frame(c: Canvas, p: Paint, w: Int, h: Int) {
        p.color = Color.BLACK
        c.drawRect(0f, 0f, w.toFloat(), BAR, p)
        c.drawRect(0f, h - BAR, w.toFloat(), h.toFloat(), p)
        p.color = Color.parseColor("#E8C25A")
        c.drawRect(0f, BAR, w.toFloat(), BAR + 3f, p)
        c.drawRect(0f, h - BAR - 3f, w.toFloat(), h - BAR, p)
    }

    private fun tag(c: Canvas, p: Paint, s: ImageSpec, h: Int) {
        val left = 52f
        val top = 58f
        val w = 196f
        val bh = 74f

        p.style = Paint.Style.FILL
        p.color = Color.argb(0x55, 0, 0, 0)
        c.drawRect(left + 7f, top + 7f, left + w + 7f, top + bh + 7f, p)
        p.color = s.accent
        c.drawRect(left, top, left + w, top + bh, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = Color.WHITE
        c.drawRect(left + 7f, top + 7f, left + w - 7f, top + bh - 7f, p)
        p.style = Paint.Style.FILL

        p.typeface = SERIF_B
        p.textSize = 46f
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        val fm = p.fontMetrics
        c.drawText("次回", left + w / 2f, top + bh / 2f - (fm.ascent + fm.descent) / 2f, p)

        p.textAlign = Paint.Align.LEFT
        if (s.meta.isNotBlank()) {
            p.textSize = 36f
            Frames.outline(c, p, s.meta, left + 6f, top + bh + 48f, Color.parseColor("#E8C25A"), Color.BLACK, 7f)
        }
        p.typeface = SERIF_B
    }

    private fun title(c: Canvas, p: Paint, s: ImageSpec, w: Int, h: Int) {
        val body = s.title.trim()
        if (body.isEmpty()) return
        val lines = body.split("\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return

        val availH = h - BOT_SAFE - TOP_SAFE
        val availW = w * 0.56f
        val right = w - 92f

        var size = 104f
        var cols: List<String> = emptyList()
        while (size > 34f) {
            val lineH = size * 1.04f
            val perCol = max(1, (availH / lineH).toInt())
            val tmp = chunk(lines, perCol)
            if (tmp.size * (size * 1.30f) <= availW && tmp.size <= 4) {
                cols = tmp
                break
            }
            size -= 4f
        }
        if (cols.isEmpty()) {
            val perCol = max(1, (availH / (size * 1.04f)).toInt())
            cols = chunk(lines, perCol).take(4)
        }

        p.typeface = SERIF_B
        p.textSize = size
        p.textAlign = Paint.Align.CENTER
        val colW = size * 1.30f
        val lineH = size * 1.04f

        for ((ci, col) in cols.withIndex()) {
            val x = right - colW / 2f - ci * colW
            var y = TOP_SAFE + size
            for (ch in col) {
                glyph(c, p, ch, x, y, size)
                y += lineH
            }
        }
        p.textAlign = Paint.Align.LEFT
    }

    private fun chunk(lines: List<String>, perCol: Int): List<String> {
        val tmp = mutableListOf<String>()
        for (l in lines) {
            var i = 0
            while (i < l.length) {
                tmp.add(l.substring(i, min(l.length, i + perCol)))
                i += perCol
            }
        }
        return tmp
    }

    private fun glyph(c: Canvas, p: Paint, ch: Char, x: Float, y: Float, size: Float) {
        val rotate = ch == 'ー' || ch == '－' || ch == '〜' || ch == '～' || ch == '—'
        val t = ch.toString()
        if (rotate) {
            c.save()
            c.rotate(90f, x, y - size * 0.35f)
            paint(c, p, t, x, y - size * 0.35f, size)
            c.restore()
        } else {
            paint(c, p, t, x, y, size)
        }
    }

    private fun paint(c: Canvas, p: Paint, t: String, x: Float, y: Float, size: Float) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(0xB0, 0x8B, 0x0A, 0x14)
        c.drawText(t, x + size * 0.055f, y + size * 0.055f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.20f
        p.strokeJoin = Paint.Join.ROUND
        p.color = Color.BLACK
        c.drawText(t, x, y, p)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        c.drawText(t, x, y, p)
    }

    private fun sub(c: Canvas, p: Paint, s: ImageSpec, w: Int, h: Int) {
        val body = s.sub.trim()
        if (body.isEmpty()) return

        p.typeface = SERIF_B
        p.textAlign = Paint.Align.LEFT
        val maxW = w - 130f

        var size = 52f
        var rows: List<String> = emptyList()
        while (size > 22f) {
            p.textSize = size
            val r = Frames.wrap(p, body, maxW)
            if (r.size <= 2) {
                rows = r
                break
            }
            size -= 2f
        }
        if (rows.isEmpty()) {
            p.textSize = size
            rows = Frames.wrap(p, body, maxW).take(2)
        }

        val lineH = size * 1.26f
        val blockH = rows.size * lineH
        val baseTop = h - BAR - 24f - blockH

        p.style = Paint.Style.FILL
        p.color = s.accent
        c.drawRect(52f, baseTop - size * 0.86f, 60f, baseTop + blockH - size * 0.18f, p)

        var y = baseTop
        for (r in rows) {
            Frames.outline(c, p, r, 78f, y, Color.parseColor("#E8C25A"), Color.BLACK, size * 0.22f)
            y += lineH
        }
    }
}
