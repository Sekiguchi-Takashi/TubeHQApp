package com.appathy.tubedesk

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/**
 * YouTube風の「動画っぽい1枚」を描く。
 * レイアウトは様式ごとに固定。差し替えるのは背景と文字だけ。
 *
 * 注意: YouTubeのロゴ・ワードマークは一切描かない（商標）。
 * 赤いシーク、丸ノブ、三角の再生記号といった一般的な意匠のみ。
 */
object Frames {

    private val SANS_B: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val SANS: Typeface = Typeface.SANS_SERIF

    fun render(shot: ImageSpec, bg: Bitmap?, scale: Float): Bitmap {
        val (w, h) = ImageSpec.size(shot.style)
        val bw = max(1, (w * scale).toInt())
        val bh = max(1, (h * scale).toInt())
        val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.scale(bw / w.toFloat(), bh / h.toFloat())

        drawBase(c, shot, bg, w, h)

        when (shot.style) {
            ImageSpec.SHORTS -> shorts(c, shot, w, h)
            ImageSpec.PLAYER -> player(c, shot, w, h)
            ImageSpec.THUMB -> thumb(c, shot, w, h)
            else -> Yokoku.draw(c, shot, w, h)
        }
        return out
    }

    // ---------------- 背景とグレーディング ----------------

    private fun drawBase(c: Canvas, s: ImageSpec, bg: Bitmap?, w: Int, h: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (bg != null && !bg.isRecycled) {
            val src = if (s.blur > 0) shrink(bg, s.blur) else bg
            val sc = max(w.toFloat() / src.width, h.toFloat() / src.height)
            val dw = src.width * sc
            val dh = src.height * sc
            val left = (w - dw) / 2f
            val top = (h - dh) / 2f
            val ip = Paint(Paint.FILTER_BITMAP_FLAG)
            ip.colorFilter = ColorMatrixColorFilter(grade(s))
            c.drawBitmap(src, Rect(0, 0, src.width, src.height),
                RectF(left, top, left + dw, top + dh), ip)
        } else {
            p.shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                Color.parseColor("#2B3440"), Color.parseColor("#0A0C10"),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = null
        }
        if (s.fade > 0) {
            p.color = Color.argb((s.fade * 1.5f).toInt().coerceIn(0, 200), 0xD6, 0xBE, 0x99)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
    }

    private fun grade(s: ImageSpec): ColorMatrix {
        val cm = ColorMatrix()
        cm.setSaturation((1f + s.sat / 100f).coerceAtLeast(0f))
        val ct = 1f + s.contrast / 100f
        val br = s.bright * 1.3f
        val t = 128f * (1f - ct) + br
        cm.postConcat(
            ColorMatrix(
                floatArrayOf(
                    ct, 0f, 0f, 0f, t,
                    0f, ct, 0f, 0f, t,
                    0f, 0f, ct, 0f, t,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        return cm
    }

    /** 縮小してから拡大描画させることでぼかしの代わりにする */
    private fun shrink(src: Bitmap, amount: Int): Bitmap {
        val f = 1 + amount / 7
        val w = max(2, src.width / f)
        val h = max(2, src.height / f)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun scrim(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, a0: Int, a1: Int) {
        val p = Paint()
        p.shader = LinearGradient(
            x0, y0, x1, y1,
            Color.argb(a0, 0, 0, 0), Color.argb(a1, 0, 0, 0), Shader.TileMode.CLAMP
        )
        c.drawRect(min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1), p)
    }

    // ---------------- ショート風 1080x1920 ----------------

    private fun shorts(c: Canvas, s: ImageSpec, w: Int, h: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        scrim(c, 0f, 0f, 0f, 260f, 0x99, 0x00)
        scrim(c, 0f, 1120f, 0f, h.toFloat(), 0x00, 0xE6)

        p.typeface = SANS_B
        p.textAlign = Paint.Align.CENTER
        p.textSize = 44f
        p.color = Color.WHITE
        p.setShadowLayer(6f, 0f, 2f, Color.argb(0xAA, 0, 0, 0))
        c.drawText("ショート", w / 2f, 130f, p)
        p.clearShadowLayer()

        if (s.title.isNotBlank()) {
            p.textAlign = Paint.Align.CENTER
            var size = 96f
            var rows: List<String>
            while (true) {
                p.textSize = size
                rows = wrap(p, s.title, w - 260f)
                if (rows.size <= 3 || size <= 44f) break
                size -= 4f
            }
            var y = 420f
            for (r in rows) {
                outline(c, p, r, w / 2f, y, Color.WHITE, Color.argb(0xCC, 0, 0, 0), size * 0.18f)
                y += size * 1.2f
            }
        }

        val rx = w - 96f
        avatar(c, p, rx, 1120f, 52f, s.accent)
        heart(c, p, rx, 1300f, 46f, Color.WHITE)
        railText(c, p, s.likes, rx, 1372f)
        bubble(c, p, rx, 1470f, 46f)
        railText(c, p, s.comments, rx, 1542f)
        shareArrow(c, p, rx, 1630f, 44f)
        railText(c, p, "共有", rx, 1702f)
        disc(c, p, rx, 1790f, 42f)

        p.textAlign = Paint.Align.LEFT
        p.typeface = SANS_B
        p.textSize = 46f
        p.setShadowLayer(8f, 0f, 3f, Color.argb(0xCC, 0, 0, 0))
        p.color = Color.WHITE
        val ch = if (s.channel.isBlank()) "" else
            if (s.channel.startsWith("@")) s.channel else "@" + s.channel
        if (ch.isNotBlank()) c.drawText(ch, 48f, 1590f, p)

        p.typeface = SANS
        p.textSize = 40f
        var y = 1656f
        for (r in wrap(p, s.sub, 780f).take(2)) {
            c.drawText(r, 48f, y, p)
            y += 52f
        }

        if (s.music.isNotBlank()) {
            p.textSize = 36f
            c.drawText("♪  " + s.music, 48f, y + 16f, p)
        }
        p.clearShadowLayer()

        p.color = Color.argb(0x59, 0xFF, 0xFF, 0xFF)
        c.drawRect(0f, h - 44f, w.toFloat(), h - 38f, p)
        p.color = s.accent
        c.drawRect(0f, h - 44f, w * s.progress / 100f, h - 38f, p)
    }

    private fun railText(c: Canvas, p: Paint, t: String, x: Float, y: Float) {
        if (t.isBlank()) return
        p.typeface = SANS_B
        p.textAlign = Paint.Align.CENTER
        p.textSize = 34f
        p.style = Paint.Style.FILL
        p.setShadowLayer(6f, 0f, 2f, Color.argb(0xAA, 0, 0, 0))
        p.color = Color.WHITE
        c.drawText(t, x, y, p)
        p.clearShadowLayer()
    }

    // ---------------- プレイヤー風 1920x1080 ----------------

    private fun player(c: Canvas, s: ImageSpec, w: Int, h: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        scrim(c, 0f, 0f, 0f, 300f, 0xB3, 0x00)
        scrim(c, 0f, 760f, 0f, h.toFloat(), 0x00, 0xE6)

        p.textAlign = Paint.Align.LEFT
        p.typeface = SANS_B
        p.setShadowLayer(10f, 0f, 4f, Color.argb(0xCC, 0, 0, 0))
        p.color = Color.WHITE

        var size = 66f
        var rows: List<String>
        while (true) {
            p.textSize = size
            rows = wrap(p, s.title, w - 260f)
            if (rows.size <= 2 || size <= 40f) break
            size -= 3f
        }
        var y = 116f
        for (r in rows.take(2)) {
            c.drawText(r, 60f, y, p)
            y += size * 1.18f
        }

        p.typeface = SANS
        p.textSize = 40f
        p.color = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val ch = if (s.channel.startsWith("@") || s.channel.isBlank()) s.channel else "@" + s.channel
        if (ch.isNotBlank()) {
            c.drawText(ch, 60f, y + 8f, p)
            y += 56f
        }
        if (s.meta.isNotBlank()) {
            p.textSize = 34f
            p.color = Color.argb(0xB3, 0xFF, 0xFF, 0xFF)
            c.drawText(s.meta, 60f, y + 8f, p)
        }
        p.clearShadowLayer()

        if (s.showPlay == 1) {
            val cx = w / 2f
            val cy = h / 2f - 40f
            p.color = Color.argb(0xE6, Color.red(s.accent), Color.green(s.accent), Color.blue(s.accent))
            c.drawRoundRect(RectF(cx - 96f, cy - 66f, cx + 96f, cy + 66f), 30f, 30f, p)
            p.color = Color.WHITE
            c.drawPath(triangle(cx - 24f, cy, 52f), p)
        }

        val left = 60f
        val right = w - 60f
        val by = 928f
        p.color = Color.argb(0x4D, 0xFF, 0xFF, 0xFF)
        c.drawRect(left, by, right, by + 7f, p)
        p.color = Color.argb(0x8C, 0xFF, 0xFF, 0xFF)
        c.drawRect(left, by, left + (right - left) * min(1f, s.progress / 100f + 0.18f), by + 7f, p)
        val px = left + (right - left) * (s.progress / 100f)
        p.color = s.accent
        c.drawRect(left, by, px, by + 7f, p)
        c.drawCircle(px, by + 3.5f, 18f, p)

        val iy = 1002f
        p.color = Color.WHITE
        c.drawPath(triangle(66f, iy, 34f), p)
        c.drawPath(triangle(150f, iy, 30f), p)
        c.drawRect(184f, iy - 30f, 192f, iy + 30f, p)
        volume(c, p, 240f, iy)

        p.typeface = SANS
        p.textAlign = Paint.Align.LEFT
        p.textSize = 34f
        c.drawText(timeText(s), 330f, iy + 12f, p)

        cc(c, p, w - 330f, iy)
        gear(c, p, w - 232f, iy)
        c.drawRect(w - 158f, iy - 26f, w - 82f, iy - 18f, p)
        c.drawRect(w - 158f, iy + 18f, w - 82f, iy + 26f, p)
        c.drawRect(w - 158f, iy - 26f, w - 150f, iy + 26f, p)
        c.drawRect(w - 90f, iy - 26f, w - 82f, iy + 26f, p)
        fullscreen(c, p, w - 40f, iy)
    }

    private fun timeText(s: ImageSpec): String {
        val total = parseSec(s.duration)
        if (total <= 0) return if (s.duration.isBlank()) "" else s.duration
        val cur = (total * s.progress / 100f).toInt()
        return fmt(cur) + " / " + fmt(total)
    }

    private fun parseSec(t: String): Int {
        val parts = t.trim().split(":")
        return try {
            when (parts.size) {
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                else -> -1
            }
        } catch (e: Exception) {
            -1
        }
    }

    private fun fmt(sec: Int): String = String.format("%d:%02d", sec / 60, sec % 60)

    // ---------------- サムネ風 1280x720 ----------------

    private fun thumb(c: Canvas, s: ImageSpec, w: Int, h: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        scrim(c, 0f, h * 0.30f, 0f, h.toFloat(), 0x00, 0xD9)
        scrim(c, 0f, 0f, w * 0.62f, 0f, 0x99, 0x00)

        p.textAlign = Paint.Align.LEFT
        p.typeface = SANS_B

        var size = 118f
        var rows: List<String>
        while (true) {
            p.textSize = size
            rows = wrap(p, s.title, 900f)
            if (rows.size <= 3 || size <= 52f) break
            size -= 4f
        }

        val lineH = size * 1.16f
        var y = h - 220f - (rows.size - 1) * lineH
        for (r in rows) {
            outline(c, p, r, 64f, y, Color.WHITE, Color.BLACK, size * 0.19f)
            y += lineH
        }

        p.style = Paint.Style.FILL
        p.color = s.accent
        c.drawRect(64f, y - size * 0.72f, 64f + min(560f, p.measureText(rows.lastOrNull() ?: "")), y - size * 0.62f + 12f, p)

        if (s.sub.isNotBlank()) {
            p.typeface = SANS_B
            p.textSize = 46f
            outline(c, p, wrap(p, s.sub, 940f).first(), 64f, h - 118f, s.accent, Color.BLACK, 10f)
        }

        if (s.channel.isNotBlank()) {
            p.typeface = SANS
            p.textSize = 32f
            p.style = Paint.Style.FILL
            p.color = Color.argb(0xCC, 0xFF, 0xFF, 0xFF)
            c.drawText(s.channel, 64f, h - 56f, p)
        }

        if (s.duration.isNotBlank()) {
            p.typeface = SANS_B
            p.textSize = 34f
            val tw = p.measureText(s.duration)
            p.color = Color.argb(0xD9, 0, 0, 0)
            c.drawRoundRect(
                RectF(w - 44f - tw - 28f, h - 92f, w - 44f, h - 36f), 8f, 8f, p
            )
            p.color = Color.WHITE
            c.drawText(s.duration, w - 44f - tw - 14f, h - 52f, p)
        }

        if (s.showPlay == 1) {
            val cx = w - 190f
            val cy = h / 2f - 60f
            p.color = Color.argb(0x99, 0, 0, 0)
            c.drawCircle(cx, cy, 74f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 6f
            p.color = Color.WHITE
            c.drawCircle(cx, cy, 74f, p)
            p.style = Paint.Style.FILL
            c.drawPath(triangle(cx - 20f, cy, 44f), p)
        }
    }

    // ---------------- 部品 ----------------

    private fun triangle(x: Float, cy: Float, sz: Float): Path {
        val p = Path()
        p.moveTo(x, cy - sz)
        p.lineTo(x + sz * 1.5f, cy)
        p.lineTo(x, cy + sz)
        p.close()
        return p
    }

    private fun avatar(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, accent: Int) {
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#5A6470")
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.color = Color.WHITE
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.FILL
        p.color = accent
        c.drawCircle(cx, cy + r + 4f, 22f, p)
        p.color = Color.WHITE
        c.drawRect(cx - 11f, cy + r - 0.5f, cx + 11f, cy + r + 8.5f, p)
        c.drawRect(cx - 4.5f, cy + r - 7f, cx + 4.5f, cy + r + 15f, p)
    }

    private fun heart(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, color: Int) {
        val path = Path()
        path.moveTo(cx, cy + r * 0.82f)
        path.cubicTo(cx - r * 1.45f, cy - r * 0.25f, cx - r * 0.52f, cy - r * 1.15f, cx, cy - r * 0.32f)
        path.cubicTo(cx + r * 0.52f, cy - r * 1.15f, cx + r * 1.45f, cy - r * 0.25f, cx, cy + r * 0.82f)
        path.close()
        p.style = Paint.Style.FILL
        p.color = Color.argb(0x66, 0, 0, 0)
        c.save()
        c.translate(3f, 4f)
        c.drawPath(path, p)
        c.restore()
        p.color = color
        c.drawPath(path, p)
    }

    private fun bubble(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        c.drawRoundRect(RectF(cx - r, cy - r * 0.85f, cx + r, cy + r * 0.45f), r * 0.35f, r * 0.35f, p)
        val tail = Path()
        tail.moveTo(cx - r * 0.55f, cy + r * 0.4f)
        tail.lineTo(cx - r * 0.15f, cy + r * 0.4f)
        tail.lineTo(cx - r * 0.62f, cy + r * 0.95f)
        tail.close()
        c.drawPath(tail, p)
    }

    private fun shareArrow(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.22f
        p.strokeCap = Paint.Cap.ROUND
        p.color = Color.WHITE
        val path = Path()
        path.moveTo(cx - r, cy + r * 0.55f)
        path.quadTo(cx - r * 0.15f, cy + r * 0.35f, cx + r * 0.28f, cy - r * 0.42f)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
        val head = Path()
        head.moveTo(cx + r * 0.95f, cy - r * 0.58f)
        head.lineTo(cx + r * 0.05f, cy - r * 0.86f)
        head.lineTo(cx + r * 0.30f, cy - r * 0.05f)
        head.close()
        c.drawPath(head, p)
    }

    private fun disc(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#2A2F36")
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.color = Color.WHITE
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        c.drawCircle(cx - r * 0.28f, cy + r * 0.28f, r * 0.2f, p)
        c.drawRect(cx - r * 0.12f, cy - r * 0.5f, cx - r * 0.05f, cy + r * 0.3f, p)
        c.drawRect(cx - r * 0.12f, cy - r * 0.55f, cx + r * 0.35f, cy - r * 0.4f, p)
    }

    private fun volume(c: Canvas, p: Paint, cx: Float, cy: Float) {
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        val body = Path()
        body.moveTo(cx - 22f, cy - 10f)
        body.lineTo(cx - 8f, cy - 10f)
        body.lineTo(cx + 6f, cy - 26f)
        body.lineTo(cx + 6f, cy + 26f)
        body.lineTo(cx - 8f, cy + 10f)
        body.lineTo(cx - 22f, cy + 10f)
        body.close()
        c.drawPath(body, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        c.drawArc(RectF(cx - 2f, cy - 22f, cx + 30f, cy + 22f), -55f, 110f, false, p)
        c.drawArc(RectF(cx - 6f, cy - 34f, cx + 44f, cy + 34f), -55f, 110f, false, p)
        p.style = Paint.Style.FILL
    }

    private fun cc(c: Canvas, p: Paint, cx: Float, cy: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.color = Color.WHITE
        c.drawRoundRect(RectF(cx - 34f, cy - 24f, cx + 34f, cy + 24f), 8f, 8f, p)
        p.style = Paint.Style.FILL
        c.drawRect(cx - 22f, cy - 4f, cx - 2f, cy + 4f, p)
        c.drawRect(cx + 4f, cy - 4f, cx + 24f, cy + 4f, p)
    }

    private fun gear(c: Canvas, p: Paint, cx: Float, cy: Float) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 6f
        p.color = Color.WHITE
        c.drawCircle(cx, cy, 22f, p)
        p.style = Paint.Style.FILL
        for (i in 0 until 8) {
            c.save()
            c.rotate(i * 45f, cx, cy)
            c.drawRect(cx - 4f, cy - 32f, cx + 4f, cy - 22f, p)
            c.restore()
        }
    }

    private fun fullscreen(c: Canvas, p: Paint, cx: Float, cy: Float) {
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        val a = 26f
        val t = 6f
        val pts = listOf(
            Pair(-1f, -1f), Pair(1f, -1f), Pair(-1f, 1f), Pair(1f, 1f)
        )
        for ((sx, sy) in pts) {
            val x = cx + sx * a
            val y = cy + sy * a
            c.drawRect(min(x, x - sx * 16f), y - t / 2f, max(x, x - sx * 16f), y + t / 2f, p)
            c.drawRect(x - t / 2f, min(y, y - sy * 16f), x + t / 2f, max(y, y - sy * 16f), p)
        }
    }

    // ---------------- テキスト ----------------

    fun wrap(p: Paint, s: String, maxW: Float): List<String> {
        if (s.isBlank()) return listOf()
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

    fun outline(
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
}
