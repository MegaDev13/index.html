package dev.neonos

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object ReleasePolicy {
    fun version(tag: String): Int? {
        val match = Regex("neon-os-v?(\\d+)\\.(\\d+)\\.(\\d+)").matchEntire(tag) ?: return null
        val parts = match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
        if (parts.any { it !in 0..99 }) return null
        return parts[0] * 10000 + parts[1] * 100 + parts[2]
    }

    fun allowed(url: String) = runCatching {
        val value = URL(url)
        value.protocol == "https" && value.host == "github.com" && value.userInfo == null &&
            value.path.startsWith("/MegaDev13/index.html/releases/download/neon-os-") && value.path.endsWith(".apk")
    }.getOrDefault(false)
}

class ReleaseActivity : NeonPanel() {
    @Volatile private var request: HttpURLConnection? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        screen("ATUALIZAÇÕES / NEON")
        add(text("Consulta manual aos releases NEON. O APK abre no navegador e a instalação é confirmada no Android."))
        val status = text("Nenhuma consulta realizada.")
        add(status)
        var asset: String? = null
        val download = button("ABRIR DOWNLOAD") {
            asset?.let { url ->
                AlertDialog.Builder(this).setMessage("Abrir o APK deste release no navegador?")
                    .setPositiveButton("Abrir") { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    .setNegativeButton("Cancelar", null).show()
            }
        }.apply { isEnabled = false }
        add(button("VERIFICAR AGORA") {
            status.text = "Consultando releases…"
            download.isEnabled = false
            worker.execute {
                val result = runCatching { releases() }
                whenAlive {
                    result.onSuccess { all ->
                        val current = ReleasePolicy.version("neon-os-" + BuildConfig.VERSION_NAME) ?: 0
                        val latest = (0 until all.length()).map { all.getJSONObject(it) }
                            .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
                            .filter { (ReleasePolicy.version(it.optString("tag_name")) ?: 0) > current }
                            .maxByOrNull { ReleasePolicy.version(it.getString("tag_name"))!! }
                        val assets = latest?.optJSONArray("assets") ?: JSONArray()
                        asset = (0 until assets.length()).map { assets.getJSONObject(it).optString("browser_download_url") }.firstOrNull(ReleasePolicy::allowed)
                        status.text = if (latest == null) "Nenhum release NEON mais recente foi publicado."
                        else latest.optString("tag_name") + "\n" + latest.optString("body").take(10000)
                        download.isEnabled = asset != null
                    }.onFailure { status.text = it.message ?: "Consulta indisponível." }
                }
            }
        })
        add(download)
    }

    private fun releases(): JSONArray {
        val connection = URL("https://api.github.com/repos/MegaDev13/index.html/releases?per_page=20").openConnection() as HttpURLConnection
        request = connection
        connection.connectTimeout = 6000
        connection.readTimeout = 8000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "NEON-OS-Android")
        try {
            check(connection.responseCode == 200) { "GitHub indisponível (HTTP ${connection.responseCode})." }
            return JSONArray(String(connection.inputStream.use { ArchiveSafety.read(it, 524288) }, Charsets.UTF_8))
        } finally { connection.disconnect(); request = null }
    }

    override fun onStop() { request?.disconnect(); super.onStop() }
}
