package dev.neonos

import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.text.InputFilter
import android.widget.EditText
import android.widget.TextView
import java.util.Locale

enum class TerminalCommand { HELP, STATUS, BATTERY, STORAGE, APPS, DARK, NEON, CORPORATE, WALLPAPER, ATLAS, CLEAR, UNKNOWN }

object TerminalParser {
    fun parse(input: String): TerminalCommand = when (input.trim().lowercase(Locale.ROOT)) {
        "help" -> TerminalCommand.HELP
        "status" -> TerminalCommand.STATUS
        "battery" -> TerminalCommand.BATTERY
        "storage" -> TerminalCommand.STORAGE
        "apps" -> TerminalCommand.APPS
        "theme dark" -> TerminalCommand.DARK
        "theme neon" -> TerminalCommand.NEON
        "theme corporate" -> TerminalCommand.CORPORATE
        "wallpaper" -> TerminalCommand.WALLPAPER
        "atlas" -> TerminalCommand.ATLAS
        "clear" -> TerminalCommand.CLEAR
        else -> TerminalCommand.UNKNOWN
    }
}

class TerminalActivity : NeonPanel() {
    private lateinit var log: TextView
    private lateinit var result: TextView
    private val refresh = { log.text = EventJournal.lines(32).ifBlank { "Sem eventos nesta sessão." } }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        screen("NEON TERMINAL / >_")
        add(text("Eventos observados pelo NEON nesta sessão.", 13f))
        val input = EditText(this).apply {
            hint = "neon > help"
            filters = arrayOf(InputFilter.LengthFilter(160))
            setSingleLine()
            contentDescription = "Comando NEON"
        }
        add(input)
        add(button("EXECUTAR") { execute(input.text.toString()); input.setText("") })
        result = text("help · consulte os comandos")
        add(result)
        log = text("", 12f).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(style.accent)
            setTextIsSelectable(true)
        }
        add(log)
    }

    override fun onStart() { super.onStart(); EventJournal.observers.add(refresh); refresh() }
    override fun onStop() { EventJournal.observers.remove(refresh); TerminalWidget.refresh(this); super.onStop() }

    fun execute(input: String) {
        val command = TerminalParser.parse(input)
        if (command !in setOf(TerminalCommand.UNKNOWN, TerminalCommand.CLEAR)) EventJournal.record("TERMINAL", command.name)
        result.text = when (command) {
            TerminalCommand.HELP -> "help · status · battery · storage · apps · theme dark · theme neon · theme corporate · wallpaper · atlas · clear"
            TerminalCommand.STATUS -> "NEON ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} · ${if (AtlasApi(this).connected()) "Rede disponível" else "Offline"}"
            TerminalCommand.BATTERY -> battery()
            TerminalCommand.STORAGE -> "Dados: %.1f GiB livres".format(StatFs(filesDir.path).availableBytes / 1073741824.0)
            TerminalCommand.APPS -> {
                startActivity(Intent(this, MainActivity::class.java).putExtra("neon.page", "GRID")); "Gaveta aberta."
            }
            TerminalCommand.DARK, TerminalCommand.NEON, TerminalCommand.CORPORATE -> {
                val id = when (command) { TerminalCommand.DARK -> Themes.TERMINAL; TerminalCommand.NEON -> Themes.NEON; else -> Themes.CORPORATE }
                Themes.select(this, id)
                recreate()
                "Tema aplicado."
            }
            TerminalCommand.WALLPAPER -> {
                startActivity(Intent(this,WallpaperActivity::class.java)); "Seletor de wallpapers aberto."
            }
            TerminalCommand.ATLAS -> { startActivity(Intent(this, AtlasEntryActivity::class.java)); "ATLAS acionado." }
            TerminalCommand.CLEAR -> { EventJournal.clear(); "Registros apagados." }
            TerminalCommand.UNKNOWN -> "Comando não permitido. Use help."
        }
    }

    private fun battery(): String {
        val info = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = info?.getIntExtra("level", -1) ?: -1
        val scale = info?.getIntExtra("scale", 100) ?: 100
        return if (level < 0 || scale <= 0) "Bateria indisponível." else "Bateria: ${level * 100 / scale}%."
    }
}
