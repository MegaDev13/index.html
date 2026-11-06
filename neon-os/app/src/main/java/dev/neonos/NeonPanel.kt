package dev.neonos

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.util.concurrent.Executors

open class NeonPanel : Activity() {
    protected val preferences by lazy { getSharedPreferences("neon", MODE_PRIVATE) }
    protected val style get() = Themes.current(this)
    protected val worker = Executors.newSingleThreadExecutor()
    protected val ui = Handler(Looper.getMainLooper())
    protected lateinit var body: LinearLayout
    protected fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()

    protected fun text(value: String, size: Float = 15f) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(style.text)
        typeface = Typeface.create(style.font, Typeface.NORMAL)
        setPadding(dp(6), dp(7), dp(6), dp(7))
    }

    protected fun button(title: String, action: () -> Unit) = text(title, 14f).apply {
        gravity = Gravity.CENTER
        minHeight = dp(48)
        setTextColor(style.accent)
        background = style.background(this@NeonPanel, true)
        isFocusable = true
        setOnClickListener { action() }
    }

    protected fun add(view: View) {
        body.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    protected fun screen(title: String) {
        window.setDecorFitsSystemWindows(false)
        darkNavigation(window)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setBackgroundColor(Color.BLACK)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.BLACK)
            addView(body)
            setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, insets.getInsets(WindowInsets.Type.ime()).bottom))
                insets
            }
        }
        setContentView(scroll)
        add(text(title, 24f).apply { setTextColor(style.accent) })
        add(button("VOLTAR") { finish() })
    }

    protected fun message(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    protected fun whenAlive(action: () -> Unit) = ui.post { if (!isDestroyed && !isFinishing) action() }
    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
