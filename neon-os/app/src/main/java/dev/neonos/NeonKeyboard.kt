package dev.neonos

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.ClipboardManager
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

class NeonKeyboard : InputMethodService() {
    private lateinit var frame: FrameLayout
    private lateinit var keys: LinearLayout
    private lateinit var style: ThemeSpec
    private val settings by lazy { KeyboardConfig.prefs(this) }
    private var keyboardColor = Color.BLACK
    private var keyboardFont = Typeface.MONOSPACE
    private var effect = 0
    private var intensity = .7f
    private var holdDelay = 285L
    private var heightOffset = 0
    private var privateField = false
    private var toolbox = ""
    private var letterStyle = 0
    private var photoRevision = -1L
    private var wallpaper: BitmapDrawable? = null
    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var lexicon: KeyboardSuggestions? = null
    private var lookup: Future<*>? = null
    private var revision = 0
    private var suggestionsAllowed = false
    private var selectionActive = false
    private val suggestions = mutableListOf<TextView>()
    private val updateSuggestions = Runnable { requestSuggestions() }
    private val letters = mutableListOf<Pair<Char, TextView>>()
    private var shiftKey: TextView? = null
    private var shift = 0
    private var lastShift = 0L
    private var symbols = false
    private var numeric = false
    private var animateKeys = false
    private var accentPopup: LinearLayout? = null
    private var accentOwner: View? = null
    private var accentLeft = 0
    private var accentCell = 0
    private var accentIndex = 0
    private var eraseRepeat: Runnable? = null
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()

    override fun onCreate() {
        super.onCreate()
        worker.execute {
            val index = runCatching {
                assets.open("suggestions/pt-BR.txt").bufferedReader().useLines { KeyboardSuggestions(it) }
            }.getOrNull()
            ui.post { lexicon = index; queueSuggestions() }
        }
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        loadStyle()
        frame = FrameLayout(this)
        keys = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(6), dp(3), dp(6))
            setOnApplyWindowInsetsListener { _, insets ->
                val safe = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
                setPadding(dp(3) + safe.left, dp(6), dp(3) + safe.right, dp(6))
                insets
            }
        }
        frame.addView(keys, FrameLayout.LayoutParams(-1, -2))
        render()
        window?.window?.let(::darkNavigation)
        return frame
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val type = info?.inputType ?: InputType.TYPE_CLASS_TEXT
        val kind = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        numeric = kind in listOf(InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME)
        symbols = numeric
        toolbox = ""
        privateField = (kind == InputType.TYPE_CLASS_TEXT && variation in PASSWORD_VARIATIONS) ||
            (kind == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) ||
            ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        loadStyle()
        letterStyle = if (kind == InputType.TYPE_CLASS_TEXT && variation in SUGGESTION_FIELDS && !privateField)
            settings.getInt("letters", 0).coerceIn(0, 3) else 0
        shift = 0
        lastShift = 0
        selectionActive = info != null && info.initialSelStart != info.initialSelEnd
        suggestionsAllowed = settings.getBoolean("suggestions", true) &&
            kind == InputType.TYPE_CLASS_TEXT && variation in SUGGESTION_FIELDS &&
            type and (InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) == 0 &&
            ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0
        window?.window?.let(::darkNavigation)
        val prefs = getSharedPreferences("neon", MODE_PRIVATE)
        animateKeys = effect != 3 && ValueAnimator.areAnimatorsEnabled() && !prefs.getBoolean("instant", false) &&
            !prefs.getBoolean("saver", false) && !getSystemService(PowerManager::class.java).isPowerSaveMode
        if (::keys.isInitialized) render()
        queueSuggestions()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
                                   candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selectionActive = newSelStart != newSelEnd
        queueSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        suggestionsAllowed = false
        if (toolbox.isNotEmpty()) {
            toolbox = ""
            if (::keys.isInitialized) render()
        }
        clearSuggestions()
        closeAccents()
        stopRepeat()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        suggestionsAllowed = false
        ui.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun loadStyle() {
        val base = Themes.current(this)
        style = base.copy(accent = settings.getInt("accent", base.accent),
            secondary = settings.getInt("secondary", base.secondary))
        keyboardColor = settings.getInt("background", Color.BLACK)
        keyboardFont = Typeface.create(KeyboardConfig.fonts[settings.getInt("font", 0).coerceIn(0, 3)], Typeface.NORMAL)
        effect = settings.getInt("effect", 0).coerceIn(0, 3)
        intensity = settings.getInt("intensity", 70).coerceIn(20, 100) / 100f
        holdDelay = settings.getInt("hold", 285).coerceIn(200, 500).toLong()
        heightOffset = (settings.getInt("height", 1).coerceIn(0, 2) - 1) * 6
        val revision = settings.getLong("photo_revision", 0)
        if (photoRevision != revision) {
            photoRevision = revision
            wallpaper = null
            if (revision > 0L) worker.execute {
                val bitmap = BitmapFactory.decodeFile(KeyboardConfig.photo(this).path)
                ui.post {
                    if (photoRevision == revision && bitmap != null && ::keys.isInitialized) {
                        wallpaper = BitmapDrawable(resources, bitmap).apply { gravity = Gravity.FILL }
                        if (isInputViewShown) render() else updateBackground()
                    } else bitmap?.recycle()
                }
            }
        }
    }

    private fun updateBackground() {
        val photo = wallpaper
        keys.background = if (photo == null) ColorDrawable(keyboardColor) else {
            photo.alpha = 255 * settings.getInt("photo", 30).coerceIn(10, 65) / 100
            LayerDrawable(arrayOf(ColorDrawable(keyboardColor), photo))
        }
    }

    private fun render() {
        closeAccents()
        stopRepeat()
        keys.removeAllViews()
        letters.clear()
        suggestions.clear()
        shiftKey = null
        updateBackground()
        if (toolbox.isNotEmpty()) {
            renderTools()
            return
        }
        if (suggestionsAllowed && letterStyle == 0 && !symbols) {
            val bar = row()
            repeat(3) {
                suggestions += key(bar, "", "Sugestão", height = 36) {}.apply { isEnabled = false }
            }
        }
        val rows = when {
            numeric -> listOf("123", "456", "789", "+0-.,")
            symbols -> listOf("1234567890", "@#$%&*()-", "+=/:;!?_")
            else -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        }
        for ((index, characters) in rows.withIndex()) {
            val line = row()
            if (index == rows.lastIndex && !symbols) {
                shiftKey = key(line, "⇧", "Maiúsculas", 1.25f) { toggleShift() }
            }
            for (letter in characters) {
                val button = key(line, cased(letter.toString())) { insert(cased(letter.toString())) }
                if (!symbols) letters += letter to button
                ACCENTS[letter]?.let { variants -> bindAccentHold(button, variants) }
            }
            if (index == rows.lastIndex) {
                val delete = key(line, "⌫", "Apagar", 1.25f) { erase() }
                bindRepeat(delete)
            }
        }
        val bottom = row()
        key(bottom, "◎", "Trocar teclado") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
        if (!numeric && !privateField) key(bottom, "⚙", "Ferramentas do teclado") { showTools("menu") }
        if (!numeric) key(bottom, if (symbols) "ABC" else "123", "Letras ou símbolos") {
            symbols = !symbols
            render()
            queueSuggestions()
        }
        bindAccentHold(key(bottom, ",") { insert(",") }, listOf(",", ";", ":", "!", "?"))
        key(bottom, "ESPAÇO", "Espaço", 3.5f) { insert(" ") }
        bindAccentHold(key(bottom, ".") { insert(".") }, listOf(".", "…", "-", "'", "\""))
        key(bottom, "↵", "Enter", 1.25f) {
            val info = currentInputEditorInfo
            if (info != null && info.actionId != 0 && info.actionLabel != null) {
                currentInputConnection?.performEditorAction(info.actionId)
            } else if (!sendDefaultEditorAction(true)) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        footer()
        updateShiftLabels()
    }

    private fun footer() {
        keys.addView(Space(this), LinearLayout.LayoutParams(-1, dp(52)))
    }

    private fun showTools(section: String) {
        if (privateField || getSystemService(KeyguardManager::class.java).isDeviceLocked) return
        toolbox = section
        render()
        queueSuggestions()
    }

    private fun renderTools() {
        val header = row()
        key(header, "‹ ABC", "Voltar às letras", 1.4f) { showTools("") }
        key(header, "☺", "Emojis") { showTools("emoji") }
        key(header, "GIF", "GIFs") { showTools("gif") }
        key(header, "COLAR", "Área de transferência", 1.5f) { showTools("clip") }
        when (toolbox) {
            "emoji" -> {
                for (emojis in EMOJI_ROWS) {
                    val line = row()
                    for (emoji in emojis) key(line, emoji, emoji) { insert(emoji) }
                }
                val line = row()
                for (face in listOf("""¯\_(ツ)_/¯""", "(⌐■_■)", "(◕‿◕)", "⚡")) {
                    key(line, face) { insert(face) }
                }
            }
            "clip" -> {
                val manager = getSystemService(ClipboardManager::class.java)
                val description = manager.primaryClipDescription
                val sensitive = description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
                val supported = description?.hasMimeType("text/*") == true
                val value = if (supported && !sensitive) manager.primaryClip?.getItemAt(0)?.text?.toString()?.take(8192) else null
                val caption = when {
                    sensitive -> "Conteúdo sensível oculto"
                    value.isNullOrEmpty() -> "Sem texto copiado"
                    else -> "COLAR · ${value.replace('\n', ' ').take(54)}"
                }
                key(row(), caption, "Colar texto copiado", height = 58) {
                    // Re-read at tap time so a stale preview cannot paste a different entry.
                    val clip = manager.primaryClip
                    if (value != null && clip?.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) != true &&
                        clip?.getItemAt(0)?.text?.toString()?.take(8192) == value) {
                        currentInputConnection?.commitText(value, 1)
                        showTools("")
                    } else showTools("clip")
                }.isEnabled = !value.isNullOrEmpty()
            }
            "gif" -> {
                val accepts = acceptsGif()
                for ((id, label) in KeyboardGifProvider.items) {
                    key(row(), label, label) { insertGif(id) }.isEnabled = accepts
                }
                if (!accepts) key(row(), "ESTE CAMPO NÃO ACEITA GIF") {}.isEnabled = false
            }
            else -> {
                key(row(), "CONFIGURAÇÕES DO TECLADO") { openPanel(KeyboardConfigActivity::class.java) }
                key(row(), "TRADUTOR · ATLAS") { openPanel(KeyboardTranslateActivity::class.java) }
                val modes = row()
                KeyboardConfig.letterStyles.forEachIndexed { index, label ->
                    key(modes, if (letterStyle == index) "✓ $label" else label, label, 1f) {
                        val variation = (currentInputEditorInfo?.inputType ?: 0) and InputType.TYPE_MASK_VARIATION
                        if (variation !in SUGGESTION_FIELDS || currentInputEditorInfo?.inputType?.and(InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return@key
                        letterStyle = index
                        settings.edit().putInt("letters", index).apply()
                        showTools("")
                    }
                }
            }
        }
        footer()
    }

    private fun openPanel(activity: Class<*>) {
        val intent = Intent(this, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent); requestHideSelf(0) }
            .onFailure { Toast.makeText(this, "Painel indisponível.", Toast.LENGTH_SHORT).show() }
    }

    private fun acceptsGif() = currentInputEditorInfo?.contentMimeTypes?.any {
        ClipDescription.compareMimeTypes("image/gif", it)
    } == true

    private fun insertGif(id: String) {
        if (!acceptsGif() || privateField) return
        val content = InputContentInfo(KeyboardGifProvider.uri(this, id),
            ClipDescription("NEON GIF", arrayOf("image/gif")), null)
        val sent = runCatching {
            currentInputConnection?.commitContent(content, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null) == true
        }.getOrDefault(false)
        if (sent) showTools("")
        else Toast.makeText(this, "O aplicativo não recebeu o GIF.", Toast.LENGTH_SHORT).show()
    }

    private fun row() = LinearLayout(this).also { keys.addView(it, LinearLayout.LayoutParams(-1, -2)) }

    private fun key(row: LinearLayout, caption: String, description: String = caption, weight: Float = 1f,
                    height: Int = keyHeight(), action: () -> Unit): CyberKey {
        val view = CyberKey(this, style.accent, style.secondary, effect, intensity, settings.getBoolean("haptics", false)) { animateKeys }.apply {
            text = caption
            contentDescription = description
            gravity = Gravity.CENTER
            typeface = keyboardFont
            textSize = if (height == 36) 13f else if (caption.length > 3) 12f else 18f
            setTextColor(style.accent)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            val keyShape = GradientDrawable().apply {
                setColor(if (wallpaper == null) style.panel else Color.argb(172, Color.red(style.panel), Color.green(style.panel), Color.blue(style.panel)))
                cornerRadius = dp(style.radius.coerceIn(3, 10)).toFloat()
                setStroke(dp(1), style.secondary)
            }
            background = RippleDrawable(ColorStateList.valueOf(style.stroke), keyShape, null)
            isFocusable = true
            setOnClickListener { action() }
        }
        row.addView(view, LinearLayout.LayoutParams(0, dp(height), weight).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
        return view
    }

    private fun keyHeight() =
        (if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 39 else 47) + heightOffset

    private fun cased(value: String) = if (shift > 0) value.uppercase(Locale.ROOT) else value

    private fun toggleShift() {
        val now = SystemClock.uptimeMillis()
        shift = when {
            shift == 2 -> 0
            shift == 1 && now-lastShift <= 350 -> 2
            shift == 1 -> 0
            else -> 1
        }
        lastShift = now
        updateShiftLabels()
    }

    private fun updateShiftLabels() {
        shiftKey?.text = when (shift) { 2 -> "⇪"; 1 -> "⬆"; else -> "⇧" }
        shiftKey?.contentDescription = if (shift == 2) "Maiúsculas travadas" else "Maiúsculas"
        for ((character, button) in letters) {
            val value = cased(character.toString())
            button.text = value
            button.contentDescription = value
        }
    }

    private fun insert(value: String) {
        currentInputConnection?.commitText(KeyboardConfig.stylize(value, letterStyle), 1)
        if (shift == 1) { shift = 0; updateShiftLabels() }
        queueSuggestions()
    }

    private fun erase() {
        currentInputConnection?.let {
            if (selectionActive) it.commitText("", 1)
            else if (!it.deleteSurroundingTextInCodePoints(1, 0)) it.deleteSurroundingText(1, 0)
        }
        queueSuggestions()
    }

    private fun bindRepeat(button: CyberKey) {
        var repeated = false
        val pulse = object : Runnable {
            override fun run() {
                if (!button.isPressed) return
                repeated = true
                erase()
                ui.postDelayed(this, 55)
            }
        }
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    stopRepeat()
                    repeated = false
                    eraseRepeat = pulse
                    button.isPressed = true
                    button.flash()
                    ui.postDelayed(pulse, 340)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRepeat()
                    button.isPressed = false
                    if (event.actionMasked == MotionEvent.ACTION_UP && !repeated) button.performClick()
                }
            }
            true
        }
    }

    private fun stopRepeat() {
        eraseRepeat?.let(ui::removeCallbacks)
        eraseRepeat = null
    }

    private fun bindAccentHold(button: CyberKey, variants: List<String>) {
        var rawX = 0f
        var rawY = 0f
        var canceled = false
        val reveal = Runnable { if (button.isPressed) openAccents(button, variants, rawX) }
        button.setOnLongClickListener {
            val position = IntArray(2)
            button.getLocationOnScreen(position)
            openAccents(button, variants, position[0] + button.width/2f)
            true
        }
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    closeAccents()
                    rawX = event.rawX
                    rawY = event.rawY
                    canceled = false
                    button.isPressed = true
                    button.flash()
                    ui.postDelayed(reveal, holdDelay)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (accentOwner === button) highlightAccent(event.rawX)
                    else if (kotlin.math.abs(event.rawX-rawX) > dp(22) || kotlin.math.abs(event.rawY-rawY) > dp(22)) {
                        ui.removeCallbacks(reveal)
                        canceled = true
                        button.isPressed = false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    ui.removeCallbacks(reveal)
                    button.isPressed = false
                    val active = accentOwner === button
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        if (active) {
                            highlightAccent(event.rawX)
                            val selected = variants[accentIndex]
                            closeAccents()
                            if (kotlin.math.abs(event.rawY-rawY) < dp(100)) insert(cased(selected))
                        } else if (!canceled) button.performClick()
                    } else if (active) closeAccents()
                }
            }
            true
        }
    }

    private fun openAccents(owner: View, variants: List<String>, pointerX: Float) {
        closeAccents()
        val keyboard = IntArray(2)
        val origin = IntArray(2)
        frame.getLocationOnScreen(keyboard)
        owner.getLocationOnScreen(origin)
        accentCell = minOf(dp(49), frame.width / variants.size).coerceAtLeast(1)
        val width = variants.size * accentCell
        val left = (origin[0] - keyboard[0] + (owner.width - accentCell)/2).coerceIn(0, (frame.width-width).coerceAtLeast(0))
        val height = dp(46)
        val top = (origin[1] - keyboard[1] - height - dp(3)).coerceAtLeast(0)
        accentLeft = keyboard[0] + left
        val popup = LinearLayout(this).apply { setBackgroundColor(Color.BLACK); elevation = dp(12).toFloat() }
        for (value in variants) {
            key(popup, cased(value), cased(value), height = 42) { closeAccents(); insert(cased(value)) }
        }
        frame.addView(popup, FrameLayout.LayoutParams(width, height).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            leftMargin = left
            topMargin = top
        })
        accentPopup = popup
        accentOwner = owner
        accentIndex = -1
        highlightAccent(pointerX)
    }

    private fun highlightAccent(rawX: Float) {
        val popup = accentPopup ?: return
        val next = ((rawX-accentLeft)/accentCell).toInt().coerceIn(0, popup.childCount-1)
        if (next == accentIndex) return
        accentIndex = next
        for (i in 0 until popup.childCount) {
            val selected = i == accentIndex
            val key = popup.getChildAt(i) as TextView
            key.setBackgroundColor(if (selected) style.accent else Color.BLACK)
            key.setTextColor(if (selected) Color.BLACK else style.accent)
        }
    }

    private fun closeAccents() {
        accentPopup?.let { frame.removeView(it) }
        accentPopup = null
        accentOwner = null
    }

    private fun clearSuggestions() {
        revision++
        ui.removeCallbacks(updateSuggestions)
        lookup?.cancel(false)
        showSuggestions("", emptyList())
    }

    private fun queueSuggestions() {
        revision++
        ui.removeCallbacks(updateSuggestions)
        lookup?.cancel(false)
        if (suggestionsAllowed && letterStyle == 0 && !selectionActive && !symbols && toolbox.isEmpty()) ui.postDelayed(updateSuggestions, 55)
        else showSuggestions("", emptyList())
    }

    private fun currentToken(connection: InputConnection): String {
        if (!suggestionsAllowed || letterStyle != 0 || selectionActive || symbols || toolbox.isNotEmpty()) return ""
        if (connection.getTextAfterCursor(1, 0)?.firstOrNull()?.isLetter() == true) return ""
        val tail = connection.getTextBeforeCursor(26, 0)?.toString().orEmpty()
        return tail.takeLastWhile(Char::isLetter).takeIf { it.length in 1..24 }.orEmpty()
    }

    private fun requestSuggestions() {
        val index = lexicon ?: return
        val connection = currentInputConnection ?: return
        val token = runCatching { currentToken(connection) }.getOrDefault("")
        val ticket = revision
        if (token.isEmpty()) { showSuggestions("", emptyList()); return }
        if (worker.isShutdown) return
        lookup = worker.submit {
            val candidates = index.suggest(token)
            ui.post { if (ticket == revision && suggestionsAllowed) showSuggestions(token, candidates) }
        }
    }

    private fun showSuggestions(token: String, words: List<String>) {
        for ((i, button) in suggestions.withIndex()) {
            val value = words.getOrNull(i)
            button.text = value.orEmpty()
            button.contentDescription = value?.let { "Sugestão " + it } ?: "Sem sugestão"
            button.isEnabled = value != null
            button.setOnClickListener(if (value == null) null else View.OnClickListener {
                val connection = currentInputConnection ?: return@OnClickListener
                if (runCatching { currentToken(connection) }.getOrDefault("") != token) return@OnClickListener
                connection.beginBatchEdit()
                try {
                    if (connection.deleteSurroundingText(token.length, 0)) {
                        if (!connection.commitText(value + " ", 1)) connection.commitText(token, 1)
                    }
                } finally { connection.endBatchEdit() }
                clearSuggestions()
            })
        }
    }

    private companion object {
        val PASSWORD_VARIATIONS = setOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        val EMOJI_ROWS = listOf(
            listOf("😀", "😂", "🥹", "😎", "🙃", "🤔", "😅", "🫠"),
            listOf("❤️", "🖤", "💚", "💜", "✨", "⚡", "🔥", "💀"),
            listOf("🤖", "👾", "🕶️", "💾", "🛰️", "🧬", "🔋", "🦾"),
            listOf("👍", "🙌", "🤝", "🫶", "🎮", "🎉", "✅", "🚀")
        )
        val SUGGESTION_FIELDS = setOf(InputType.TYPE_TEXT_VARIATION_NORMAL, InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
            InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE, InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT)
        val ACCENTS = mapOf(
            'a' to listOf("á", "à", "â", "ã"), 'e' to listOf("é", "ê"), 'i' to listOf("í"),
            'o' to listOf("ó", "ô", "õ"), 'u' to listOf("ú", "ü"), 'c' to listOf("ç")
        )
    }
}

private class CyberKey(context: Context, private val accent: Int, private val secondary: Int,
                       private val effect: Int, private val intensity: Float, private val haptics: Boolean,
                       private val motionEnabled: () -> Boolean) : TextView(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var progress = 1f
    private var pulse: ValueAnimator? = null

    fun flash() {
        if (!isEnabled) return
        if (haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (!motionEnabled()) return
        pulse?.cancel()
        pulse = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 145
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) flash()
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (progress >= 1f) return
        val y = height * progress
        val opacity = ((1f-progress)*225f*intensity).toInt()
        if (effect == 1) {
            paint.color = accent
            paint.alpha = opacity
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = resources.displayMetrics.density
            val radius = width * (.15f + progress * .33f)
            canvas.drawCircle(width/2f, height/2f, radius, paint)
            paint.style = Paint.Style.FILL
            return
        }
        paint.strokeWidth = resources.displayMetrics.density
        paint.color = secondary
        paint.alpha = opacity/2
        canvas.drawLine(width*.05f, y + paint.strokeWidth*2, width*.72f, y + paint.strokeWidth*2, paint)
        paint.color = accent
        paint.alpha = opacity
        if (effect == 2) {
            for (index in 0..2) {
                val offset = ((index * .23f + progress) % 1f) * width
                canvas.drawRect(offset, y + index * 4f, offset + width * .23f, y + index * 4f + paint.strokeWidth, paint)
            }
        } else canvas.drawLine(width*.12f, y, width*.93f, y, paint)
    }

    override fun onDetachedFromWindow() {
        pulse?.cancel()
        pulse = null
        super.onDetachedFromWindow()
    }
}
