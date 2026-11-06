package dev.neonos

import android.graphics.*
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.sin

/** Package-specific artwork. Unmapped apps use an explicitly generic monogram. */
object NeonIcons {
    private val packages = mapOf(
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "plane" to listOf("org.telegram.messenger"),
        "social" to listOf("com.facebook.katana", "com.facebook.lite", "com.twitter.android", "com.reddit.frontpage"),
        "camera" to listOf("com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera", "com.instagram.android"),
        "phone" to listOf("com.android.dialer", "com.google.android.dialer", "com.android.phone"),
        "chat" to listOf("com.android.messaging", "com.google.android.apps.messaging", "com.discord", "org.thoughtcrime.securesms"),
        "browser" to listOf("com.android.chrome", "org.mozilla.firefox", "com.mi.globalbrowser", "com.microsoft.emmx", "com.brave.browser", "com.opera.browser"),
        "calendar" to listOf("com.android.calendar", "com.google.android.calendar"),
        "mail" to listOf("com.google.android.gm", "com.microsoft.office.outlook", "com.android.email"),
        "settings" to listOf("com.android.settings", "com.miui.securitycenter"),
        "music" to listOf("com.spotify.music", "com.miui.player", "deezer.android.app", "com.google.android.apps.youtube.music"),
        "video" to listOf("com.google.android.youtube", "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient", "com.disney.disneyplus", "com.zhiliaoapp.musically"),
        "gallery" to listOf("com.android.gallery3d", "com.google.android.apps.photos", "com.miui.gallery"),
        "files" to listOf("com.google.android.documentsui", "com.android.documentsui", "com.mi.android.globalFileexplorer", "com.google.android.apps.nbu.files"),
        "clock" to listOf("com.android.deskclock", "com.google.android.deskclock"),
        "calculator" to listOf("com.android.calculator2", "com.google.android.calculator", "com.miui.calculator"),
        "contacts" to listOf("com.android.contacts", "com.google.android.contacts"),
        "store" to listOf("com.android.vending", "com.xiaomi.mipicks", "com.mercadolibre", "com.shopee.br", "com.amazon.mShop.android.shopping"),
        "bank" to listOf("com.nu.production", "br.com.intermedium", "com.itau", "com.bradesco", "com.santander.app", "br.gov.caixa.tem", "br.com.bb.android", "com.picpay", "com.mercadopago.wallet"),
        "map" to listOf("com.google.android.apps.maps", "com.waze"),
        "ai" to listOf("com.openai.chatgpt", "com.google.android.apps.bard", "com.anthropic.claude"),
        "terminal" to listOf("dev.neonos.poco", "com.termux"),
        "book" to listOf("com.amazon.kindle", "com.google.android.apps.books", "com.adobe.reader", "com.miui.notes", "com.google.android.keep", "notion.id"),
        "cloud" to listOf("com.google.android.apps.docs", "com.microsoft.skydrive", "com.dropbox.android"),
        "game" to listOf("com.supercell.clashofclans", "com.dts.freefireth", "com.tencent.ig", "com.roblox.client", "com.mojang.minecraftpe")
    )
    private val byPackage = packages.flatMap { (glyph, names) -> names.map { it to glyph } }.toMap()
    val glyphs = packages.keys.toList() + listOf("shield", "grid")
    fun mapped(packageName: String) = byPackage[packageName]
    fun drawable(glyph: String, style: ThemeSpec, label: String = "", shape: String = "theme") =
        NeonGlyph(glyph, style, label, shape)
}

class NeonGlyph(
    val glyph: String,
    private val theme: ThemeSpec,
    private val label: String,
    private val shape: String
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun getIntrinsicWidth() = 192
    override fun getIntrinsicHeight() = 192
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(filter: ColorFilter?) {}
    @Deprecated("Framework") override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getConstantState(): ConstantState = object : ConstantState() {
        override fun newDrawable() = NeonGlyph(glyph, theme, label, shape)
        override fun getChangingConfigurations() = 0
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 100f, bounds.height() / 100f)
        val frame = framePath()
        paint.style = Paint.Style.FILL
        paint.color = if (theme.id == Themes.CORPORATE) Color.rgb(15, 18, 23) else Color.BLACK
        canvas.drawPath(frame, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.65f
        paint.color = theme.accent
        canvas.drawPath(frame, paint)
        paint.color = when (theme.id) {
            Themes.CORPORATE -> Color.rgb(207, 63, 67)
            Themes.TERMINAL -> theme.secondary
            else -> Color.rgb(178, 97, 255)
        }
        canvas.drawLine(18f, 18f, 34f, 18f, paint)
        canvas.drawLine(66f, 82f, 82f, 82f, paint)
        paint.color = theme.accent
        paint.strokeWidth = 3.2f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        symbol(canvas)
        canvas.restore()
    }

    private fun framePath() = Path().apply {
        val effective = if (shape == "theme") {
            if (theme.id == Themes.NEON) "hex" else "square"
        } else shape
        when (effective) {
            "circle" -> addCircle(50f, 50f, 46f, Path.Direction.CW)
            "hex" -> {
                for (i in 0..5) {
                    val angle = i * Math.PI / 3
                    val x = (50 + 46 * cos(angle)).toFloat()
                    val y = (50 + 46 * sin(angle)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            else -> {
                moveTo(7f, 15f); lineTo(15f, 7f); lineTo(93f, 7f); lineTo(93f, 85f)
                lineTo(85f, 93f); lineTo(7f, 93f); close()
            }
        }
    }

    private fun symbol(c: Canvas) {
        fun line(x: Float, y: Float, x2: Float, y2: Float) = c.drawLine(x, y, x2, y2, paint)
        fun box(x: Float, y: Float, x2: Float, y2: Float) = c.drawRoundRect(x, y, x2, y2, 3f, 3f, paint)
        fun ring(x: Float, y: Float, radius: Float) = c.drawCircle(x, y, radius, paint)
        fun trace(vararg points: Float) {
            val path = Path().apply {
                moveTo(points[0], points[1])
                for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
            }
            c.drawPath(path, paint)
        }
        when (glyph) {
            "whatsapp" -> {
                ring(50f, 47f, 25f)
                trace(33f, 65f, 29f, 76f, 44f, 70f)
                trace(40f, 36f, 35f, 43f, 44f, 55f, 57f, 62f, 65f, 56f, 56f, 50f, 51f, 54f, 43f, 47f, 46f, 42f, 40f, 36f)
            }
            "plane" -> trace(20f, 43f, 80f, 22f, 63f, 77f, 46f, 58f, 37f, 70f, 34f, 52f, 20f, 43f, 34f, 52f, 65f, 36f, 46f, 58f)
            "chat" -> {
                box(22f, 25f, 78f, 66f)
                trace(34f, 66f, 30f, 77f, 50f, 66f)
                line(34f, 39f, 65f, 39f)
                line(34f, 52f, 59f, 52f)
            }
            "phone" -> trace(29f, 23f, 23f, 37f, 30f, 55f, 46f, 71f, 63f, 78f, 76f, 70f, 62f, 58f, 53f, 64f, 36f, 47f, 42f, 37f, 29f, 23f)
            "camera" -> {
                box(22f, 32f, 78f, 72f)
                trace(34f, 32f, 39f, 23f, 61f, 23f, 67f, 32f)
                ring(50f, 51f, 13f); ring(69f, 41f, 1.5f)
            }
            "browser" -> {
                ring(50f, 50f, 27f)
                c.drawOval(38f, 23f, 62f, 77f, paint)
                line(23f, 50f, 77f, 50f); line(28f, 36f, 72f, 36f); line(28f, 64f, 72f, 64f)
            }
            "calendar" -> {
                box(24f, 28f, 76f, 77f)
                line(24f, 41f, 76f, 41f); line(36f, 22f, 36f, 34f); line(64f, 22f, 64f, 34f)
                for (x in 0..2) for (y in 0..1) ring(36f+x*14, 53f+y*12, 1.8f)
            }
            "mail" -> {
                box(22f, 29f, 78f, 72f)
                trace(24f, 32f, 50f, 53f, 76f, 32f)
            }
            "settings" -> {
                for (i in 0..7) {
                    c.save(); c.rotate(i * 45f, 50f, 50f); line(50f, 20f, 50f, 30f); c.restore()
                }
                ring(50f, 50f, 21f); ring(50f, 50f, 9f)
            }
            "music" -> {
                trace(39f, 66f, 39f, 31f, 69f, 24f, 69f, 61f)
                c.drawOval(24f, 59f, 39f, 73f, paint); c.drawOval(54f, 54f, 69f, 68f, paint)
                line(39f, 40f, 69f, 33f)
            }
            "video" -> { box(21f, 27f, 79f, 73f); trace(44f, 37f, 64f, 50f, 44f, 63f, 44f, 37f) }
            "gallery" -> { box(23f, 24f, 77f, 76f); ring(62f, 38f, 6f); trace(25f, 68f, 42f, 48f, 55f, 61f, 65f, 51f, 76f, 67f) }
            "files" -> {
                trace(22f, 76f, 22f, 28f, 43f, 28f, 50f, 36f, 78f, 36f, 78f, 76f, 22f, 76f)
                line(33f, 49f, 67f, 49f); line(33f, 61f, 58f, 61f)
            }
            "clock" -> { ring(50f, 50f, 27f); trace(50f, 31f, 50f, 50f, 65f, 57f) }
            "calculator" -> {
                box(29f, 21f, 71f, 79f); box(36f, 29f, 64f, 40f)
                for (x in 0..2) for (y in 0..2) ring(38f+x*12, 50f+y*10, 1.3f)
            }
            "contacts" -> { ring(50f, 37f, 11f); c.drawArc(28f, 52f, 72f, 87f, 180f, 180f, false, paint); line(28f, 70f, 72f, 70f) }
            "store" -> { box(25f, 36f, 75f, 77f); c.drawArc(37f, 22f, 63f, 52f, 180f, 180f, false, paint) }
            "bank" -> {
                trace(22f, 35f, 50f, 20f, 78f, 35f, 22f, 35f)
                for (i in 0..2) line(32f+i*18, 42f, 32f+i*18, 68f)
                line(22f, 76f, 78f, 76f)
            }
            "map" -> { trace(50f, 78f, 29f, 48f, 28f, 34f, 38f, 23f, 62f, 23f, 72f, 34f, 71f, 48f, 50f, 78f); ring(50f, 40f, 9f) }
            "ai" -> { ring(50f, 50f, 20f); c.drawOval(20f, 42f, 80f, 58f, paint); c.drawOval(42f, 20f, 58f, 80f, paint) }
            "terminal" -> { box(23f, 25f, 77f, 75f); trace(32f, 39f, 43f, 50f, 32f, 61f); line(49f, 61f, 66f, 61f) }
            "book" -> { box(29f, 22f, 71f, 78f); line(38f, 35f, 62f, 35f); line(38f, 47f, 62f, 47f); line(38f, 59f, 56f, 59f) }
            "cloud" -> trace(29f, 68f, 22f, 59f, 25f, 46f, 37f, 42f, 42f, 29f, 57f, 25f, 69f, 35f, 70f, 45f, 78f, 50f, 77f, 62f, 69f, 69f, 29f, 68f)
            "game" -> { trace(28f, 34f, 72f, 34f, 81f, 66f, 71f, 72f, 59f, 60f, 41f, 60f, 29f, 72f, 19f, 66f, 28f, 34f); line(30f, 43f, 30f, 56f); line(24f, 50f, 37f, 50f); ring(66f, 49f, 2f) }
            "shield" -> { trace(24f, 28f, 50f, 20f, 76f, 28f, 71f, 58f, 50f, 79f, 29f, 58f, 24f, 28f); trace(36f, 47f, 46f, 57f, 65f, 37f) }
            "social" -> { ring(50f, 50f, 12f); ring(50f, 23f, 5f); ring(27f, 63f, 5f); ring(73f, 63f, 5f); line(50f, 29f, 50f, 37f); line(33f, 60f, 39f, 56f); line(61f, 56f, 67f, 60f) }
            "grid" -> for (x in 0..2) for (y in 0..2) box(28f+x*16, 28f+y*16, 37f+x*16, 37f+y*16)
            else -> {
                paint.style = Paint.Style.FILL
                paint.textSize = 25f
                paint.typeface = Typeface.MONOSPACE
                paint.textAlign = Paint.Align.CENTER
                c.drawText(label.take(2).uppercase().ifBlank { "?" }, 50f, 59f, paint)
            }
        }
    }
}
