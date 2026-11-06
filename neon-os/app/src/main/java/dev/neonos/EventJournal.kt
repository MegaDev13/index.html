package dev.neonos

import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

data class NeonEvent(val timestamp: Long, val kind: String, val detail: String)

/** Session-local observations. The journal has no access to system or other apps' logs. */
object EventJournal {
    private val events = ArrayDeque<NeonEvent>()
    val observers = CopyOnWriteArraySet<() -> Unit>()
    @Synchronized fun snapshot() = events.toList()

    fun record(kind: String, detail: String) {
        synchronized(this) {
            events.add(NeonEvent(System.currentTimeMillis(), kind.take(24), detail.replace('\n', ' ').take(100)))
            while (events.size > 120) events.removeFirst()
        }
        notifyObservers()
    }

    fun clear() {
        synchronized(this) { events.clear() }
        notifyObservers()
    }

    private fun notifyObservers() {
        Handler(Looper.getMainLooper()).post { observers.forEach { it() } }
    }

    fun lines(count: Int): String {
        val format = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
        return snapshot().takeLast(count).joinToString("\n") { "[${format.format(Date(it.timestamp))}] ${it.kind} :: ${it.detail}" }
    }
}
