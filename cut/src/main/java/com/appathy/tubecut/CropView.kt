package com.appathy.tubecut

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/**
 * 縦切り出しの枠を抽出フレームの上に重ねて見せる。
 * 枠の計算は EditProject.cropRect に集約してあり、ffmpeg と同じ値を使う。
 */
class CropView(ctx: Context) : View(ctx) {

    var frame: Bitmap? = null
    var srcW: Int = 1920
    var srcH: Int = 1080
    var crop: IntArray = intArrayOf(0, 0, 1080, 1080)

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val vw = width.toFloat()
        val vh = height.toFloat()
        p.color = Color.parseColor("#0A0E12")
        canvas.drawRect(0f, 0f, vw, vh, p)
        if (vw <= 0 || vh <= 0) return

        // 素材を画面に収める倍率
        val scale = min(vw / srcW, vh / srcH)
        val dw = srcW * scale
        val dh = srcH * scale
        val left = (vw - dw) / 2f
        val top = (vh - dh) / 2f

        val bmp = frame
        if (bmp != null && !bmp.isRecycled) {
            canvas.drawBitmap(
                bmp, Rect(0, 0, bmp.width, bmp.height),
                RectF(left, top, left + dw, top + dh),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
        } else {
            p.color = Color.parseColor("#1C2430")
            canvas.drawRect(left, top, left + dw, top + dh, p)
        }

        val cx = left + crop[0] * scale
        val cy = top + crop[1] * scale
        val cw = crop[2] * scale
        val ch = crop[3] * scale

        // 枠の外を暗く落とす
        p.color = Color.argb(0xB0, 0, 0, 0)
        canvas.drawRect(left, top, cx, top + dh, p)
        canvas.drawRect(cx + cw, top, left + dw, top + dh, p)

        // 枠
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = Ui.ACC
        canvas.drawRect(cx, cy, cx + cw, cy + ch, p)

        // 三分割の目安
        p.strokeWidth = 1.5f
        p.color = Color.argb(0x80, 0xFF, 0xFF, 0xFF)
        canvas.drawLine(cx + cw / 3f, cy, cx + cw / 3f, cy + ch, p)
        canvas.drawLine(cx + cw * 2 / 3f, cy, cx + cw * 2 / 3f, cy + ch, p)
        canvas.drawLine(cx, cy + ch / 3f, cx + cw, cy + ch / 3f, p)
        canvas.drawLine(cx, cy + ch * 2 / 3f, cx + cw, cy + ch * 2 / 3f, p)

        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        p.textSize = 28f
        canvas.drawText("${crop[2]}×${crop[3]} → 1080×1920", left + 12f, top + dh - 14f, p)
    }
}
