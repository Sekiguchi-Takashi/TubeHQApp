package com.appathy.tubecut

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max

/**
 * テロップPNGを Canvas で描く。ffmpeg の overlay で乗せる前提。
 * drawtext はフォント指定と改行の扱いが面倒なので使わない。
 *
 * 背景は透明。overlay の座標計算を単純にするため、
 * **動画と同じ解像度のキャンバス全面**に描き、位置決めもここで済ませる。
 */
object TelopDraw {

    private val SANS_B: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val SANS: Typeface = Typeface.SANS_SERIF

    fun render(t: Telop, w: Int, h: Int, accent: Int): Bitmap {
        val bmp = Bitmap.createBitmap(max(2, w), max(2, h), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        val text = t.text.trim()
        if (text.isEmpty()) return bmp

        val base = h * 0.062f
        var size = when (t.style) {
            "thin" -> base * 0.86f
            "band" -> base * 0.92f
            else -> base
        }

        val maxW = w * 0.86f
        var rows: List<String>
        while (true) {
            p.textSize = size
            p.typeface = if (t.style == "thin") SANS else SANS_B
            rows = wrap(p, text, maxW)
            if (rows.size <= 2 || size <= base * 0.5f) break
            size -= base * 0.06f
        }
        rows = rows.take(2)

        val lineH = size * 1.28f
        val blockH = rows.size * lineH

        val top = when (t.pos) {
            "top" -> h * 0.10f
            "center" -> (h - blockH) / 2f + size
            else -> h - h * 0.12f - blockH + size
        }

        if (t.style == "band") {
            var bw = 0f
            for (r in rows) bw = max(bw, p.measureText(r))
            val pad = size * 0.5f
            p.style = Paint.Style.FILL
            p.color = Color.argb(0xE0, Color.red(accent), Color.green(accent), Color.blue(accent))
            c.drawRoundRect(
                RectF(
                    (w - bw) / 2f - pad,
                    top - size - size * 0.28f,
                    (w + bw) / 2f + pad,
                    top - size + blockH + size * 0.28f
                ),
                size * 0.22f, size * 0.22f, p
            )
        }

        p.textAlign = Paint.Align.CENTER
        var y = top
        for (r in rows) {
            when (t.style) {
                "outline" -> {
                    stroked(c, p, r, w / 2f, y, Color.WHITE, Color.BLACK, size * 0.20f)
                }
                "band" -> {
                    p.style = Paint.Style.FILL
                    p.color = Color.WHITE
                    c.drawText(r, w / 2f, y, p)
                }
                "thin" -> {
                    p.setShadowLayer(size * 0.14f, 0f, size * 0.05f, Color.argb(0xCC, 0, 0, 0))
                    p.style = Paint.Style.FILL
                    p.color = Color.WHITE
                    c.drawText(r, w / 2f, y, p)
                    p.clearShadowLayer()
                }
                else -> {
                    stroked(c, p, r, w / 2f, y, Color.WHITE, Color.argb(0xE6, 0, 0, 0), size * 0.24f)
                }
            }
            y += lineH
        }
        p.textAlign = Paint.Align.LEFT
        return bmp
    }

    private fun stroked(
        c: Canvas, p: Paint, s: String, x: Float, y: Float,
        fill: Int, stroke: Int, w: Float
    ) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = w
        p.strokeJoin = Paint.Join.ROUND
        p.color = stroke
        c.drawText(s, x, y, p)
        p.style = Paint.Style.FILL
        p.color = fill
        c.drawText(s, x, y, p)
    }

    private fun wrap(p: Paint, s: String, maxW: Float): List<String> {
        val out = mutableListOf<String>()
        var cur = StringBuilder()
        for (ch in s) {
            if (ch == '\n') {
                out.add(cur.toString()); cur = StringBuilder(); continue
            }
            cur.append(ch)
            if (p.measureText(cur.toString()) > maxW && cur.length > 1) {
                cur.deleteCharAt(cur.length - 1)
                out.add(cur.toString())
                cur = StringBuilder().append(ch)
            }
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }
}
