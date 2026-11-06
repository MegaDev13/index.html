package dev.neonos

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date
import kotlin.math.round

data class WeatherCity(val name: String, val latitude: Double, val longitude: Double) {
    init { require(name.isNotBlank() && name.length <= 160 && latitude in -90.0..90.0 && longitude in -180.0..180.0) }
    fun json() = JSONObject().put("name", name).put("latitude", latitude).put("longitude", longitude)
    companion object {
        fun parse(value: JSONObject) = WeatherCity(value.getString("name"), value.getDouble("latitude"), value.getDouble("longitude"))
    }
}

data class WeatherReading(val city: WeatherCity, val raw: JSONObject, val fetched: Long) {
    private val current = raw.getJSONObject("current")
    private val daily = raw.getJSONObject("daily")
    val temperature = current.getDouble("temperature_2m")
    val apparent = current.getDouble("apparent_temperature")
    val humidity = current.getInt("relative_humidity_2m")
    val code = current.getInt("weather_code")
    val low = daily.getJSONArray("temperature_2m_min").getDouble(0)
    val high = daily.getJSONArray("temperature_2m_max").getDouble(0)
    val observed = current.getString("time")
    val degrees get() = "%.0f°C".format(temperature)
    val condition get() = when (code) {
        0 -> "Céu limpo"
        1, 2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Névoa"
        51, 53, 55, 56, 57 -> "Garoa"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Chuva"
        71, 73, 75, 77, 85, 86 -> "Neve"
        95, 96, 99 -> "Trovoadas"
        else -> "Condição não classificada"
    }
    val symbol get() = when (code) { 0 -> "☀"; in 1..48 -> "☁"; in 95..99 -> "ϟ"; else -> "☂" }
    init {
        require(raw.getJSONObject("current_units").getString("temperature_2m") == "°C")
        require(listOf(temperature, apparent, low, high).all { it in -100.0..80.0 } && low <= high && humidity in 0..100 && fetched > 0)
    }
    fun json() = JSONObject().put("city", city.json()).put("raw", raw).put("fetched", fetched)
}

object MapProjection {
    fun x(longitude: Double) = ((longitude + 180) / 360).toFloat()
    fun y(latitude: Double) = ((90 - latitude) / 180).toFloat()
}

class WeatherClient(context: Context) {
    private val prefs = context.getSharedPreferences("neon", 0)
    @Volatile private var connection: HttpURLConnection? = null
    fun city(): WeatherCity? = runCatching { WeatherCity.parse(JSONObject(prefs.getString("weather_city", "")!!)) }.getOrNull()
    fun cached(): WeatherReading? = runCatching {
        val value = JSONObject(prefs.getString("weather_cache", "")!!)
        WeatherReading(WeatherCity.parse(value.getJSONObject("city")), value.getJSONObject("raw"), value.getLong("fetched"))
    }.getOrNull()?.takeIf { it.city == city() && System.currentTimeMillis() - it.fetched in 0..172800000L }

    fun select(city: WeatherCity) {
        prefs.edit().putString("weather_city", city.json().toString()).putBoolean("weather_enabled", true)
            .remove("weather_cache").remove("weather_attempt").apply()
    }

    fun cancel() = connection?.disconnect()
    fun due(): Boolean {
        val interval = prefs.getInt("weather_interval", 60)
        return interval > 0 && city() != null && prefs.getBoolean("weather_enabled", false) &&
            System.currentTimeMillis() - prefs.getLong("weather_attempt", 0) >= interval.coerceAtLeast(30) * 60000L
    }

    fun search(name: String): List<WeatherCity> {
        require(name.trim().length in 2..100) { "Digite entre 2 e 100 caracteres." }
        val query = URLEncoder.encode(name.trim(), "UTF-8")
        val results = get("https://geocoding-api.open-meteo.com/v1/search?name=$query&count=8&language=pt").optJSONArray("results") ?: JSONArray()
        return (0 until results.length()).map {
            val item = results.getJSONObject(it)
            val label = listOf(item.getString("name"), item.optString("admin1"), item.optString("country")).filter(String::isNotBlank).distinct().joinToString(", ")
            WeatherCity(label, item.getDouble("latitude"), item.getDouble("longitude"))
        }
    }

    fun refresh(): WeatherReading {
        val selected = city() ?: error("Selecione uma cidade.")
        check(prefs.getBoolean("weather_enabled", false)) { "Ative o clima escolhendo a cidade." }
        prefs.edit().putLong("weather_attempt", System.currentTimeMillis()).apply()
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${selected.latitude}&longitude=${selected.longitude}" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
        val reading = WeatherReading(selected, get(url), System.currentTimeMillis())
        if (selected == city() && prefs.getBoolean("weather_enabled", false)) {
            prefs.edit().putString("weather_cache", reading.json().toString()).apply()
            EventJournal.record("WEATHER", "Consulta Open-Meteo concluída")
        }
        return reading
    }

    private fun get(url: String): JSONObject {
        val request = URL(url).openConnection() as HttpURLConnection
        connection = request
        request.connectTimeout = 6000
        request.readTimeout = 8000
        request.instanceFollowRedirects = false
        try {
            check(request.responseCode == 200) { "Open-Meteo indisponível (HTTP ${request.responseCode})." }
            val bytes = request.inputStream.use { it.readNBytes(262145) }
            require(bytes.size <= 262144) { "Resposta excede o limite." }
            return JSONObject(String(bytes, Charsets.UTF_8))
        } finally {
            request.disconnect()
            if (connection === request) connection = null
        }
    }
}

class HudSettingsActivity : NeonPanel() {
    private val weather by lazy { WeatherClient(this) }
    private lateinit var status: TextView
    private var location: CancellationSignal? = null
    private var busy = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        screen("HUD / CLIMA")
        status = text("")
        add(status)
        add(button("SELECIONAR CIDADE") { chooseCity() })
        add(button("LOCALIZAÇÃO APROXIMADA") {
            AlertDialog.Builder(this).setTitle("Localização opcional")
                .setMessage("Uma consulta em primeiro plano. O Open-Meteo recebe as coordenadas arredondadas. A busca por cidade dispensa essa permissão.")
                .setPositiveButton("Continuar") { _, _ ->
                    if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
                    else requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 601)
                }.setNegativeButton("Cancelar", null).show()
        })
        add(button("ATUALIZAR CLIMA AGORA") { refresh() })
        add(button("INTERVALO DE ATUALIZAÇÃO") {
            AlertDialog.Builder(this).setItems(arrayOf("Manual", "30 minutos", "60 minutos", "3 horas")) { _, index ->
                preferences.edit().putInt("weather_interval", intArrayOf(0, 30, 60, 180)[index]).apply()
                updateStatus()
            }.show()
        })
        add(button("DESATIVAR CLIMA E APAGAR CACHE") {
            weather.cancel()
            preferences.edit().putBoolean("weather_enabled", false).remove("weather_cache").apply()
            updateStatus()
        })
        toggle("Relógio de 12 horas", "clock_12", false)
        toggle("Mostrar mapa", "hud_map", true)
        toggle("Mostrar círculo de temperatura", "hud_weather", true)
        toggle("Mostrar terminal na Home", "hud_terminal", style.mono)
        toggle("Feedback tátil", "haptic", true)
        add(button("ESCALA DO HUD") {
            AlertDialog.Builder(this).setItems(arrayOf("Compacta", "Ajustada à tela", "Ampliada")) { _, index ->
                preferences.edit().putInt("hud_scale", index).apply()
            }.show()
        })
        add(button("AUTORIZAR INDICADOR BLUETOOTH") {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) message("Indicador autorizado.")
            else requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 602)
        })
        add(button("OPEN-METEO / FONTE DOS DADOS") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/")))
        })
        updateStatus()
    }

    private fun toggle(label: String, key: String, default: Boolean) {
        add(Switch(this).apply {
            text = label
            setTextColor(style.text)
            isChecked = preferences.getBoolean(key, default)
            setOnCheckedChangeListener { _, checked -> preferences.edit().putBoolean(key, checked).apply() }
        })
    }

    private fun updateStatus() {
        val reading = weather.cached()
        val city = weather.city()?.name ?: "Escolha a cidade"
        status.text = if (reading == null) "$city\nSem dados meteorológicos."
        else "$city\n${reading.degrees} · ${reading.condition}\n" +
            "Sensação %.0f°C · umidade %d%% · mín %.0f° / máx %.0f°\n".format(reading.apparent, reading.humidity, reading.low, reading.high) +
            "Consulta ${DateFormat.getDateTimeInstance().format(Date(reading.fetched))}\nDados do modelo ${reading.observed} · Open-Meteo"
    }

    private fun chooseCity() {
        val input = EditText(this).apply { hint = "Cidade ou cidade, estado"; setSingleLine() }
        AlertDialog.Builder(this).setTitle("Buscar cidade no Open-Meteo").setView(input)
            .setPositiveButton("Pesquisar") { _, _ ->
                val query = input.text.toString()
                worker.execute {
                    runCatching { weather.search(query) }.onSuccess { cities ->
                        whenAlive {
                            if (cities.isEmpty()) message("Nenhuma cidade encontrada.")
                            else AlertDialog.Builder(this).setTitle("Escolha a localização")
                                .setItems(cities.map { it.name }.toTypedArray()) { _, index -> weather.select(cities[index]); refresh() }.show()
                        }
                    }.onFailure { whenAlive { message(it.message ?: "Busca indisponível.") } }
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun refresh() {
        if (busy) return
        busy = true
        status.text = "Consultando Open-Meteo…"
        worker.execute {
            val result = runCatching { weather.refresh() }
            whenAlive {
                busy = false
                updateStatus()
                result.exceptionOrNull()?.let { message(it.message ?: "Consulta indisponível.") }
            }
        }
    }

    private fun locate() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(LocationManager::class.java)
        val signal = CancellationSignal()
        location?.cancel()
        location = signal
        try {
            manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, signal, mainExecutor) { point ->
                if (point == null) message("Localização indisponível. Selecione a cidade manualmente.")
                else {
                    weather.select(WeatherCity("Localização aproximada", round(point.latitude * 100) / 100, round(point.longitude * 100) / 100))
                    refresh()
                }
            }
            ui.postDelayed({ signal.cancel() }, 20000)
        } catch (_: Exception) {
            message("Provedor aproximado indisponível. Selecione a cidade manualmente.")
        }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 601 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) locate()
    }

    override fun onStop() {
        weather.cancel()
        location?.cancel()
        super.onStop()
    }
}
