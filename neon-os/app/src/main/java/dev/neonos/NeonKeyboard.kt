package dev.neonos

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Sends keystrokes to the focused editor without keeping a text buffer. */
class NeonKeyboard : InputMethodService() {
    private lateinit var keys: LinearLayout
    private var shifted = false
    private var symbols = false
    private var numeric = false
    private var alternates = emptyList<String>()
    private val style get() = Themes.current(this)
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(3), dp(6), dp(3), dp(6))
        keys = this
        setOnApplyWindowInsetsListener { _, insets ->
            val safe = insets.getInsetsIgnoringVisibility(
                WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
            setPadding(dp(3) + safe.left, dp(6), dp(3) + safe.right, dp(6))
            insets
        }
        render()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val kind = (info?.inputType ?: InputType.TYPE_CLASS_TEXT) and InputType.TYPE_MASK_CLASS
        numeric = kind in listOf(InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME)
        symbols = numeric
        shifted = false
        alternates = emptyList()
        if (::keys.isInitialized) render()
    }

    private fun render() {
        keys.removeAllViews()
        keys.setBackgroundColor(Color.BLACK)
        if (alternates.isNotEmpty()) {
            val choices = row()
            for (value in alternates) key(choices, value) { insert(value) }
            key(choices, "×", "Fechar acentos") { alternates = emptyList(); render() }
        }
        val rows = when {
            numeric -> listOf("123", "456", "789", "+0-.,")
            symbols -> listOf("1234567890", "@#$%&*()-", "+=/:;!?_")
            else -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        }
        for ((index, letters) in rows.withIndex()) {
            val line = row()
            if (index == rows.lastIndex && !symbols) {
                key(line, "⇧", "Maiúsculas", 1.25f) { shifted = !shifted; render() }
            }
            for (letter in letters) {
                val value = if (shifted) letter.uppercaseChar().toString() else letter.toString()
                key(line, value) { insert(value) }.setOnLongClickListener {
                    val variants = ACCENTS[letter.lowercaseChar()] ?: return@setOnLongClickListener false
                    alternates = variants.map { if (shifted) it.uppercase(Locale.ROOT) else it }
                    render()
                    true
                }
            }
            if (index == rows.lastIndex) key(line, "⌫", "Apagar", 1.25f) {
                currentInputConnection?.let {
                    if (!it.deleteSurroundingTextInCodePoints(1, 0)) it.deleteSurroundingText(1, 0)
                }
            }
        }
        val bottom = row()
        key(bottom, "◎", "Trocar teclado") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
        if (!numeric) key(bottom, if (symbols) "ABC" else "123", "Letras ou símbolos") {
            symbols = !symbols
            alternates = emptyList()
            render()
        }
        key(bottom, ",") { insert(",") }
        key(bottom, "ESPAÇO", "Espaço", 3.5f) { insert(" ") }
        key(bottom, ".") { insert(".") }
        key(bottom, "↵", "Enter", 1.25f) {
            val info = currentInputEditorInfo
            if (info != null && info.actionId != 0 && info.actionLabel != null) {
                currentInputConnection?.performEditorAction(info.actionId)
            } else if (!sendDefaultEditorAction(true)) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        // The IME framework can consume navigation insets before they reach its input view.
        // A measured footer keeps the last row above either navigation mode from the first layout.
        keys.addView(android.widget.Space(this), LinearLayout.LayoutParams(-1, dp(52)))
    }

    private fun row() = LinearLayout(this).also { keys.addView(it, LinearLayout.LayoutParams(-1, -2)) }

    private fun key(row: LinearLayout, caption: String, description: String = caption, weight: Float = 1f,
                    action: () -> Unit): TextView {
        val spec = style
        val key = TextView(this).apply {
            text = caption
            contentDescription = description
            gravity = Gravity.CENTER
            typeface = Typeface.MONOSPACE
            textSize = if (caption.length > 3) 12f else 18f
            setTextColor(spec.accent)
            background = RippleDrawable(ColorStateList.valueOf(spec.stroke), spec.background(this@NeonKeyboard, true), null)
            isFocusable = true
            setOnClickListener { action() }
        }
        val height = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 39 else 47
        row.addView(key, LinearLayout.LayoutParams(0, dp(height), weight).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        })
        return key
    }

    private fun insert(value: String) {
        currentInputConnection?.commitText(value, 1)
        if (shifted || alternates.isNotEmpty()) {
            shifted = false
            alternates = emptyList()
            render()
        }
    }

    private companion object {
        val ACCENTS = mapOf(
            'a' to listOf("á", "à", "â", "ã"), 'e' to listOf("é", "ê"), 'i' to listOf("í"),
            'o' to listOf("ó", "ô", "õ"), 'u' to listOf("ú", "ü"), 'c' to listOf("ç")
        )
    }
}
