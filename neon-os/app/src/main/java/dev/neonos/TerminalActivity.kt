package dev.neonos

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.os.StatFs
import android.text.InputFilter
import android.widget.EditText
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

enum class TerminalCommand { HELP, STATUS, BATTERY, STORAGE, APPS, DARK, NEON, CORPORATE, WALLPAPER, ATLAS, CLEAR, OPEN, VOLUME, MUTE, UNMUTE, BRIGHTNESS, UNKNOWN }

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
        else -> when (LocalTools.normalize(input.trim().substringBefore(' '))) {
            "open", "abrir" -> if (argument(input).isBlank()) TerminalCommand.UNKNOWN else TerminalCommand.OPEN
            "volume" -> TerminalCommand.VOLUME
            "silenciar", "mute" -> TerminalCommand.MUTE
            "dessilenciar", "desilenciar", "unsilence", "unmute" -> TerminalCommand.UNMUTE
            "brilho" -> TerminalCommand.BRIGHTNESS
            else -> TerminalCommand.UNKNOWN
        }
    }

    fun argument(input: String) = input.trim().substringAfter(' ', "").trim()
}

internal fun applyNeonBrightness(activity: Activity) {
    val level = activity.getSharedPreferences("neon", Activity.MODE_PRIVATE).getInt("terminal_brightness", -1)
    activity.window.attributes = activity.window.attributes.apply {
        screenBrightness = if (level < 0) -1f else level.coerceIn(1, 100) / 100f
    }
}

class TerminalActivity : NeonPanel() {
    private lateinit var log: TextView
    private lateinit var result: TextView
    private val refresh = { log.text = EventJournal.lines(32).ifBlank { "Sem eventos nesta sessão." } }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        screen("NEON TERMINAL / >_")
        applyNeonBrightness(this)
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
            TerminalCommand.HELP -> "open <app> · volume [0–100] · silenciar · dessilenciar\nbrilho [1–100 | auto | sistema]\nstatus · battery · storage · apps · theme dark/neon/corporate · wallpaper · atlas · clear"
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
            TerminalCommand.OPEN -> {
                openApp(TerminalParser.argument(input))
                "Procurando aplicativo…"
            }
            TerminalCommand.VOLUME, TerminalCommand.MUTE, TerminalCommand.UNMUTE ->
                media(command, TerminalParser.argument(input))
            TerminalCommand.BRIGHTNESS -> brightness(TerminalParser.argument(input))
            TerminalCommand.UNKNOWN -> "Comando não permitido. Use help."
        }
    }

    private fun openApp(name: String) {
        val repository = AppRepository(this)
        worker.execute {
            val entries = runCatching { repository.load() }.getOrDefault(emptyList())
            val normalized = LocalTools.normalize(name)
            val exact = entries.filter { LocalTools.normalize(it.label) == normalized || it.component.packageName == name }
            val matches = exact.ifEmpty { entries.filter { Policy.matches(it.label, name) }.take(30) }
            whenAlive {
                fun launch(app: AppEntry) {
                    result.text = runCatching { repository.open(app); "Abrindo ${app.label}." }
                        .getOrElse { "Não foi possível abrir ${app.label}." }
                }
                when (matches.size) {
                    0 -> result.text = "Aplicativo não encontrado. Use apps para ver a gaveta."
                    1 -> launch(matches.first())
                    else -> AlertDialog.Builder(this).setTitle("Escolha o aplicativo")
                        .setItems(matches.map { "${it.label} / perfil ${it.serial}" }.toTypedArray()) { _, index -> launch(matches[index]) }
                        .setNegativeButton("Cancelar", null).show()
                }
            }
        }
    }

    private fun media(command: TerminalCommand, value: String): String {
        val audio = getSystemService(AudioManager::class.java)
        val stream = AudioManager.STREAM_MUSIC
        if (audio.isVolumeFixed) return "O Android fixou o volume de mídia neste dispositivo."
        return runCatching {
            when (command) {
                TerminalCommand.MUTE -> audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                TerminalCommand.UNMUTE -> audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                else -> if (value.isNotEmpty()) {
                    val percent = value.toIntOrNull()
                    if (percent == null || percent !in 0..100) return "Use volume 0 a 100."
                    val min = audio.getStreamMinVolume(stream)
                    val max = audio.getStreamMaxVolume(stream)
                    audio.setStreamVolume(stream, (min + (max-min) * percent / 100f).roundToInt(), 0)
                }
            }
            val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
            val level = (audio.getStreamVolume(stream) * 100f / max).roundToInt()
            if (audio.isStreamMute(stream)) "Mídia silenciada." else "Volume de mídia: $level%."
        }.getOrElse { "Controle de mídia indisponível." }
    }

    private fun brightness(value: String): String {
        val command = LocalTools.normalize(value)
        if (command == "sistema") {
            return runCatching { startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS)); "Configurações de tela abertas." }
                .getOrElse { "Configurações de tela indisponíveis." }
        }
        if (command.isNotBlank()) {
            val level = if (command == "auto") -1 else command.toIntOrNull()?.takeIf { it in 1..100 }
                ?: return "Use brilho 1 a 100, brilho auto ou brilho sistema."
            preferences.edit().putInt("terminal_brightness", level).apply()
            applyNeonBrightness(this)
        }
        val level = preferences.getInt("terminal_brightness", -1)
        return if (level < 0) "Brilho do NEON segue o Android." else "Brilho do NEON: $level%."
    }

    private fun battery(): String {
        val info = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = info?.getIntExtra("level", -1) ?: -1
        val scale = info?.getIntExtra("scale", 100) ?: 100
        return if (level < 0 || scale <= 0) "Bateria indisponível." else "Bateria: ${level * 100 / scale}%."
    }
}
