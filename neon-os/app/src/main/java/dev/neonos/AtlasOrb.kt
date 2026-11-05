package dev.neonos

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import kotlin.math.*

/** RMS rings are driven only by onRmsChanged; processing/speech use a labelled state animation. */
class AtlasOrb(context:Context):View(context) {
    var spec=Themes.current(context)
    var state="PRONTO";private set
    var rms=0f
    private var phase=0f
    private var animator:ValueAnimator?=null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init {contentDescription="Núcleo ATLAS, pronto";isFocusable=false}
    fun status(value:String) {state=value;contentDescription="Núcleo ATLAS, $value";sync();invalidate()}
    private fun sync() {
        val p=context.getSharedPreferences("neon",0)
        val enabled=isAttachedToWindow && windowVisibility==VISIBLE && state in setOf("OUVINDO","PROCESSANDO","FALANDO") && !p.getBoolean("saver",false) && !p.getBoolean("instant",false) && !context.getSystemService(PowerManager::class.java).isPowerSaveMode && Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0
        if(!enabled){animator?.cancel();animator=null;return}
        if(animator==null) animator=ValueAnimator.ofFloat(0f,1f).apply {duration=2200;repeatCount=ValueAnimator.INFINITE;addUpdateListener{phase=it.animatedValue as Float;invalidate()};start()}
    }
    fun stop(){animator?.cancel();animator=null;rms=0f}
    override fun onAttachedToWindow(){super.onAttachedToWindow();sync()}
    override fun onDetachedFromWindow(){stop();super.onDetachedFromWindow()}
    override fun onWindowVisibilityChanged(v:Int){super.onWindowVisibilityChanged(v);sync()}
    override fun onDraw(c:Canvas){drawCore(c,width.toFloat(),height.toFloat(),spec,phase,if(state=="OUVINDO")rms.coerceIn(0f,1f)else 0f,paint)}
    companion object {
        fun thumbnail(t:ThemeSpec,size:Int):Bitmap {val b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);drawCore(Canvas(b),size.toFloat(),size.toFloat(),t,0f,0f,Paint(Paint.ANTI_ALIAS_FLAG));return b}
        private fun drawCore(c:Canvas,w:Float,h:Float,t:ThemeSpec,phase:Float,rms:Float,p:Paint) {
            val cx=w/2;val cy=h/2;val r=min(w,h)*.27f
            p.style=Paint.Style.FILL;p.shader=RadialGradient(cx-r*.25f,cy-r*.3f,r*1.25f,intArrayOf(t.accent,Color.BLACK),floatArrayOf(0f,1f),Shader.TileMode.CLAMP);p.alpha=190;c.drawCircle(cx,cy,r,p);p.shader=null
            p.style=Paint.Style.STROKE;p.color=t.accent;p.alpha=220;p.strokeWidth=max(1f,w/220)
            c.drawOval(cx-r*1.1f,cy-r*.38f,cx+r*1.1f,cy+r*.38f,p)
            c.drawOval(cx-r*.38f,cy-r*1.1f,cx+r*.38f,cy+r*1.1f,p)
            for(i in 0..2){val rr=r*(1.25f+i*.25f+rms*.18f);p.alpha=if(i==0)170 else 80;c.drawArc(cx-rr,cy-rr,cx+rr,cy+rr,phase*360+i*93f,if(t.mono)100f else 245f,false,p)}
            p.alpha=100;p.color=t.secondary
            for(i in 0..11){val a=(i/12f+phase)*2*PI;val rr=r*1.98f;c.drawLine(cx+cos(a).toFloat()*rr,cy+sin(a).toFloat()*rr,cx+cos(a).toFloat()*(rr+w*.018f),cy+sin(a).toFloat()*(rr+w*.018f),p)}
            p.alpha=255
        }
    }
}
