package com.appathy.tubehq

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 昔のTVアニメ「次回予告」風サムネイル。
 * 画角とレイアウトは常に固定。差し替えるのは背景画像と文字だけ。
 */
object Yokoku {

    const val W = 1280
    const val H = 720

    private val SERIF_B: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)

    private const val BAR = 26f
    private const val TOP_SAFE = 156f
    private const val BOT_SAFE = 196f
    private const val TITLE_RIGHT = 1188f

    fun render(bg: Bitmap?, mainText: String, subText: String, epText: String): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        drawBackground(c, p, bg)
        drawGrade(c, p)
        drawScanlines(c, p)
        drawFrame(c, p)
        drawTag(c, p, epText)
        drawTitle(c, p, mainText)
        drawSub(c, p, subText)

        return bmp
    }

    private fun drawBackground(c: Canvas, p: Paint, bg: Bitmap?) {
        if (bg != null && !bg.isRecycled) {
            val s = max(W.toFloat() / bg.width, H.toFloat() / bg.height)
            val dw = bg.width * s
            val dh = bg.height * s
            val left = (W - dw) / 2f
            val top = (H - dh) / 2f
            c.drawBitmap(
                bg,
                Rect(0, 0, bg.width, bg.height),
                RectF(left, top, left + dw, top + dh),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
        } else {
            p.shader = LinearGradient(
                0f, 0f, W.toFloat(), H.toFloat(),
                Color.parseColor("#243449"), Color.parseColor("#07090C"),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
            p.shader = null
        }
    }

    /** 退色したフィルム風の色味と、文字を乗せる面の落とし込み */
    private fun drawGrade(c: Canvas, p: Paint) {
        p.color = Color.argb(0x2E, 0xC8, 0x8A, 0x3C)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)

        p.color = Color.argb(0x4D, 0, 0, 0)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)

        p.shader = LinearGradient(
            W * 0.30f, 0f, W.toFloat(), 0f,
            Color.argb(0, 0, 0, 0), Color.argb(0xCC, 0, 0, 0),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = null

        p.shader = LinearGradient(
            0f, H * 0.52f, 0f, H.toFloat(),
            Color.argb(0, 0, 0, 0), Color.argb(0xE6, 0, 0, 0),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, H * 0.52f, W.toFloat(), H.toFloat(), p)
        p.shader = null

        p.shader = LinearGradient(
            0f, 0f, 0f, H * 0.30f,
            Color.argb(0xA0, 0, 0, 0), Color.argb(0, 0, 0, 0),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, W.toFloat(), H * 0.30f, p)
        p.shader = null
    }

    private fun drawScanlines(c: Canvas, p: Paint) {
        p.color = Color.argb(0x1F, 0, 0, 0)
        var y = 0f
        while (y < H) {
            c.drawRect(0f, y, W.toFloat(), y + 2f, p)
            y += 4f
        }
    }

    /** 上下の黒帯＋細い金線。ここが毎回同じ「額縁」 */
    private fun drawFrame(c: Canvas, p: Paint) {
        p.color = Color.BLACK
        c.drawRect(0f, 0f, W.toFloat(), BAR, p)
        c.drawRect(0f, H - BAR, W.toFloat(), H.toFloat(), p)

        p.color = Ui.ACC
        c.drawRect(0f, BAR, W.toFloat(), BAR + 3f, p)
        c.drawRect(0f, H - BAR - 3f, W.toFloat(), H - BAR, p)
    }

    /** 左上の「次回」札と話数、右上のNEXT EPISODE */
    private fun drawTag(c: Canvas, p: Paint, epText: String) {
        val left = 52f
        val top = 58f
        val w = 196f
        val h = 74f

        p.style = Paint.Style.FILL
        p.color = Color.argb(0x55, 0, 0, 0)
        c.drawRect(left + 7f, top + 7f, left + w + 7f, top + h + 7f, p)

        p.color = Ui.RED
        c.drawRect(left, top, left + w, top + h, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = Color.WHITE
        c.drawRect(left + 7f, top + 7f, left + w - 7f, top + h - 7f, p)
        p.style = Paint.Style.FILL

        p.typeface = SERIF_B
        p.textSize = 46f
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        val fm = p.fontMetrics
        c.drawText("次回", left + w / 2f, top + h / 2f - (fm.ascent + fm.descent) / 2f, p)

        val ep = epText.trim()
        if (ep.isNotEmpty()) {
            p.textAlign = Paint.Align.LEFT
            p.textSize = 36f
            outlineText(c, p, ep, left + 6f, top + h + 48f, Ui.ACC, Color.BLACK, 7f)
        }

        p.textAlign = Paint.Align.RIGHT
        p.textSize = 24f
        p.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        outlineText(c, p, "N E X T   E P I S O D E", W - 52f, 96f, Ui.ACC, Color.BLACK, 5f)
        p.textAlign = Paint.Align.LEFT
        p.typeface = SERIF_B
    }

    /** 主題：右側に縦書き。列は右から左へ折り返す */
    private fun drawTitle(c: Canvas, p: Paint, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return

        val lines = body.split("\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return

        val availH = H - BOT_SAFE - TOP_SAFE
        val availW = W * 0.56f

        var size = 104f
        var cols: List<String> = emptyList()
        while (size > 34f) {
            val lineH = size * 1.04f
            val perCol = max(1, (availH / lineH).toInt())
            val tmp = mutableListOf<String>()
            for (l in lines) {
                var i = 0
                while (i < l.length) {
                    tmp.add(l.substring(i, min(l.length, i + perCol)))
                    i += perCol
                }
            }
            if (tmp.size * (size * 1.30f) <= availW && tmp.size <= 4) {
                cols = tmp
                break
            }
            size -= 4f
        }
        if (cols.isEmpty()) {
            val lineH = size * 1.04f
            val perCol = max(1, (availH / lineH).toInt())
            val tmp = mutableListOf<String>()
            for (l in lines) {
                var i = 0
                while (i < l.length) {
                    tmp.add(l.substring(i, min(l.length, i + perCol)))
                    i += perCol
                }
            }
            cols = tmp.take(4)
        }

        p.typeface = SERIF_B
        p.textSize = size
        p.textAlign = Paint.Align.CENTER

        val colW = size * 1.30f
        val lineH = size * 1.04f

        for ((ci, col) in cols.withIndex()) {
            val x = TITLE_RIGHT - colW / 2f - ci * colW
            var y = TOP_SAFE + size
            for (ch in col) {
                drawGlyph(c, p, ch, x, y, size)
                y += lineH
            }
        }
        p.textAlign = Paint.Align.LEFT
    }

    private fun drawGlyph(c: Canvas, p: Paint, ch: Char, x: Float, y: Float, size: Float) {
        val rotate = ch == 'ー' || ch == '－' || ch == '〜' || ch == '～' || ch == '—'
        val s = ch.toString()
        if (rotate) {
            c.save()
            c.rotate(90f, x, y - size * 0.35f)
            paintGlyph(c, p, s, x, y - size * 0.35f, size)
            c.restore()
        } else {
            paintGlyph(c, p, s, x, y, size)
        }
    }

    private fun paintGlyph(c: Canvas, p: Paint, s: String, x: Float, y: Float, size: Float) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(0xB0, 0x8B, 0x0A, 0x14)
        c.drawText(s, x + size * 0.055f, y + size * 0.055f, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.20f
        p.strokeJoin = Paint.Join.ROUND
        p.color = Color.BLACK
        c.drawText(s, x, y, p)

        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        c.drawText(s, x, y, p)
    }

    /** 副題：下の帯に横書き。最大2行で自動縮小 */
    private fun drawSub(c: Canvas, p: Paint, text: String) {
        val body = text.trim()
        if (body.isEmpty()) return

        p.typeface = SERIF_B
        p.textAlign = Paint.Align.LEFT

        val maxW = W - 130f
        var size = 52f
        var rows: List<String> = emptyList()
        while (size > 22f) {
            p.textSize = size
            val r = wrap(p, body, maxW)
            if (r.size <= 2) {
                rows = r
                break
            }
            size -= 2f
        }
        if (rows.isEmpty()) {
            p.textSize = size
            rows = wrap(p, body, maxW).take(2)
        }

        val lineH = size * 1.26f
        val blockH = rows.size * lineH
        val baseTop = H - BAR - 24f - blockH

        p.style = Paint.Style.FILL
        p.color = Ui.RED
        c.drawRect(52f, baseTop - size * 0.86f, 60f, baseTop + blockH - size * 0.18f, p)

        var y = baseTop
        for (r in rows) {
            outlineText(c, p, r, 78f, y, Ui.ACC, Color.BLACK, size * 0.22f)
            y += lineH
        }
    }

    private fun wrap(p: Paint, s: String, maxW: Float): List<String> {
        val out = mutableListOf<String>()
        var cur = StringBuilder()
        for (ch in s) {
            if (ch == '\n') {
                out.add(cur.toString()); cur = StringBuilder(); continue
            }
            cur.append(ch)
            if (p.measureText(cur.toString()) > maxW) {
                cur.deleteCharAt(cur.length - 1)
                out.add(cur.toString())
                cur = StringBuilder().append(ch)
            }
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    private fun outlineText(
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

    fun estimatedColumns(text: String): Int =
        ceil(text.replace("\n", "").length / 8.0).toInt()
}
