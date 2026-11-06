package dev.neonos

object Policy {
    val effects = listOf("Neural Link", "Ghost Protocol", "Neon Pulse", "Cyber Matrix", "Quantum Gate", "Cadeado abrindo", "Varredura ocular", "Análise digital")
    fun duration(requested: Int, instant: Boolean, saver: Boolean, systemAnimations: Boolean): Long =
        if (instant || saver || !systemAnimations) 0 else requested.coerceIn(200, 350).toLong()
    fun matches(label: String, query: String): Boolean = normalize(label).contains(normalize(query))
    private fun normalize(value: String) = java.text.Normalizer.normalize(value.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "")
}

/** Only arms on an observed lock/screen-off, never infers the biometric method. */
class UnlockGate {
    private var armed = false
    private var readyAt = -1L
    fun observeLocked() { armed = true }
    fun visibleUnlocked(now: Long) { if (armed) { readyAt = now; armed = false } }
    fun userPresent(now: Long) { if (armed) { readyAt = now; armed = false } }
    fun consume(now: Long, locked: Boolean): Boolean {
        if (locked) { observeLocked(); return false }
        val ready = readyAt >= 0 && now - readyAt in 0..2500
        readyAt = -1
        return ready
    }
}
