package dev.neonos

import android.app.WallpaperManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner

class LockStudioActivity : NeonPanel() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        screen("BLOQUEIO NEON")
        add(text("Arte para a tela nativa. Depois do desbloqueio, a Home NEON revela o mesmo fundo com o efeito escolhido.", 13f))
        val effects = Spinner(this).apply {
            adapter = ArrayAdapter(this@LockStudioActivity, android.R.layout.simple_spinner_dropdown_item, Policy.effects)
            setSelection(preferences.getInt("effect", 5).coerceIn(Policy.effects.indices))
        }
        add(effects)
        val preview = FrameLayout(this)
        val art = ArtView(this, style.accent)
        val effect = AccessView(this)
        preview.addView(art, FrameLayout.LayoutParams(-1, -1))
        preview.addView(effect, FrameLayout.LayoutParams(-1, -1))
        body.addView(preview, LinearLayout.LayoutParams(-1, dp(288)))
        add(button("PRÉVIA DO DESBLOQUEIO") {
            effect.accent = style.accent
            effect.effect = effects.selectedItemPosition
            effect.play(350)
        })
        add(button("APLICAR ARTE NO BLOQUEIO") {
            val theme = style
            val choice = effects.selectedItemPosition
            worker.execute {
                val result = runCatching {
                    val bitmap = NeonArt.wallpaper(1080, 2400, theme.accent, theme.id)
                    try { WallpaperManager.getInstance(this).setBitmap(bitmap, null, false, WallpaperManager.FLAG_LOCK) }
                    finally { bitmap.recycle() }
                }
                whenAlive {
                    result.onSuccess {
                        preferences.edit().putBoolean("lock_continuity", true)
                            .putString("lock_theme", theme.id).putInt("lock_accent", theme.accent)
                            .putInt("effect", choice).apply()
                        message("Arte do bloqueio e transição salvas.")
                    }.onFailure { message("O Android não permitiu aplicar esta arte ao bloqueio.") }
                }
            }
        })
        add(button("DESATIVAR CONTINUIDADE VISUAL") {
            preferences.edit().putBoolean("lock_continuity", false).apply()
            message("Continuidade visual desativada. O wallpaper pode ser trocado no HyperOS.")
        })
    }
}
