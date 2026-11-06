package dev.neonos

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.os.StatFs
import android.os.SystemClock
import android.util.Base64
import android.view.WindowManager
import android.widget.TextView
import org.json.JSONObject
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom

class BridgeActivity : NeonPanel() {
    private var session: LocalBridge? = null
    private lateinit var status: TextView
    private lateinit var token: TextView
    private val expire = Runnable { stopBridge(); status.text = "Sessão expirada." }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        screen("NEON BRIDGE")
        add(text("Diagnósticos MCP locais por USB. A sessão dura até dez minutos e encerra ao sair deste painel.", 13f))
        status = text("Sessão parada.")
        add(status)
        token = text("", 12f).apply { tag = "neon:bridge-token"; setTextIsSelectable(true) }
        add(token)
        add(button("INICIAR SESSÃO LOCAL") { startBridge() })
        add(button("COPIAR CHAVE DA SESSÃO") {
            val value = session?.token ?: return@button
            val clip = ClipData.newPlainText("Chave temporária NEON", value)
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
            getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        })
        add(button("PARAR SESSÃO") { stopBridge() })
        add(text("No computador autorizado, encaminhe a porta com adb forward tcp:38223 tcp:38223 e use o adaptador MCP em modo launcher.", 13f))
        add(button("ABRIR TERMUX PARA SHELL") {
            val target = packageManager.getLaunchIntentForPackage("com.termux")
            if (target == null) message("Instale o Termux oficial e configure o SSH pelo guia do Bridge.")
            else runCatching { startActivity(target) }.onFailure { message("Termux indisponível.") }
        })
    }

    private fun startBridge() {
        if (session != null) return
        val bridge = LocalBridge(filesDir.absolutePath)
        session = bridge
        status.text = "Abrindo sessão local…"
        worker.execute {
            val result = runCatching {
                bridge.serve {
                    ui.post {
                        if (session !== bridge || isFinishing || isDestroyed) return@post
                        status.text = "Ativo em 127.0.0.1:38223"
                        token.text = bridge.token
                        ui.postDelayed(expire, 600_000)
                    }
                }
            }
            ui.post {
                if (session === bridge) {
                    stopBridge()
                    if (result.isFailure) status.text = "Não foi possível abrir a porta local."
                }
            }
        }
    }

    private fun stopBridge() {
        ui.removeCallbacks(expire)
        session?.close()
        session = null
        token.text = ""
        status.text = "Sessão parada."
    }

    override fun onPause() {
        stopBridge()
        super.onPause()
    }
}

private class LocalBridge(private val dataPath: String) {
    val token: String = Base64.encodeToString(ByteArray(24).apply { SecureRandom().nextBytes(this) },
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private val server = ServerSocket().apply { reuseAddress = true }
    @Volatile private var client: Socket? = null

    fun serve(ready: () -> Unit) {
        server.use {
            server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 38223), 4)
            ready()
            while (!server.isClosed) {
                server.accept().use { socket ->
                    client = socket
                    try {
                        if (!server.isClosed) runCatching { handle(socket) }
                    } finally { client = null }
                }
            }
        }
    }

    fun close() {
        runCatching { server.close() }
        runCatching { client?.close() }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 2500
        val raw = readLine(socket.getInputStream()) ?: return
        val request = runCatching { JSONObject(raw) }.getOrNull()
        val supplied = request?.optString("token").orEmpty()
        val authenticated = MessageDigest.isEqual(supplied.toByteArray(), token.toByteArray())
        val value = if (authenticated) diagnostic(request?.optString("check").orEmpty()) else null
        val result = JSONObject().put("ok", value != null)
        if (value != null) result.put("value", value)
        socket.getOutputStream().write((result.toString() + "\n").toByteArray())
    }

    private fun readLine(input: InputStream): String? {
        val bytes = ByteArray(1024)
        for (i in bytes.indices) {
            val next = input.read()
            if (next == -1) return null
            if (next == 10) return String(bytes, 0, i, Charsets.UTF_8)
            bytes[i] = next.toByte()
        }
        return null
    }

    private fun diagnostic(check: String): String? = when (check) {
        "android" -> Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT
        "kernel" -> System.getProperty("os.version")
        "uptime" -> (SystemClock.elapsedRealtime() / 1000).toString() + " s"
        "storage" -> StatFs(dataPath).let { (it.availableBytes / 1048576).toString() + " MiB livres" }
        else -> null
    }
}
