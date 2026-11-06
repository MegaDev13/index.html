package dev.neonos

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.util.AtomicFile
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import java.io.File

internal object KeyboardConfig {
    fun prefs(context: Context) = context.getSharedPreferences("neon_keyboard", Context.MODE_PRIVATE)
    fun photo(context: Context) = File(context.filesDir, "keyboard-wallpaper.webp")
    val fonts = listOf("monospace", "sans-serif", "sans-serif-condensed", "serif")
    val fontLabels = arrayOf("Terminal", "Sans", "Condensada", "Serifada")
    val effects = arrayOf("Varredura", "Pulso", "Glitch", "Desligado")
    val letterStyles = arrayOf("Normal", "Negrito", "Itálico", "Monoespaçado")
    val heights = arrayOf("Baixo", "Padrão", "Alto")

    fun stylize(text: String, mode: Int): String {
        val (upper, lower, digit) = when (mode) {
            1 -> Triple(0x1D400, 0x1D41A, 0x1D7CE)
            2 -> Triple(0x1D434, 0x1D44E, 0)
            3 -> Triple(0x1D670, 0x1D68A, 0x1D7F6)
            else -> return text
        }
        return buildString {
            for (c in text) when {
                c == 'h' && mode == 2 -> appendCodePoint(0x210E)
                c in 'A'..'Z' -> appendCodePoint(upper + (c - 'A'))
                c in 'a'..'z' -> appendCodePoint(lower + (c - 'a'))
                c in '0'..'9' && digit != 0 -> appendCodePoint(digit + (c - '0'))
                else -> append(c)
            }
        }
    }
}

class KeyboardConfigActivity : NeonPanel() {
    private val config by lazy { KeyboardConfig.prefs(this) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        render()
    }

    private fun render() {
        screen("NEON KEYBOARD")
        add(text("Aparência e digitação do teclado do Android. Use ⚙ no teclado para abrir as ferramentas."))
        add(button("ATIVAR NO ANDROID") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        add(button("ESCOLHER TECLADO") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() })
        toggle("Sugestões em português", "suggestions", true)
        toggle("Vibração ao tocar", "haptics", false)
        choice("Fonte das teclas", "font", KeyboardConfig.fontLabels, 0)
        choice("Estilo do texto digitado", "letters", KeyboardConfig.letterStyles, 0)
        choice("Altura", "height", KeyboardConfig.heights, 1)
        slider("Pressionar para acentuar", "hold", 200, 500, 285, " ms")
        add(text("VISUAL", 18f))
        color("Cor das letras", "accent", style.accent)
        color("Cor dos efeitos", "secondary", style.secondary)
        color("Fundo do teclado", "background", Color.BLACK)
        choice("Efeito ao tocar", "effect", KeyboardConfig.effects, 0)
        slider("Intensidade do efeito", "intensity", 20, 100, 70, "%")
        add(button("ESCOLHER FOTO DE FUNDO") {
            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
            runCatching { startActivityForResult(picker, PICK_PHOTO) }.onFailure { message("Seletor de fotos indisponível.") }
        })
        slider("Visibilidade da foto", "photo", 10, 65, 30, "%")
        if (KeyboardConfig.photo(this).isFile) add(button("REMOVER FOTO") {
            AtomicFile(KeyboardConfig.photo(this)).delete()
            config.edit().remove("photo_revision").apply()
            render()
        })
        add(button("RESTAURAR AJUSTES DO TECLADO") {
            AtomicFile(KeyboardConfig.photo(this)).delete()
            config.edit().clear().apply()
            render()
        })
        add(text("PREENCHIMENTO AUTOMÁTICO", 18f))
        add(text("Seu gerenciador de senhas pode perguntar se você quer salvar um login. O teclado não lê nem armazena senhas."))
        add(button("CONFIGURAR GERENCIADOR DE SENHAS") {
            AlertDialog.Builder(this).setTitle("Preenchimento automático do Android")
                .setMessage("Nas configurações, pesquise por preenchimento automático ou gerenciador de senhas. Escolha seu provedor e habilite o salvamento nele.")
                .setNegativeButton("Voltar", null)
                .setPositiveButton("Abrir configurações") { _, _ -> startActivity(Intent(Settings.ACTION_SETTINGS)) }
                .show()
        })
    }

    private fun toggle(label: String, name: String, default: Boolean) {
        add(Switch(this).apply {
            text = label
            setTextColor(style.text)
            isChecked = config.getBoolean(name, default)
            setOnCheckedChangeListener { _, checked -> config.edit().putBoolean(name, checked).apply() }
        })
    }

    private fun choice(label: String, name: String, options: Array<String>, default: Int) {
        val current = config.getInt(name, default).coerceIn(options.indices)
        add(button("$label · ${options[current]}") {
            AlertDialog.Builder(this).setTitle(label).setSingleChoiceItems(options, current) { dialog, which ->
                config.edit().putInt(name, which).apply()
                dialog.dismiss()
                render()
            }.setNegativeButton("Cancelar", null).show()
        })
    }

    private fun slider(label: String, name: String, min: Int, max: Int, default: Int, unit: String) {
        val current = config.getInt(name, default).coerceIn(min, max)
        val caption = text("$label · $current$unit")
        add(caption)
        add(SeekBar(this).apply {
            contentDescription = label
            this.max = max - min
            progress = current - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    caption.text = "$label · ${progress + min}$unit"
                    if (fromUser) config.edit().putInt(name, progress + min).apply()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        })
    }

    private fun color(label: String, name: String, default: Int) {
        val current = config.getInt(name, default)
        val hex = "#%06X".format(current and 0xFFFFFF)
        add(button("$label · $hex") {
            val field = EditText(this).apply {
                setSingleLine(true)
                setText(hex)
                selectAll()
                filters = arrayOf(InputFilter.LengthFilter(7))
            }
            val dialog = AlertDialog.Builder(this).setTitle(label).setView(field)
                .setNegativeButton("Cancelar", null)
                .setNeutralButton("Seguir tema") { _, _ -> config.edit().remove(name).apply(); render() }
                .setPositiveButton("Aplicar", null).show()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = field.text.toString().trim()
                if (!Regex("#?[0-9a-fA-F]{6}").matches(value)) field.error = "Use um código como #00FF88."
                else {
                    config.edit().putInt(name, Color.parseColor("#" + value.removePrefix("#"))).apply()
                    dialog.dismiss()
                    render()
                }
            }
        }.apply { setTextColor(current.takeUnless { name == "background" } ?: style.text) })
    }

    @Deprecated("Activity result for the Android document picker")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request != PICK_PHOTO || result != Activity.RESULT_OK) return
        val uri: Uri = data?.data ?: return
        worker.execute {
            val ok = runCatching {
                val source = ImageDecoder.createSource(contentResolver, uri)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    require(info.size.width in 1..16000 && info.size.height in 1..16000)
                    val divisor = maxOf(1f, maxOf(info.size.width, info.size.height) / 1200f)
                    decoder.setTargetSize((info.size.width / divisor).toInt().coerceAtLeast(1),
                        (info.size.height / divisor).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                val file = AtomicFile(KeyboardConfig.photo(this))
                val output = file.startWrite()
                try {
                    check(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 86, output))
                    file.finishWrite(output)
                } catch (e: Exception) {
                    file.failWrite(output)
                    throw e
                } finally { bitmap.recycle() }
                config.edit().putLong("photo_revision", System.currentTimeMillis()).apply()
            }.isSuccess
            whenAlive {
                message(if (ok) "Foto aplicada ao teclado." else "Não foi possível ler a foto.")
                if (ok) render()
            }
        }
    }

    private companion object { const val PICK_PHOTO = 71 }
}
