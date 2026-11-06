package dev.neonos

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView

/** Shared design tokens: launcher, assistant, icons, wallpaper and RemoteViews. */
data class ThemeSpec(val id:String,val title:String,val accent:Int,val secondary:Int,val text:Int,val muted:Int,val panel:Int,val stroke:Int,val radius:Int,val font:String,val mono:Boolean) {
    fun background(context:Context,button:Boolean=false)=GradientDrawable().apply {
        setColor(panel);cornerRadius=radius*context.resources.displayMetrics.density
        setStroke((context.resources.displayMetrics.density).toInt().coerceAtLeast(1),if(button)secondary else stroke)
    }
}
object Themes {
    const val NEON="cyberpunk_neon";const val TERMINAL="dark_terminal";const val CORPORATE="corporate_dystopia"
    val ids=listOf(NEON,TERMINAL,CORPORATE)
    val titles=listOf("Cyberpunk Neon","Dark Terminal","Corporate Dystopia")
    private fun c(s:String)=Color.parseColor(s)
    fun spec(id:String,neonAccent:Int=Color.rgb(58,242,228))=when(id) {
        TERMINAL -> ThemeSpec(TERMINAL,titles[1],c("#00FF88"),c("#00B86B"),c("#D6E5DF"),c("#74818A"),c("#000000"),c("#143D2B"),3,"monospace",true)
        CORPORATE -> ThemeSpec(CORPORATE,titles[2],c("#E1B76B"),c("#8F7752"),c("#E2E7EA"),c("#85909B"),c("#0B1016"),c("#323D48"),0,"sans-serif-condensed",false)
        else -> ThemeSpec(NEON,titles[0],neonAccent,c("#466B7B"),c("#E9F2FA"),c("#99ABBE"),c("#050C13"),c("#1F3D48"),20,"sans-serif",false)
    }
    fun current(context:Context):ThemeSpec {val p=context.getSharedPreferences("neon",0);return spec(p.getString("theme",NEON)?:NEON,p.getInt("accent",Color.rgb(58,242,228)))}
    fun select(context:Context,id:String) {
        require(id in ids)
        val changed=current(context).id!=id
        context.getSharedPreferences("neon",0).edit().putString("theme",id).commit()
        if(changed)EventJournal.record("THEME",id)
        NeonWidget.refresh(context);AtlasWidget.refresh(context)
    }
    fun icon(view:ImageView,spec:ThemeSpec) {
        view.clearColorFilter()
        if(spec.id==TERMINAL) view.colorFilter=ColorMatrixColorFilter(floatArrayOf(0f,0f,0f,0f,0f,.15f,.5f,.05f,0f,65f,.08f,.27f,.03f,0f,35f,0f,0f,0f,1f,0f))
        if(spec.id==CORPORATE) view.colorFilter=ColorMatrixColorFilter(ColorMatrix().apply {setSaturation(.15f)})
    }
    fun tag(view:View,part:String,spec:ThemeSpec) {view.tag="theme:${spec.id}:$part"}
}
