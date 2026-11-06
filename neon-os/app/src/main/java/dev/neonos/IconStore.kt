package dev.neonos

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ArchiveSafety {
    fun allowedIconPath(path: String): Boolean {
        if (path.length !in 1..180 || !path.startsWith("NEON_ICON_PACK/") || "//" in path) return false
        return path.matches(Regex("[A-Za-z0-9_./-]+")) && path.split('/').none { it == "." || it == ".." }
    }

    fun read(input: InputStream, limit: Int): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return result.toByteArray()
            require(result.size() + count <= limit) { "Arquivo excede o limite de tamanho." }
            result.write(buffer, 0, count)
        }
    }
}

data class PackIcon(val packageName: String, val component: String, val theme: String, val file: String, val label: String)
data class IconPackDraft(val directory: File, val entries: List<PackIcon>)

class IconStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("neon", 0)
    private val directory = File(context.filesDir, "neon-icons").apply { mkdirs() }
    private var cachedRevision = Long.MIN_VALUE
    private var cachedMapping = JSONObject()
    val revision get() = prefs.getLong("icon_revision", 0)
    val mode get() = prefs.getString("icon_mode", null)
        ?: if (prefs.getString("iconPack", "").isNullOrBlank()) "neon" else "pack"

    @Synchronized private fun map(): JSONObject {
        if (revision != cachedRevision) {
            cachedMapping = runCatching { JSONObject(prefs.getString("icon_overrides", "{}")!!) }.getOrElse { JSONObject() }
            cachedRevision = revision
        }
        return cachedMapping
    }

    private fun key(app: AppEntry, theme: String) = "$theme:app:${app.key}"
    private fun packageKey(theme: String, pkg: String, component: String) = "$theme:package:$pkg|$component"
    private fun selection(app: AppEntry, theme: String): String {
        val mapping = map()
        val pkg = app.component.packageName
        return mapping.optString(key(app, theme), mapping.optString(packageKey(theme, pkg, app.component.flattenToString()),
            mapping.optString(packageKey(theme, pkg, ""))))
    }

    fun state(app: AppEntry, theme: String): String {
        val selected = selection(app, theme)
        return when {
            selected == "original" -> "Original"
            selected.startsWith("glyph:") -> "Desenho alternativo"
            selected.isNotBlank() -> "Imagem personalizada"
            mode == "original" -> "Original"
            mode == "pack" -> "Pacote instalado"
            NeonIcons.mapped(app.component.packageName) != null -> "Desenho NEON por pacote"
            else -> "Monograma genérico"
        }
    }

    fun drawable(app: AppEntry, theme: ThemeSpec): Drawable? {
        val selected = selection(app, theme.id)
        if (selected == "original") return null
        val shape = prefs.getString("icon_shape", "theme") ?: "theme"
        val drawable = when {
            selected.startsWith("glyph:") -> NeonIcons.drawable(selected.substringAfter(':'), theme, app.label, shape)
            selected.isNotBlank() -> {
                val file = File(directory, selected)
                val bitmap = if (file.canonicalPath.startsWith(directory.canonicalPath + File.separator)) BitmapFactory.decodeFile(file.path) else null
                bitmap?.let { BitmapDrawable(context.resources, it) }
            }
            mode == "neon" -> NeonIcons.drawable(NeonIcons.mapped(app.component.packageName) ?: "generic", theme, app.label, shape)
            else -> null
        }
        return if (drawable != null && app.user != android.os.Process.myUserHandle())
            context.packageManager.getUserBadgedIcon(drawable, app.user) else drawable
    }

    fun choose(app: AppEntry, theme: String, value: String?) {
        val next = JSONObject(map().toString())
        if (value == null) next.remove(key(app, theme)) else next.put(key(app, theme), value)
        save(next)
    }

    fun changeMode(mode: String) {
        require(mode in setOf("neon", "original", "pack"))
        prefs.edit().putString("icon_mode", mode).putLong("icon_revision", revision + 1).apply()
    }

    private fun save(next: JSONObject) {
        check(prefs.edit().putString("icon_previous", map().toString()).putString("icon_overrides", next.toString())
            .putLong("icon_revision", revision + 1).commit()) { "Não foi possível salvar o conjunto." }
        cleanUnusedPacks()
    }

    fun restorePrevious(): Boolean {
        val previous = prefs.getString("icon_previous", null) ?: return false
        save(JSONObject(previous))
        return true
    }

    fun importImage(input: InputStream): String {
        val data = ArchiveSafety.read(input, 2 * 1024 * 1024)
        val path = "manual/${digest(data)}.png"
        val file = File(directory, path)
        file.parentFile!!.mkdirs()
        saveIcon(data, file)
        return path
    }

    private fun saveIcon(data: ByteArray, file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        require(bounds.outMimeType in setOf("image/png", "image/jpeg", "image/webp") &&
            bounds.outWidth in 16..2048 && bounds.outHeight in 16..2048) { "Imagem inválida. Use PNG, JPEG ou WebP até 2 MiB e 2048 px." }
        val original = BitmapFactory.decodeByteArray(data, 0, data.size) ?: error("Imagem inválida.")
        val scaled = Bitmap.createScaledBitmap(original, 256, 256, true)
        try {
            file.outputStream().use { check(scaled.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            if (scaled !== original) scaled.recycle()
            original.recycle()
        }
    }

    fun stage(input: InputStream): IconPackDraft {
        cleanUnusedPacks()
        val target = File(directory, "pack-" + UUID.randomUUID()).apply { mkdirs() }
        try {
            val files = unpack(input)
            val manifest = JSONObject(String(files["NEON_ICON_PACK/manifest.json"] ?: error("Manifesto ausente."), Charsets.UTF_8))
            require(manifest.getInt("version") == 1) { "Versão de pacote desconhecida." }
            val icons = manifest.getJSONArray("icons")
            require(icons.length() in 1..750) { "Use entre 1 e 750 desenhos." }
            val seen = mutableSetOf<String>()
            val entries = (0 until icons.length()).map { index ->
                val item = icons.getJSONObject(index)
                val pkg = item.getString("package")
                val component = item.optString("component").let {
                    if (it.isBlank()) "" else {
                        val name = ComponentName.unflattenFromString(it)
                        require(name?.packageName == pkg) { "Componente inválido." }
                        name!!.flattenToString()
                    }
                }
                val theme = item.optString("theme", "all")
                val path = item.getString("file")
                require(pkg.length <= 255 && pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) { "Pacote inválido." }
                require(theme in Themes.ids + "all" && path.startsWith("icons/") && ArchiveSafety.allowedIconPath("NEON_ICON_PACK/$path"))
                require(seen.add("$pkg|$component|$theme")) { "Mapeamento duplicado." }
                val data = files["NEON_ICON_PACK/$path"] ?: error("Imagem ausente no ZIP.")
                val name = digest(data) + ".png"
                val image = File(target, name)
                if (!image.isFile) saveIcon(data, image)
                PackIcon(pkg, component, theme, name, item.optString("label", pkg).take(100))
            }
            return IconPackDraft(target, entries)
        } catch (error: Exception) {
            target.deleteRecursively()
            throw error
        }
    }

    private fun unpack(input: InputStream): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        var entries = 0
        var total = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= 1000) { "O pacote excede 1000 arquivos." }
                require(ArchiveSafety.allowedIconPath(entry.name)) { "Caminho inválido no ZIP." }
                if (entry.isDirectory) continue
                require(entry.name == "NEON_ICON_PACK/manifest.json" || entry.name.startsWith("NEON_ICON_PACK/icons/"))
                require(entry.name !in result) { "Entrada duplicada." }
                val bytes = ArchiveSafety.read(zip, if (entry.name.endsWith("/manifest.json")) 262144 else 2097152)
                total += bytes.size
                require(total <= 64 * 1024 * 1024) { "O pacote excede 64 MiB descompactados." }
                result[entry.name] = bytes
            }
        }
        return result
    }

    fun apply(draft: IconPackDraft, selected: List<PackIcon>, installed: List<AppEntry>): Int {
        require(selected.isNotEmpty()) { "Selecione ao menos um desenho." }
        val next = JSONObject(map().toString())
        var bindings = 0
        for (icon in selected) {
            val themes = if (icon.theme == "all") Themes.ids else listOf(icon.theme)
            for (theme in themes) {
                val path = "${draft.directory.name}/${icon.file}"
                next.put(packageKey(theme, icon.packageName, icon.component), path)
                for (app in installed) {
                    if (app.component.packageName == icon.packageName && (icon.component.isBlank() || app.component.flattenToString() == icon.component)) {
                        next.put(key(app, theme), path)
                        bindings++
                    }
                }
            }
        }
        save(next)
        EventJournal.record("ICONS", "$bindings vínculos de tema aplicados")
        return bindings
    }

    private fun cleanUnusedPacks() {
        val references = map().toString() + prefs.getString("icon_previous", "")
        directory.listFiles()?.filter { it.isDirectory && it.name.startsWith("pack-") && !references.contains(it.name) }
            ?.forEach { it.deleteRecursively() }
    }

    fun export(output: OutputStream, apps: List<AppEntry>): Int {
        require(apps.size in 1..250) { "Selecione entre 1 e 250 apps." }
        val repository = AppRepository(context)
        val theme = Themes.current(context).id
        val manifest = JSONArray()
        ZipOutputStream(output).use { zip ->
            for (app in apps) {
                val original = runCatching { repository.icon(app) }.getOrNull()
                val icon = original ?: context.packageManager.defaultActivityIcon
                val name = "icons/${digest(app.key.toByteArray())}.png"
                val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
                try {
                    icon.setBounds(0, 0, 192, 192)
                    icon.draw(Canvas(bitmap))
                    zip.putNextEntry(ZipEntry("NEON_ICON_EXPORT/$name"))
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip))
                    zip.closeEntry()
                } finally { bitmap.recycle() }
                manifest.put(JSONObject().put("name", app.label).put("package", app.component.packageName)
                    .put("component", app.component.flattenToString()).put("profile", app.serial).put("icon", name)
                    .put("icon_source", if (original != null) "original" else "android_default")
                    .put("theme", theme).put("custom_state", state(app, theme)))
            }
            val data = JSONObject().put("version", 1).put("apps", manifest).toString(2)
            val readme = "Inventário local de apps iniciáveis e ícones originais selecionados. " +
                "O formato de importação é NEON_ICON_PACK/manifest.json. Consulte ICON-PACK-FORMAT.md no repositório."
            for ((name, value) in listOf("apps.json" to data, "README.txt" to readme)) {
                zip.putNextEntry(ZipEntry("NEON_ICON_EXPORT/$name"))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return manifest.length()
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
