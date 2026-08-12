package com.appathy.tubedesk

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class PrompterActivity : Activity() {

    private lateinit var sv: ScrollView
    private lateinit var text: TextView
    private lateinit var info: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var speed = 1.4f
    private var fontSize = 30f
    private var acc = 0f

    private val ticker = object : Runnable {
        override fun run() {
            if (running) {
                acc += speed
                if (acc >= 1f) {
                    val d = acc.toInt()
                    acc -= d
                    sv.scrollBy(0, d)
                }
                handler.postDelayed(this, 16)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val store = Store(this)
        val id = intent.getStringExtra("id")
        val p = store.projects.firstOrNull { it.id == id }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.BLACK)

        sv = ScrollView(this)
        text = TextView(this)
        text.setTextColor(Color.WHITE)
        text.textSize = fontSize
        text.setLineSpacing(0f, 1.5f)
        text.typeface = Typeface.DEFAULT_BOLD
        val pad = Ui.dp(this, 20)
        text.setPadding(pad, Ui.dp(this, 220), pad, Ui.dp(this, 600))
        text.text = build(p)
        sv.addView(text)
        root.addView(sv, Ui.lp(-1, 0, 1f))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.setBackgroundColor(Color.parseColor("#101418"))
        bar.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 10))

        info = TextView(this)
        info.setTextColor(Ui.SUB)
        info.textSize = 12f
        info.gravity = Gravity.CENTER
        bar.addView(info)

        val row = Ui.row(this)
        row.gravity = Gravity.CENTER
        row.addView(Ui.button(this, "速−") { speed = Math.max(0.2f, speed - 0.2f); updateInfo() })
        row.addView(Ui.button(this, "再生", true) { toggle() })
        row.addView(Ui.button(this, "速＋") { speed = Math.min(6f, speed + 0.2f); updateInfo() })
        row.addView(Ui.button(this, "字−") { fontSize = Math.max(16f, fontSize - 2f); text.textSize = fontSize; updateInfo() })
        row.addView(Ui.button(this, "字＋") { fontSize = Math.min(64f, fontSize + 2f); text.textSize = fontSize; updateInfo() })
        row.addView(Ui.button(this, "頭出し") { sv.scrollTo(0, 0) })
        bar.addView(Ui.scrollH(this, row))

        root.addView(bar, Ui.lp(-1, -2))

        sv.setOnClickListener { toggle() }
        text.setOnClickListener { toggle() }

        setContentView(root)
        updateInfo()
    }

    private fun build(p: Project?): String {
        if (p == null) return "台本が見つかりません"
        val sb = StringBuilder()
        sb.append(p.title).append("\n\n")
        for (s in p.scenes) {
            if (s.head.isNotBlank()) sb.append("― ").append(s.head).append(" ―\n")
            if (s.note.isNotBlank()) sb.append("（").append(s.note).append("）\n")
            sb.append(s.body).append("\n\n")
        }
        return sb.toString()
    }

    private fun toggle() {
        running = !running
        if (running) handler.post(ticker) else handler.removeCallbacks(ticker)
        updateInfo()
    }

    private fun updateInfo() {
        info.text = (if (running) "▶ 再生中" else "⏸ 停止中") +
            "　速度 ${String.format("%.1f", speed)}　字 ${fontSize.toInt()}　（画面タップで停止／再開）"
    }

    override fun onPause() {
        super.onPause()
        running = false
        handler.removeCallbacks(ticker)
    }
}
