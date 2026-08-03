package com.appathy.tubehq

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

object Ui {

    val BG = Color.parseColor("#0E1116")
    val CARD = Color.parseColor("#182029")
    val LINE = Color.parseColor("#2C3644")
    val TXT = Color.parseColor("#E8EDF2")
    val SUB = Color.parseColor("#8A97A6")
    val ACC = Color.parseColor("#E8C25A")
    val RED = Color.parseColor("#C1121F")

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun lp(w: Int, h: Int, weight: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight)

    fun col(c: Context, padDp: Int = 0): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        val p = dp(c, padDp)
        l.setPadding(p, p, p, p)
        return l
    }

    fun row(c: Context): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.HORIZONTAL
        l.gravity = Gravity.CENTER_VERTICAL
        return l
    }

    fun title(c: Context, s: String): TextView {
        val t = TextView(c)
        t.text = s
        t.setTextColor(TXT)
        t.textSize = 20f
        t.typeface = Typeface.DEFAULT_BOLD
        t.setPadding(0, dp(c, 6), 0, dp(c, 10))
        return t
    }

    fun label(c: Context, s: String, small: Boolean = false): TextView {
        val t = TextView(c)
        t.text = s
        t.setTextColor(if (small) SUB else TXT)
        t.textSize = if (small) 12f else 15f
        t.setPadding(0, dp(c, 4), 0, dp(c, 4))
        return t
    }

    fun edit(c: Context, hint: String, multi: Boolean = false, lines: Int = 3): EditText {
        val e = EditText(c)
        e.hint = hint
        e.setHintTextColor(SUB)
        e.setTextColor(TXT)
        e.textSize = 15f
        e.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10))
        val g = GradientDrawable()
        g.setColor(Color.parseColor("#111820"))
        g.setStroke(dp(c, 1), LINE)
        g.cornerRadius = dp(c, 8).toFloat()
        e.background = g
        if (multi) {
            e.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            e.gravity = Gravity.TOP
            e.minLines = lines
            e.isSingleLine = false
        } else {
            e.inputType = InputType.TYPE_CLASS_TEXT
            e.isSingleLine = true
        }
        val p = LinearLayout.LayoutParams(-1, -2)
        p.topMargin = dp(c, 4)
        p.bottomMargin = dp(c, 8)
        e.layoutParams = p
        return e
    }

    fun button(c: Context, s: String, accent: Boolean = false, cb: () -> Unit): Button {
        val b = Button(c)
        b.text = s
        b.isAllCaps = false
        b.textSize = 14f
        b.setTextColor(if (accent) Color.parseColor("#101418") else TXT)
        val g = GradientDrawable()
        g.setColor(if (accent) ACC else Color.parseColor("#222C38"))
        g.cornerRadius = dp(c, 8).toFloat()
        b.background = g
        b.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8))
        b.minHeight = dp(c, 44)
        b.setOnClickListener { cb() }
        val p = LinearLayout.LayoutParams(-2, -2)
        p.rightMargin = dp(c, 8)
        p.topMargin = dp(c, 4)
        p.bottomMargin = dp(c, 4)
        b.layoutParams = p
        return b
    }

    fun card(c: Context): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        val g = GradientDrawable()
        g.setColor(CARD)
        g.setStroke(dp(c, 1), LINE)
        g.cornerRadius = dp(c, 12).toFloat()
        l.background = g
        val p = dp(c, 14)
        l.setPadding(p, p, p, p)
        val mp = LinearLayout.LayoutParams(-1, -2)
        mp.bottomMargin = dp(c, 10)
        l.layoutParams = mp
        return l
    }

    fun chip(c: Context, s: String, color: Int): TextView {
        val t = TextView(c)
        t.text = s
        t.setTextColor(Color.parseColor("#101418"))
        t.textSize = 11f
        t.typeface = Typeface.DEFAULT_BOLD
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(c, 20).toFloat()
        t.background = g
        t.setPadding(dp(c, 10), dp(c, 3), dp(c, 10), dp(c, 3))
        val p = LinearLayout.LayoutParams(-2, -2)
        p.rightMargin = dp(c, 6)
        t.layoutParams = p
        return t
    }

    fun scroll(c: Context, inner: View): ScrollView {
        val s = ScrollView(c)
        s.isFillViewport = true
        s.addView(inner, ViewGroup.LayoutParams(-1, -2))
        return s
    }

    fun scrollH(c: Context, inner: View): HorizontalScrollView {
        val s = HorizontalScrollView(c)
        s.isHorizontalScrollBarEnabled = false
        s.addView(inner, ViewGroup.LayoutParams(-2, -2))
        return s
    }

    fun spacer(c: Context, h: Int): View {
        val v = View(c)
        v.layoutParams = LinearLayout.LayoutParams(-1, dp(c, h))
        return v
    }

    fun mmss(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return String.format("%02d:%02d", m, s)
    }
}
