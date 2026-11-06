package dev.neonos

import android.Manifest
import android.animation.ValueAnimator
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import android.provider.Settings
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil

class WorldMapView(context: Context) : View(context) {
    var city: WeatherCity? = null
        set(value) {
            field = value
            contentDescription = value?.let { "Mapa-múndi. Marcador em ${it.name}" } ?: "Mapa-múndi. Escolha uma cidade para marcar."
            invalidate()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val contours = listOf(
        floatArrayOf(-168f,72f,-151f,60f,-134f,58f,-124f,49f,-123f,37f,-111f,28f,-96f,17f,-85f,10f,-79f,9f,-84f,22f,-81f,30f,-68f,45f,-53f,50f,-59f,61f,-80f,73f,-111f,74f),
        floatArrayOf(-81f,12f,-68f,10f,-60f,5f,-50f,1f,-35f,-6f,-40f,-22f,-51f,-35f,-66f,-55f,-73f,-49f,-76f,-22f,-81f,-3f),
        floatArrayOf(-17f,36f,8f,37f,32f,31f,43f,12f,51f,11f,43f,-11f,35f,-26f,18f,-35f,11f,-20f,5f,1f,-11f,7f,-17f,20f),
        floatArrayOf(-10f,36f,-9f,44f,5f,50f,10f,59f,25f,70f,42f,66f,64f,70f,100f,76f,140f,70f,179f,64f,162f,54f,144f,48f,135f,34f,120f,22f,105f,8f,98f,20f,80f,7f,67f,24f,48f,30f,35f,41f,22f,39f),
        floatArrayOf(113f,-22f,124f,-14f,135f,-12f,142f,-18f,153f,-28f,145f,-39f,126f,-35f,114f,-34f),
        floatArrayOf(-53f,59f,-40f,62f,-20f,76f,-41f,83f,-61f,76f),
        floatArrayOf(46f,-13f,50f,-16f,48f,-25f,44f,-24f),
        floatArrayOf(166f,-34f,178f,-38f,170f,-47f,165f,-45f)
    )

    override fun onDraw(canvas: Canvas) {
        val theme = Themes.current(context)
        paint.color = theme.accent
        paint.alpha = 35
        paint.strokeWidth = 1f
        for (i in 1..5) canvas.drawLine(width * i / 6f, 0f, width * i / 6f, height.toFloat(), paint)
        for (i in 1..3) canvas.drawLine(0f, height * i / 4f, width.toFloat(), height * i / 4f, paint)
        for (points in contours) {
            val path = Path()
            for (i in points.indices step 2) {
                val x = MapProjection.x(points[i].toDouble()) * width
                val y = MapProjection.y(points[i + 1].toDouble()) * height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            paint.style = Paint.Style.FILL
            paint.alpha = 110
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE
            paint.alpha = 240
            paint.strokeWidth = 1.1f * resources.displayMetrics.density
            canvas.drawPath(path, paint)
        }
        city?.let {
            val x = MapProjection.x(it.longitude) * width
            val y = MapProjection.y(it.latitude) * height
            val density = resources.displayMetrics.density
            paint.alpha = 255
            paint.color = theme.text
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, 2.6f * density, paint)
            paint.color = theme.accent
            paint.style = Paint.Style.STROKE
            canvas.drawCircle(x, y, 6f * density, paint)
            canvas.drawLine(x - 11 * density, y, x + 11 * density, y, paint)
            canvas.drawLine(x, y - 11 * density, x, y + 11 * density, paint)
        }
    }
}

class HudHomeView(
    context: Context,
    apps: List<AppEntry>,
    loadIcon: (AppEntry, ImageView) -> Unit,
    openApp: (AppEntry) -> Unit,
    appMenu: (AppEntry) -> Unit,
    route: (String) -> Unit,
    dockAction: (Int, Boolean) -> Unit
) : ViewGroup(context) {
    private data class Box(val view: View, val x: Float, val y: Float, val width: Float, val height: Float)
    private val boxes = mutableListOf<Box>()
    private val style = Themes.current(context)
    private val prefs = context.getSharedPreferences("neon", 0)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val showRing = prefs.getBoolean("hud_weather", true)
    private val showTerminal = prefs.getBoolean("hud_terminal", style.mono)
    private var level = -1
    private var charging = false
    private var phase = 0f
    private var chargeMotion: ValueAnimator? = null
    val world = WorldMapView(context)
    val dockViews = mutableListOf<ImageView>()
    val clock = label("—", 42f, style.text).apply { Themes.tag(this, "clock", style) }
    val atlas = label("ATLAS / OFICIAL", 13f, style.accent).apply { Themes.tag(this, "atlas-home", style) }
    private val battery = label("BATERIA —", 15f, style.text)
    private val power = label("", 11f, style.muted)
    private val calendar = label("", 15f, style.text)
    private val city = label("SELECIONAR CIDADE", 12f, style.accent)
    private val mapReading = label("", 11f, style.muted)
    val temperature = label("—°C", 32f, style.accent)
    private val condition = label("", 12f, style.text)
    private val metrics = label("", 11f, style.muted)
    private val connection = label("", 11f, style.accent)
    private val log = label("", 10.5f, style.accent).apply { typeface = Typeface.MONOSPACE; gravity = Gravity.START or Gravity.CENTER_VERTICAL }
    private val observe = { updateJournal() }

    init {
        setWillNotDraw(false)
        place(battery, 35f, 23f, 143f, 38f)
        place(power, 35f, 61f, 143f, 31f)
        place(calendar, 270f, 15f, 130f, 97f)
        battery.setOnClickListener { route("battery") }
        calendar.setOnClickListener { route("calendar") }
        if (prefs.getBoolean("hud_map", true)) {
            place(world, 100f, 126f, 220f, 113f)
            place(city, 76f, 238f, 268f, 30f)
            place(mapReading, 80f, 269f, 260f, 36f)
            world.setOnClickListener { route("weather") }
            city.setOnClickListener { route("weather") }
        }
        if (showRing) {
            place(temperature, 138f, 348f, 144f, 61f)
            place(condition, 98f, 451f, 224f, 37f)
            place(metrics, 86f, 490f, 248f, 42f)
            temperature.setOnClickListener { route("weather") }
        }
        place(clock, 104f, 526f, 212f, 74f)
        place(atlas, 128f, 602f, 164f, 43f)
        atlas.background = style.background(context, true)
        atlas.setOnClickListener { route("atlas") }
        atlas.setOnLongClickListener { route("atlas-panel"); true }
        place(label("QUICK ACCESS", 9f, style.muted), 14f, 597f, 111f, 25f)
        val slots = listOf(18f to 327f, 18f to 401f, 350f to 327f, 350f to 401f, 20f to 645f, 75f to 674f, 20f to 703f, 129f to 702f)
        apps.take(if (showTerminal) 4 else 8).forEachIndexed { index, app ->
            val icon = ImageView(context).apply {
                contentDescription = "Abrir ${app.label}"
                isFocusable = true
                setOnClickListener {
                    if (prefs.getBoolean("haptic", true)) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    openApp(app)
                }
                setOnLongClickListener { appMenu(app); true }
            }
            place(icon, slots[index].first, slots[index].second, 51f, 51f)
            loadIcon(app, icon)
        }
        place(connection, if (showTerminal) 292f else 248f, if (showTerminal) 594f else 652f, if (showTerminal) 120f else 156f, 70f)
        connection.setOnClickListener { route("connectivity") }
        if (showTerminal) {
            place(log, 20f, 657f, 381f, 68f)
            log.background = style.background(context)
            log.setPadding(8, 4, 8, 4)
            log.setOnClickListener { route("terminal") }
        }
        val symbols = listOf("phone", "chat", "grid", "ai", "settings")
        val descriptions = listOf("Telefone", "Mensagens", "Gaveta de aplicativos", "ATLAS", "Configuração")
        for (index in symbols.indices) {
            val icon = ImageView(context).apply {
                setImageDrawable(NeonIcons.drawable(symbols[index], style, shape="circle"))
                contentDescription = descriptions[index]
                isFocusable = true
                setOnClickListener { dockAction(index, false) }
                setOnLongClickListener { dockAction(index, true); true }
            }
            dockViews.add(icon)
            place(icon, 24f + 77 * index, 736f, 53f, 53f)
        }
        setOnLongClickListener { route("edit"); true }
        updateWeather(null, false)
    }

    private fun label(value: String, size: Float, color: Int) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        typeface = if(style.mono) Typeface.MONOSPACE else Typeface.create(style.font, Typeface.NORMAL)
        maxLines = 3
        includeFontPadding = false
    }

    private fun place(view: View, x: Float, y: Float, width: Float, height: Float) {
        boxes.add(Box(view, x, y, width, height))
        addView(view, LayoutParams(0, 0))
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val h = MeasureSpec.getSize(heightSpec)
        setMeasuredDimension(w, h)
        for (box in boxes) {
            box.view.measure(MeasureSpec.makeMeasureSpec((w * box.width / 420).toInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((h * box.height / 790).toInt(), MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (box in boxes) {
            val x = (width * box.x / 420).toInt()
            val y = (height * box.y / 790).toInt()
            box.view.layout(x, y, x + box.view.measuredWidth, y + box.view.measuredHeight)
        }
    }

    fun updateBattery(percent: Int, plugged: Boolean) {
        level = percent
        charging = plugged
        battery.text = if (percent < 0) "BATERIA —" else "BATERIA  $percent%"
        power.text = when { percent < 0 -> "SEM LEITURA"; plugged && percent == 100 -> "CARGA COMPLETA"; plugged -> "CARREGANDO"; else -> "EM USO" }
        syncChargeMotion()
        invalidate()
    }

    fun updateClockAndNetwork() {
        val now = Date()
        clock.text = SimpleDateFormat(if (prefs.getBoolean("clock_12", false)) "h:mm a" else "HH:mm", Locale.getDefault()).format(now)
        calendar.text = SimpleDateFormat("EEEE\ndd\nMMMM yyyy", Locale("pt", "BR")).format(now)
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.getNetworkCapabilities(manager.activeNetwork)
        val status = when {
            network?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WI-FI CONECTADO"
            network?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true -> "REDE DISPONÍVEL"
            else -> "OFFLINE"
        }
        val bluetooth = if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) "AUTORIZAR"
        else runCatching {
            val adapter = context.getSystemService(BluetoothManager::class.java).adapter
            if (adapter == null) "INDISPONÍVEL" else if (adapter.isEnabled) "LIGADO" else "DESLIGADO"
        }.getOrDefault("INDISPONÍVEL")
        connection.text = "$status\nBT: $bluetooth\nCONEXÕES ›"
        atlas.text = if (prefs.getBoolean("atlas_integrated", false)) "ATLAS / API" else "ATLAS / OFICIAL"
    }

    fun updateWeather(reading: WeatherReading?, cached: Boolean) {
        val location = reading?.city ?: WeatherClient(context).city()
        world.city = location
        city.text = location?.name ?: "SELECIONAR CIDADE"
        temperature.text = reading?.degrees ?: "—°C"
        condition.text = reading?.let { "${it.symbol}  ${it.condition}" } ?: "SEM DADOS METEOROLÓGICOS"
        metrics.text = reading?.let { "Sens. %.0f° · umid. %d%%\nMín %.0f° / máx %.0f°".format(it.apparent, it.humidity, it.low, it.high) }
            ?: "Toque para configurar"
        mapReading.text = reading?.let {
            val timestamp = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(it.fetched))
            "${it.degrees} · ${if (cached) "CACHE" else "consulta"} $timestamp\nOpen-Meteo"
        } ?: "Clima por Open-Meteo"
    }

    private fun updateJournal() { log.text = EventJournal.lines(2).ifBlank { "NEON TERMINAL / Sem eventos nesta sessão" } }

    private fun syncChargeMotion() {
        if (!charging || !isShown || !isAttachedToWindow || prefs.getString("performance", "balanced") != "full" ||
            prefs.getBoolean("instant", false) || prefs.getBoolean("saver", false) ||
            context.getSystemService(PowerManager::class.java).isPowerSaveMode ||
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) {
            stopMotion()
            return
        }
        if (chargeMotion == null) chargeMotion = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun actionAt(x:Float,y:Float):Boolean {
        val rect=Rect()
        return boxes.any{(it.view.isClickable||it.view.isLongClickable)&&it.view.getGlobalVisibleRect(rect)&&rect.contains(x.toInt(),y.toInt())}
    }
    fun stopMotion() { chargeMotion?.cancel(); chargeMotion = null }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); EventJournal.observers.add(observe); updateJournal() }
    override fun onDetachedFromWindow() { EventJournal.observers.remove(observe); stopMotion(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.scale(width / 420f, height / 790f)
        paint.color = style.accent
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.35f
        paint.alpha = 175
        fun trace(vararg nodes: Float) {
            val path = Path().apply {
                moveTo(nodes[0], nodes[1])
                for (i in 2 until nodes.size step 2) lineTo(nodes[i], nodes[i + 1])
            }
            canvas.drawPath(path, paint)
        }
        trace(8f,8f,8f,765f,25f,789f,395f,789f,412f,765f,412f,8f)
        trace(17f,16f,17f,126f,86f,171f,86f,282f,73f,299f,73f,454f,100f,486f,100f,544f,150f,586f,150f,599f)
        trace(403f,16f,403f,126f,334f,171f,334f,282f,347f,299f,347f,454f,320f,486f,320f,544f,270f,586f,270f,599f)
        trace(26f,116f,144f,116f,171f,88f,171f,18f)
        trace(249f,18f,249f,88f,276f,116f,394f,116f)
        trace(12f,730f,408f,730f)
        canvas.drawRoundRect(12f,310f,77f,465f,5f,5f,paint)
        canvas.drawRoundRect(343f,310f,408f,465f,5f,5f,paint)
        paint.alpha = 45
        for (i in 0..6) {
            val x = 103f + i * 35
            trace(x,150f,x,235f+i*7,x+10,250f+i*7,x+10,489f,x-6,520f,x-6,720f)
        }
        if (showRing) {
            paint.alpha = 100
            paint.strokeWidth = 2f
            canvas.drawCircle(210f, 379f, 70f, paint)
            canvas.drawCircle(210f, 379f, 60f, paint)
            paint.alpha = 255
            paint.strokeWidth = 5f
            for (i in 0..3) canvas.drawArc(143f,312f,277f,446f,i*90f+12,46f,false,paint)
        }
        for (i in 0..6) {
            paint.alpha = if (chargeMotion != null && i == (phase * 7).toInt()) 255 else if (level >= 0 && i < ceil(level * 7 / 100.0)) 215 else 45
            paint.strokeWidth = 1.8f
            val y = 26f + i * 11
            canvas.drawLine(22f, y, 35f, y + 5, paint)
        }
        paint.alpha = 255
        canvas.restore()
    }
}
