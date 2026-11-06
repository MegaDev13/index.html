package dev.neonos

import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.util.AtomicFile
import android.view.*
import android.widget.FrameLayout
import android.widget.SeekBar
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer

data class WallpaperCrop(val zoom: Float = 1f, val x: Float = 0f, val y: Float = 0f, val brightness: Float = 1f, val darken: Float = 0f) {
    fun paint(canvas: Canvas, bitmap: Bitmap, paint: Paint) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val scale = maxOf(w / bitmap.width, h / bitmap.height) * zoom
        val imageWidth = bitmap.width * scale
        val imageHeight = bitmap.height * scale
        val left = (w - imageWidth) * (1 + x.coerceIn(-1f, 1f)) / 2
        val top = (h - imageHeight) * (1 + y.coerceIn(-1f, 1f)) / 2
        val matrix = ColorMatrix().apply { setScale(brightness, brightness, brightness, 1f) }
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + imageWidth, top + imageHeight), paint)
        paint.colorFilter = null
        if (darken > 0) canvas.drawColor(Color.argb((darken * 225).toInt(), 0, 0, 0))
    }
}

object WallpaperStore {
    fun file(context: Context, theme: String) = File(context.filesDir, "wallpaper-$theme.jpg")
    fun load(context: Context, theme: String): Bitmap? = BitmapFactory.decodeFile(file(context, theme).path)

    fun read(input: InputStream): Bitmap {
        val bytes = ArchiveSafety.read(input, 24 * 1024 * 1024)
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            require(info.mimeType in setOf("image/jpeg", "image/png", "image/webp") && w in 64..12000 && h in 64..12000 &&
                w.toLong() * h <= 48000000) { "Use PNG, JPEG ou WebP até 24 MiB e 48 megapixels." }
            val ratio = minOf(1f, 3200f / maxOf(w, h))
            decoder.setTargetSize((w * ratio).toInt(), (h * ratio).toInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    fun save(context: Context, theme: String, bitmap: Bitmap) {
        val file = AtomicFile(file(context, theme))
        val stream = file.startWrite()
        try {
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream))
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
        context.getSharedPreferences("neon", 0).edit().putBoolean("wallpaper_use_$theme", true)
            .putBoolean("system_wallpaper", false).putLong("wallpaper_revision", System.currentTimeMillis()).apply()
        EventJournal.record("WALLPAPER", "Imagem salva no tema $theme")
    }
}

class WallpaperPreview(context: Context) : View(context) {
    var image: Bitmap? = null
    var crop = WallpaperCrop()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var lastX = 0f
    private var lastY = 0f
    private val zoom = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            crop = crop.copy(zoom = (crop.zoom * detector.scaleFactor).coerceIn(1f, 4f))
            invalidate()
            return true
        }
    })
    init { contentDescription = "Prévia do wallpaper. Arraste para reposicionar e use pinça para zoom." }
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val bitmap = image ?: return
        val layer = canvas.save()
        // A view's Canvas reports the window dimensions. Paint in the preview's local bounds.
        val scale = maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height) * crop.zoom
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val x = (width - w) * (1 + crop.x) / 2
        val y = (height - h) * (1 + crop.y) / 2
        canvas.clipRect(0, 0, width, height)
        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(crop.brightness, crop.brightness, crop.brightness, 1f) })
        canvas.drawBitmap(bitmap, null, RectF(x, y, x + w, y + h), paint)
        paint.colorFilter = null
        if (crop.darken > 0) canvas.drawColor(Color.argb((crop.darken * 225).toInt(), 0, 0, 0))
        canvas.restoreToCount(layer)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        zoom.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> if (!zoom.isInProgress) {
                crop = crop.copy(x = (crop.x - (event.x - lastX) / width * 2).coerceIn(-1f, 1f),
                    y = (crop.y - (event.y - lastY) / height * 2).coerceIn(-1f, 1f))
                lastX = event.x
                lastY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
}

class WallpaperActivity : NeonPanel() {
    private lateinit var preview: WallpaperPreview
    private var busy = false
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        screen("WALLPAPER / STUDIO")
        add(text("Salve uma imagem para cada tema. Arraste a prévia para ajustar o recorte.", 13f))
        add(button("ESCOLHER JPG / PNG / WEBP") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png", "image/webp")), 501)
        })
        preview = WallpaperPreview(this)
        add(FrameLayout(this).apply {
            addView(preview, FrameLayout.LayoutParams(dp(168), dp(374), Gravity.CENTER))
        })
        slider("Zoom", 100, 400, 100) { preview.crop = preview.crop.copy(zoom = it / 100f) }
        slider("Brilho", 50, 150, 100) { preview.crop = preview.crop.copy(brightness = it / 100f) }
        slider("Escurecer", 0, 100, 0) { preview.crop = preview.crop.copy(darken = it / 100f) }
        slider("Opacidade dos painéis", 20, 100, preferences.getInt("panel_opacity", 85)) {
            preferences.edit().putInt("panel_opacity", it).apply()
        }
        add(button("SALVAR NA HOME NEON") { save(0) })
        add(button("APLICAR AO ANDROID") {
            AlertDialog.Builder(this).setTitle("Escolha onde aplicar").setItems(arrayOf("Tela inicial", "Bloqueio", "Ambas")) { _, index ->
                save(intArrayOf(WallpaperManager.FLAG_SYSTEM, WallpaperManager.FLAG_LOCK, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)[index])
            }.setNegativeButton("Cancelar", null).show()
        })
        add(button("USAR FUNDO ATUAL DO ANDROID") {
            preferences.edit().putBoolean("system_wallpaper", true).apply()
            message("Fundo do Android selecionado para a Home NEON.")
        })
        add(button("RESTAURAR FUNDO NEON DO TEMA") {
            preferences.edit().putBoolean("system_wallpaper", false).putBoolean("wallpaper_use_${style.id}", false).apply()
            message("Fundo NEON selecionado. Sua imagem continua guardada.")
        })
        add(button("NEON CIRCUIT LIVE / PRÉVIA ANDROID") {
            runCatching {
                startActivityForResult(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this, NeonLiveWallpaper::class.java)), 502)
            }.onFailure { message("Prévia de live wallpaper indisponível.") }
        })
        val theme = style.id
        worker.execute { val bitmap = WallpaperStore.load(this, theme); whenAlive { preview.image = bitmap; preview.invalidate() } }
    }

    private fun slider(label: String, min: Int, max: Int, initial: Int, changed: (Int) -> Unit) {
        val value = text("$label · $initial%")
        add(value)
        add(SeekBar(this).apply {
            this.max = max - min
            progress = initial - min
            contentDescription = label
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    value.text = "$label · ${progress + min}%"
                    changed(progress + min)
                    preview.invalidate()
                }
            })
        })
    }

    private fun save(flags: Int) {
        val image = preview.image ?: run { message("Escolha uma imagem."); return }
        if (busy) return
        busy = true
        val crop = preview.crop
        val theme = style.id
        worker.execute {
            val bitmap = Bitmap.createBitmap(1220, 2712, Bitmap.Config.ARGB_8888)
            val result = runCatching {
                crop.paint(Canvas(bitmap), image, Paint(Paint.FILTER_BITMAP_FLAG))
                WallpaperStore.save(this, theme, bitmap)
                if (flags != 0) {
                    WallpaperManager.getInstance(this).setBitmap(bitmap, null, false, flags)
                    EventJournal.record("WALLPAPER", "Imagem aplicada ao Android")
                }
            }
            bitmap.recycle()
            whenAlive {
                busy = false
                message(result.exceptionOrNull()?.message ?: if (flags == 0) "Wallpaper salvo no tema." else "Wallpaper aplicado ao Android.")
            }
        }
    }

    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        super.onActivityResult(code, result, data)
        if (result != RESULT_OK) return
        if (code == 502) {
            preferences.edit().putBoolean("system_wallpaper", true).apply()
            EventJournal.record("WALLPAPER", "Live wallpaper confirmado no Android")
        }
        val uri = data?.data ?: return
        if (code == 501) worker.execute {
            val result = runCatching { contentResolver.openInputStream(uri)?.use { WallpaperStore.read(it) } ?: error("Imagem indisponível.") }
            whenAlive {
                result.onSuccess { preview.image = it; preview.crop = WallpaperCrop(); preview.invalidate() }
                    .onFailure { message(it.message ?: "Imagem rejeitada.") }
            }
        }
    }
}
