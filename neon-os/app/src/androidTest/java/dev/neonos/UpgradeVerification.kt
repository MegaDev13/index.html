package dev.neonos

import android.app.Activity
import android.app.Instrumentation
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.SystemClock
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class UpgradeVerification(private val test: Instrumentation, private val directory: File) {
    private val context get() = test.targetContext
    private val prefs get() = context.getSharedPreferences("neon", 0)
    private var assertions = 0
    private fun check(value: Boolean, message: String) {
        assertions++
        if (!value) throw AssertionError(message)
    }
    private fun main(block: () -> Unit) = test.onUi(block)
    private fun find(view: View, match: (View) -> Boolean): View? {
        if (match(view)) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index), match)?.let { return it }
        return null
    }
    private fun text(activity: Activity, value: String) = find(activity.window.decorView) { it is TextView && it.text.toString() == value }
    private fun open(type: Class<out Activity>, clear: Boolean = false): Activity {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or if (clear) Intent.FLAG_ACTIVITY_CLEAR_TASK else 0
        return test.startActivitySync(Intent(context, type).addFlags(flags)).also {
            test.waitForIdleSync()
            Thread.sleep(650)
        }
    }
    private fun shot(name: String) {
        test.waitForIdleSync()
        Thread.sleep(550)
        check(test.uiAutomation.rootInActiveWindow?.packageName?.toString() == context.packageName, "Foreign window over $name")
        val image = test.uiAutomation.takeScreenshot()
        check(image != null, "Screenshot unavailable")
        image!!.let { File(directory, "$name.png").outputStream().use { stream -> it.compress(Bitmap.CompressFormat.PNG, 100, stream) }; it.recycle() }
    }
    private fun swipe(x: Float, y: Float, dx: Float, dy: Float, hold: Long = 0, window: Activity? = null) {
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, px: Float, py: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, px, py, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                if (window == null) test.sendPointerSync(event)
                else main { window.dispatchTouchEvent(event) }
            } finally {
                event.recycle()
            }
        }
        send(MotionEvent.ACTION_DOWN, x, y)
        Thread.sleep(hold)
        for (step in 1..10) {
            send(MotionEvent.ACTION_MOVE, x + dx*step/10, y + dy*step/10)
            Thread.sleep(30)
        }
        send(MotionEvent.ACTION_UP, x+dx, y+dy)
        Thread.sleep(650)
        test.waitForIdleSync()
    }

    fun run(): Int {
        val evidence = JSONObject()
        val apps = AppRepository(context).load()
        check(apps.size >= 3, "Launchable apps missing")
        val selected = apps.take(3)
        main { Themes.select(context, Themes.NEON) }
        val weather = WeatherClient(context)
        val live = runCatching {
            val city = weather.search("Capim Grosso").first { it.name.contains("Capim Grosso") }
            weather.select(city)
            weather.refresh()
        }
        val reading = live.getOrNull()
        evidence.put("live_weather_android", reading != null)
        if (reading != null) File(directory, "weather-live.json").writeText(reading.json().toString(2))
        else File(directory, "weather-live-error.txt").writeText(live.exceptionOrNull().toString())
        val savedFavorites = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()
        prefs.edit().remove("favorites").commit()
        var home = open(MainActivity::class.java, true)
        main {
            val hud = find(home.window.decorView) { it is HudHomeView } as? HudHomeView
            check(hud != null, "HUD missing")
            check(find(home.window.decorView) { it is WorldMapView } != null, "World map removed")
            check(find(home.window.decorView) { it is TextView && it.text.toString().startsWith("BATERIA") } != null, "Battery missing")
            if (reading != null) {
                check(hud!!.world.city == reading.city, "Marker differs from weather city")
                check(hud.temperature.text.toString() == reading.degrees, "Temperature ring differs from weather")
                check(find(home.window.decorView) { it is TextView && it.text.toString().contains(reading.degrees) && it.text.toString().contains("Open-Meteo") } != null,
                    "Temperature missing from map")
                check(weather.cached()?.degrees == reading.degrees, "Weather cache did not preserve actual reading")
            }
        }
        shot("v2-hud-map-weather")
        val hudBounds = Rect()
        main { check(find(home.window.decorView) { it is HudHomeView }!!.getGlobalVisibleRect(hudBounds), "HUD invisible") }
        val gestureX = hudBounds.centerX().toFloat()
        val gestureY = hudBounds.top + hudBounds.height() * .65f
        val drawer = find(home.window.decorView) { it.tag == "neon:swipe-drawer" }!!
        val distance = drawer.height * .42f
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, y: Float, elapsed: Long) {
            val event = MotionEvent.obtain(downTime, downTime + elapsed, action, gestureX, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { main { home.dispatchTouchEvent(event) } } finally { event.recycle() }
        }
        main { check(drawer.visibility == View.INVISIBLE && drawer.height > 0, "Drawer was not laid out ahead of the gesture") }
        send(MotionEvent.ACTION_DOWN, gestureY, 0)
        send(MotionEvent.ACTION_MOVE, gestureY - distance / 2, 160)
        main {
            check(drawer.visibility == View.VISIBLE, "Drawer only appeared after release")
            check(kotlin.math.abs(drawer.translationY - (drawer.height - distance / 2)) < 1f, "Drawer did not follow initial drag")
        }
        send(MotionEvent.ACTION_MOVE, gestureY - distance, 320)
        main {
            check(kotlin.math.abs(drawer.translationY - (drawer.height - distance)) < 1f, "Drawer did not follow continued drag")
        }
        shot("v031-drawer-drag")
        send(MotionEvent.ACTION_MOVE, gestureY - distance / 4, 500)
        main {
            check(kotlin.math.abs(drawer.translationY - (drawer.height - distance / 4)) < 1f, "Drawer did not follow reversal")
        }
        send(MotionEvent.ACTION_UP, gestureY - distance / 4, 750)
        Thread.sleep(400)
        main { check(drawer.visibility == View.INVISIBLE, "Short drag left drawer open") }

        swipe(gestureX, gestureY, 0f, -hudBounds.height() * .25f)
        main {
            check(drawer.visibility == View.VISIBLE && drawer.translationY == 0f, "Swipe up did not open the same drawer")
            check(text(home, "Aplicativos") != null, "Drawer title missing")
        }
        shot("v2-gesture-drawer")
        evidence.put("swipe_up", true).put("drawer_follows_finger_before_release", true).put("short_drawer_swipe_returns_home", true)
        prefs.edit().putStringSet("favorites", savedFavorites).commit()

        val store = IconStore(context)
        val inventory = File(directory, "NEON_ICON_EXPORT-emulator.zip")
        check(inventory.outputStream().use { store.export(it, selected) } == 3, "Inventory selection not honored")
        ZipFile(inventory).use { zip ->
            val manifest = JSONObject(zip.getInputStream(zip.getEntry("NEON_ICON_EXPORT/apps.json")).reader().readText())
            check(manifest.getInt("version") == 1, "Wrong manifest version")
            val list = manifest.getJSONArray("apps")
            check(list.length() == selected.size, "Unexpected inventory records")
            for (i in selected.indices) {
                val entry = list.getJSONObject(i)
                check(entry.getString("package") == selected[i].component.packageName, "Package mapping differs")
                check(zip.getEntry("NEON_ICON_EXPORT/" + entry.getString("icon")) != null, "Original icon missing")
            }
        }
        val example = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        NeonIcons.drawable("shield", Themes.spec(Themes.NEON)).apply { setBounds(0, 0, 192, 192); draw(Canvas(example)) }
        val icon = ByteArrayOutputStream().also { example.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        example.recycle()
        fun zip(entries: List<Pair<String, ByteArray>>) = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                for ((name, value) in entries) { zip.putNextEntry(ZipEntry(name)); zip.write(value); zip.closeEntry() }
            }
        }.toByteArray()
        val manifest = JSONObject().put("version", 1).put("icons", JSONArray().put(JSONObject()
            .put("package", selected[0].component.packageName).put("theme", "all")
            .put("file", "icons/shield.png").put("label", "Escudo NEON / exemplo")))
        val bytes = zip(listOf("NEON_ICON_PACK/manifest.json" to manifest.toString().toByteArray(), "NEON_ICON_PACK/icons/shield.png" to icon))
        File(directory, "NEON_ICON_PACK-example.zip").writeBytes(bytes)
        val draft = store.stage(ByteArrayInputStream(bytes))
        check(draft.entries.size == 1, "Validated pack missing")
        val studio = open(IconStudioActivity::class.java) as IconStudioActivity
        shot("v2-icon-studio")
        main { studio.preview(draft) }
        shot("v2-icon-pack-preview")
        val oldIcons = prefs.getString("icon_overrides", null)
        val previousIcons = prefs.getString("icon_previous", null)
        val applyButton = test.uiAutomation.rootInActiveWindow
            ?.findAccessibilityNodeInfosByText("Aplicar selecionados")?.firstOrNull { it.isClickable }
        check(applyButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true, "Icon preview apply unavailable")
        val iconDeadline = SystemClock.elapsedRealtime() + 6000
        while (store.state(selected[0], Themes.NEON) != "Imagem personalizada" &&
            SystemClock.elapsedRealtime() < iconDeadline) Thread.sleep(120)
        check(Themes.ids.all { store.state(selected[0], it) == "Imagem personalizada" }, "Imported icon missing in a theme")
        store.choose(selected[0], Themes.NEON, "original")
        check(store.drawable(selected[0], Themes.spec(Themes.NEON)) == null, "Original icon not restored")
        check(store.restorePrevious() && store.drawable(selected[0], Themes.spec(Themes.NEON)) != null, "Previous icon set lost")
        val bad = zip(listOf("NEON_ICON_PACK/../bad.png" to icon))
        check(runCatching { store.stage(ByteArrayInputStream(bad)) }.isFailure, "Unsafe ZIP accepted")
        main {
            studio.finish()
            prefs.edit().putString("icon_overrides", oldIcons).putString("icon_previous", previousIcons).putLong("icon_revision", store.revision + 1).commit()
        }
        evidence.put("icon_inventory_import_restore", true)

        val samples = Bitmap.createBitmap(900, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(samples)
        canvas.drawColor(Color.BLACK)
        val glyphs = listOf("whatsapp", "plane", "camera", "calendar", "music", "bank", "ai", "grid")
        for ((row, id) in Themes.ids.withIndex()) for ((column, glyph) in glyphs.withIndex()) {
            NeonIcons.drawable(glyph, Themes.spec(id)).apply {
                setBounds(12+column*111, 12+row*158, 106+column*111, 106+row*158)
                draw(canvas)
            }
            canvas.drawText(glyph, 16f+column*111, 135f+row*158, Paint().apply { color = Color.WHITE; textSize = 15f })
        }
        File(directory, "v2-original-icons.png").outputStream().use { samples.compress(Bitmap.CompressFormat.PNG, 100, it) }
        samples.recycle()

        val wallpaper = Bitmap.createBitmap(240, 540, Bitmap.Config.ARGB_8888)
        Canvas(wallpaper).apply {
            drawColor(Color.rgb(15, 6, 27))
            val paint = Paint().apply { color = Color.rgb(173, 63, 200); strokeWidth = 2f }
            for (i in 0..8) drawLine(i*30f, 0f, i*30f, 540f, paint)
            paint.color = Color.WHITE
            paint.textSize = 18f
            drawText("WALLPAPER / TESTE", 10f, 280f, paint)
        }
        val imageFile = File(context.cacheDir, "neon-wallpaper-test.png")
        imageFile.outputStream().use { wallpaper.compress(Bitmap.CompressFormat.PNG, 100, it) }
        for (format in listOf(Bitmap.CompressFormat.PNG, Bitmap.CompressFormat.JPEG, Bitmap.CompressFormat.WEBP_LOSSLESS)) {
            val image = ByteArrayOutputStream().also { wallpaper.compress(format, 95, it) }.toByteArray()
            WallpaperStore.read(ByteArrayInputStream(image)).let { check(it.width == 240, "Wallpaper decode failed"); it.recycle() }
        }
        val editor = open(WallpaperActivity::class.java) as WallpaperActivity
        main { editor.onActivityResult(501, Activity.RESULT_OK, Intent().setData(Uri.fromFile(imageFile))) }
        Thread.sleep(850)
        shot("v2-wallpaper-preview")
        main { check(text(editor, "SALVAR NA HOME NEON")!!.performClick(), "Save control not wired") }
        val deadline = SystemClock.elapsedRealtime() + 6000
        while (!WallpaperStore.file(context, Themes.NEON).isFile && SystemClock.elapsedRealtime() < deadline) Thread.sleep(120)
        check(WallpaperStore.file(context, Themes.NEON).isFile, "Wallpaper not persisted")
        val manager = WallpaperManager.getInstance(context)
        val previousId = manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        manager.setBitmap(wallpaper, null, false, WallpaperManager.FLAG_SYSTEM)
        wallpaper.recycle()
        check(manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM) != previousId, "Android wallpaper did not change")
        main { editor.finish() }
        home = open(MainActivity::class.java, true)
        shot("v2-custom-wallpaper-home")
        prefs.edit().putBoolean("wallpaper_use_${Themes.NEON}", false).commit()
        evidence.put("wallpaper_formats_preview_save_android", true)

        val layout = HomeLayout(context)
        val savedLayout = prefs.getString("home_layout", null)
        val folder = layout.createFolder("Ferramentas", selected.take(2).map { it.key }, 0)
        check(layout.folderApps(folder).size == 2, "Folder membership failed")
        home = open(MainActivity::class.java, true)
        main { check(text(home, "▦  Ferramentas")!!.performClick(), "Folder control failed") }
        shot("v2-folder")
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        main { layout.dissolve(folder, 0); selected.forEach { layout.move(it.key, 1) } }
        home = open(MainActivity::class.java, true)
        swipe(home.window.decorView.width * .72f, home.window.decorView.height * .48f, -home.window.decorView.width * .5f, 0f)
        main {
            check(text(home, "HOME / PÁGINA 2") != null, "Horizontal swipe did not change page")
            prefs.edit().putBoolean("home_edit", true).commit()
            text(home, "HOME")!!.performClick()
        }
        test.waitForIdleSync()
        Thread.sleep(400)
        val source = Rect()
        val target = Rect()
        val dragDeadline = SystemClock.elapsedRealtime() + 8000
        var ready: Boolean
        do {
            ready = false
            main {
                val first = find(home.window.decorView) { it.contentDescription == "Abrir ${selected[0].label}" }
                val last = find(home.window.decorView) { it.contentDescription == "Abrir ${selected[2].label}" }
                ready = first?.isAttachedToWindow == true && last?.isAttachedToWindow == true &&
                    first.getGlobalVisibleRect(source) && last.getGlobalVisibleRect(target) && source != target
            }
            if (!ready) Thread.sleep(100)
        } while (!ready && SystemClock.elapsedRealtime() < dragDeadline)
        check(ready, "Drag targets are not distinct and visible")
        swipe(source.centerX().toFloat(), source.centerY().toFloat(),
            (target.centerX()-source.centerX()).toFloat(), (target.centerY()-source.centerY()).toFloat(),
            ViewConfiguration.getLongPressTimeout().toLong() + 400, home)
        check(HomeLayout(context).page(1) == listOf(selected[1].key, selected[0].key, selected[2].key),
            "Drag did not persist item order: ${layout.page(1)} from $source to $target")
        shot("v2-drag-page")
        evidence.put("folders_horizontal_gesture_drag", true).put("drag_input", "MotionEvents dispatched to the attached Activity")
        prefs.edit().putBoolean("home_edit", false).putString("home_layout", savedLayout).commit()

        val terminal = open(TerminalActivity::class.java) as TerminalActivity
        main {
            terminal.execute("battery")
            check(find(terminal.window.decorView) { it is TextView && it.text.toString().startsWith("Bateria: ") } != null, "Battery command failed")
            terminal.execute("rm -rf /")
            check(text(terminal, "Comando não permitido. Use help.") != null, "Terminal accepted system command")
            terminal.execute("help")
        }
        shot("v2-terminal-console")
        main { terminal.finish(); Themes.select(context, Themes.TERMINAL) }
        open(MainActivity::class.java, true)
        check(EventJournal.snapshot().any { it.kind == "THEME" && it.detail == Themes.TERMINAL }, "Theme event absent")
        shot("v2-dark-terminal-events")
        evidence.put("real_terminal_events", true)
        check(context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED, "Location authorized automatically")
        check(context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED, "Bluetooth authorized automatically")
        evidence.put("physical_poco", false).put("assertions", assertions)
        File(directory, "v2-verification.json").writeText(evidence.toString(2))
        return assertions
    }
}
