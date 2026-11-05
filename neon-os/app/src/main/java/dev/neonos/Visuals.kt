package dev.neonos

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

object NeonArt {
    fun wallpaper(width: Int, height: Int, accent: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), width.toFloat(), height.toFloat(), accent)
        return bitmap
    }
    fun draw(c: Canvas, w: Float, h: Float, accent: Int) {
        c.drawColor(Color.BLACK)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(w*.55f,h*.54f,w*.7f,intArrayOf(Color.rgb(9,28,37),Color.BLACK),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,w,h,p); p.shader=null
        val scale = w/400f
        c.save(); c.scale(scale,scale)
        val hh=h/scale; val cy=hh*.53f
        p.style=Paint.Style.STROKE; p.strokeWidth=.7f; p.color=Color.rgb(14,29,36)
        for(i in 0..12) { c.drawLine(i*40f,0f,i*40f,hh,p) }
        for(i in 0..(hh/40).toInt()) c.drawLine(0f,i*40f,400f,i*40f,p)
        p.strokeWidth=1.1f; p.color=accent; p.alpha=100
        c.drawCircle(210f,cy,138f,p); c.drawCircle(210f,cy,132f,p)
        p.color=Color.rgb(164,79,255); p.alpha=110; p.strokeWidth=3f
        c.drawArc(63f,cy-147f,357f,cy+147f,205f,84f,false,p)
        p.color=accent;p.alpha=220;p.strokeWidth=2.5f
        c.drawArc(72f,cy-138f,348f,cy+138f,-48f,105f,false,p)
        for(i in 0..42) { val a=i*2*PI/43; val r=144f; p.alpha=if(i%5==0)180 else 45; c.drawLine(210f+cos(a).toFloat()*r,cy+sin(a).toFloat()*r,210f+cos(a).toFloat()*(r+if(i%5==0)10 else 4),cy+sin(a).toFloat()*(r+if(i%5==0)10 else 4),p) }
        p.alpha=140;p.strokeWidth=1f
        for(i in 0..5) {
            val x=80f+i*44; val path=Path();path.moveTo(x,cy+120);path.lineTo(x,cy+170+i*7);path.lineTo(x-30,cy+200+i*7);path.lineTo(x-30,hh-25);c.drawPath(path,p)
        }
        p.style=Paint.Style.FILL;p.alpha=255;p.color=accent;p.typeface=Typeface.MONOSPACE;p.textSize=9f
        c.drawText("N E O N   /   C O R E",26f,hh-32,p)
        p.color=Color.rgb(208,44,131);c.drawRect(350f,42f,374f,45f,p)
        p.color=Color.rgb(47,78,88);p.textSize=7f;c.drawText("PERSONAL TERMINAL  //  01",26f,48f,p)
        c.restore()
    }
}
class ArtView(context: Context, var accent: Int): View(context) {
    override fun onDraw(canvas: Canvas) { NeonArt.draw(canvas,width.toFloat(),height.toFloat(),accent) }
}
class AccessView(context: Context): View(context) {
    var accent=Color.CYAN; var effect=2; var intensity=.65f; var luminance=.8f
    private var progress=1f
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var animation: ValueAnimator?=null
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO; isClickable=false; isFocusable=false }
    fun play(duration: Long) {
        stop(); if(duration==0L)return
        animation=ValueAnimator.ofFloat(0f,1f).apply {
            this.duration=duration
            addUpdateListener { progress=it.animatedValue as Float; invalidate() }
            start()
        }
    }
    fun stop() { animation?.cancel(); animation=null;progress=1f;invalidate() }
    override fun onDetachedFromWindow() { stop();super.onDetachedFromWindow() }
    override fun onDraw(c: Canvas) {
        if(progress>=1f)return
        val w=width.toFloat();val h=height.toFloat();val t=progress
        paint.color=accent;paint.alpha=((1-t)*190*luminance).toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=2*resources.displayMetrics.density
        when(effect) {
            0 -> for(i in 0..11) { val x=w*(i+1)/13; val yy=h*(.25f+(i%4)*.15f);c.drawLine(w*.5f,h*.5f,x,yy,paint);c.drawCircle(x,yy,3f+t*12,paint) }
            1 -> for(i in 0..7) { val yy=h*(i+.5f)/8;c.drawLine(w*((i%3)*.1f)*t,yy,w*(.6f+t*.4f),yy,paint) }
            2 -> for(i in 0..2)c.drawCircle(w*.5f,h*.57f,(t*w*.8f+i*w*.13f)*(0.5f+intensity*.5f),paint)
            3 -> {paint.style=Paint.Style.FILL;paint.typeface=Typeface.MONOSPACE;paint.textSize=16*resources.displayMetrics.scaledDensity;for(i in 0..8)for(j in 0..8)c.drawText(if((i+j)%2==0)"01" else "//",w*i/9,h*((j+t)/9),paint)}
            else -> { c.save(); c.translate(w*.5f,h*.5f);c.rotate(t*55);for(i in 0..3) {val r=w*(.12f+t*.6f+i*.06f);c.drawRect(-r,-r,r,r,paint)};c.restore() }
        }
    }
}
