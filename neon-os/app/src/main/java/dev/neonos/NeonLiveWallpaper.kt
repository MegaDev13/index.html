package dev.neonos

import android.graphics.*
import android.os.*
import android.provider.Settings
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

class NeonLiveWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = CircuitEngine()

    inner class CircuitEngine : Engine() {
        private val thread = HandlerThread("neon-live-wallpaper").apply { start() }
        private val handler = Handler(thread.looper)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val prefs = getSharedPreferences("neon", 0)
        private var image: Bitmap? = null
        private var imageTheme = ""
        @Volatile private var visible = false
        @Volatile private var offset = .5f
        private val frame = Runnable { draw() }

        override fun onVisibilityChanged(shown: Boolean) {
            visible = shown
            handler.removeCallbacks(frame)
            if (shown) handler.post(frame)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            handler.post {
                image?.recycle()
                image = null
                handler.removeCallbacks(frame)
                if (visible) draw()
            }
        }

        override fun onOffsetsChanged(x: Float, y: Float, xs: Float, ys: Float, px: Int, py: Int) {
            offset = x.coerceIn(0f, 1f)
            handler.removeCallbacks(frame)
            if (visible) handler.post(frame)
        }

        private fun draw() {
            if (!visible || !surfaceHolder.surface.isValid) return
            val mode = prefs.getString("performance", "balanced")
            val motion = mode != "economy" && !prefs.getBoolean("saver", false) && !prefs.getBoolean("instant", false) &&
                !getSystemService(PowerManager::class.java).isPowerSaveMode &&
                Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0
            val canvas = runCatching { surfaceHolder.lockCanvas() }.getOrNull() ?: return
            try {
                val theme = Themes.current(this@NeonLiveWallpaper)
                val key = "${theme.id}:${theme.accent}"
                if (image == null || key != imageTheme) {
                    image?.recycle()
                    image = NeonArt.wallpaper(canvas.width.coerceIn(1, 1600), canvas.height.coerceIn(1, 3400), theme.accent, theme.id)
                    imageTheme = key
                }
                val border = canvas.width * .035f
                val shift = (offset - .5f) * border
                canvas.drawBitmap(image!!, null, RectF(-border + shift, 0f, canvas.width + border + shift, canvas.height.toFloat()), paint)
                if (motion) {
                    val phase = (SystemClock.uptimeMillis() % 4800) / 4800f
                    paint.color = theme.accent
                    paint.alpha = (110 * (1 - phase)).toInt()
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    canvas.drawCircle(canvas.width * .52f, canvas.height * .53f, canvas.width * (.18f + phase * .28f), paint)
                    paint.style = Paint.Style.FILL
                    paint.alpha = 255
                }
            } finally {
                runCatching { surfaceHolder.unlockCanvasAndPost(canvas) }
            }
            if (visible && motion) handler.postDelayed(frame, if (mode == "full") 33 else 125)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            visible = false
            handler.removeCallbacksAndMessages(null)
            handler.post { image?.recycle(); image = null; thread.quitSafely() }
            super.onDestroy()
        }
    }
}
