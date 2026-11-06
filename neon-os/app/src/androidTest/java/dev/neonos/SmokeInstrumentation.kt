package dev.neonos

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.os.*
import android.view.*
import android.widget.*
import java.io.File

/** Propagate UI assertion failures to the instrumentation thread for the failure artifact. */
internal fun Instrumentation.onUi(block: () -> Unit) {
    var result: Result<Unit>? = null
    runOnMainSync { result = runCatching(block) }
    checkNotNull(result).getOrThrow()
}

/** Runs on an actual Android emulator through am instrument; no mock UI. */
class SmokeInstrumentation: Instrumentation() {
    private var assertions=0
    private fun check(value:Boolean,message:String) {assertions++;if(!value)throw AssertionError(message)}
    override fun onCreate(arguments:Bundle?) {super.onCreate(arguments);start()}
    private fun find(view:View,text:String):TextView? {
        if(view is TextView && view.text.toString()==text)return view
        if(view is ViewGroup)for(i in 0 until view.childCount){val found=find(view.getChildAt(i),text);if(found!=null)return found}
        return null
    }
    override fun onStart() {
        val result=Bundle()
        try {
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            waitForIdleSync()
            val dir=File(targetContext.getExternalFilesDir(null),"verification").apply{mkdirs()}
            val window=activity.window.decorView
            fun unobstructed() {
                val until=SystemClock.elapsedRealtime()+5000
                while(uiAutomation.rootInActiveWindow?.packageName?.toString()!=targetContext.packageName && SystemClock.elapsedRealtime()<until)Thread.sleep(150)
                check(uiAutomation.rootInActiveWindow?.packageName?.toString()==targetContext.packageName,"System dialog or foreign window covers NEON: ${uiAutomation.rootInActiveWindow?.packageName}")
            }
            fun click(text:String) {
                unobstructed()
                if(text in setOf("HOME","GRID","STATUS","CONFIG")) {
                    val pos=IntArray(2)
                    onUi {val view=find(window,text);check(view!=null,"Missing control: $text");view!!.getLocationOnScreen(pos);pos[0]+=view.width/2;pos[1]+=view.height/2}
                    val now=SystemClock.uptimeMillis()
                    sendPointerSync(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,pos[0].toFloat(),pos[1].toFloat(),0))
                    sendPointerSync(MotionEvent.obtain(now,now+20,MotionEvent.ACTION_UP,pos[0].toFloat(),pos[1].toFloat(),0))
                } else onUi {val view=find(window,text);check(view!=null,"Missing control: $text");view!!.performClick()}
                waitForIdleSync()
            }
            fun screenshot(name:String) {Thread.sleep(350);unobstructed();uiAutomation.takeScreenshot()?.let {image->File(dir,"$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()}}
            Thread.sleep(1300);waitForIdleSync()
            check(targetContext.packageManager.getPackageInfo(targetContext.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions?.toSet().orEmpty()==setOf("android.permission.SET_WALLPAPER","android.permission.INTERNET","android.permission.ACCESS_NETWORK_STATE","android.permission.RECORD_AUDIO","android.permission.ACCESS_COARSE_LOCATION","android.permission.BLUETOOTH_CONNECT"),"Unexpected permission")
            onUi {check(find(window,"HOME")!=null,"Home missing");check(find(window,"QUICK ACCESS")!=null,"Favorites missing")}
            screenshot("01-home")
            click("GRID");onUi {check(find(window,"Aplicativos")!=null,"Grid title missing")};screenshot("02-grid")
            onUi {
                fun edit(view:View):EditText? {if(view is EditText)return view;if(view is ViewGroup)for(i in 0 until view.childCount){val v=edit(view.getChildAt(i));if(v!=null)return v};return null}
                val search=edit(window);check(search!=null,"Search missing");search!!.setText("zzzz-no-result")
            };waitForIdleSync();onUi {check(find(window,"Nenhum aplicativo encontrado.")!=null,"Search does not filter")}
            click("STATUS");screenshot("03-status")
            click("CONFIG");screenshot("04-config")
            onUi {
                check(find(window,"Modo instantâneo") is Switch,"Instant control missing")
                val instant=find(window,"Modo instantâneo") as Switch;instant.isChecked=true
            };click("PRÉVIA DO EFEITO")
            onUi {(find(window,"Modo instantâneo") as Switch).isChecked=false}
            val measurement=Diagnostics(activity)
            for(effect in 0..4) {
                onUi {
                    val view=AccessView(activity).apply {this.effect=effect}
                    val holder=window as ViewGroup;holder.addView(view,ViewGroup.LayoutParams(-1,-1));view.play(280)
                    Handler(Looper.getMainLooper()).postDelayed({holder.removeView(view)},320)
                };Thread.sleep(130)
                uiAutomation.takeScreenshot()?.let {image->File(dir,"effect-${effect+1}.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()};Thread.sleep(250)
            }
            // Measure a separate pass with no screenshots, shell commands or sampling in the loop.
            onUi {measurement.start()}
            for(effect in 0..4) {
                onUi {
                    val view=AccessView(activity).apply {this.effect=effect}
                    val holder=window as ViewGroup;holder.addView(view,ViewGroup.LayoutParams(-1,-1));view.play(280)
                    Handler(Looper.getMainLooper()).postDelayed({holder.removeView(view)},320)
                };Thread.sleep(420)
            }
            unobstructed()
            onUi {
                val json=measurement.report();check(json.getLong("pss_kib")>0,"Memory not measured");check(json.getInt("frames")>0,"No frames recorded");File(dir,"profiling-emulator.json").writeText(json.toString(2));measurement.stop()
            }
            click("HOME")
            onUi {check(find(window,"QUICK ACCESS")!=null,"Return home failed")}
            val entries=AppRepository(targetContext).load();check(entries.isNotEmpty(),"No launchable apps discovered")
            val settings=entries.firstOrNull {it.component.packageName=="com.android.settings"}
            check(settings!=null,"Settings launcher entry missing")
            onUi {AppRepository(targetContext).open(settings!!)};Thread.sleep(350)
            uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            try {
                onUi {
                    val host=android.appwidget.AppWidgetHost(targetContext,9923)
                    val id=host.allocateAppWidgetId()
                    try {
                        val manager=android.appwidget.AppWidgetManager.getInstance(targetContext)
                        check(manager.bindAppWidgetIdIfAllowed(id,ComponentName(targetContext,NeonWidget::class.java)),"Widget bind failed")
                        val info=manager.getAppWidgetInfo(id)
                        check(info!=null,"Widget info missing")
                        host.startListening()
                        val widget=host.createView(activity,id,info)
                        check(widget.appWidgetId==id,"Widget host view wrong id")
                        host.stopListening()
                    } finally {host.deleteAppWidgetId(id)}
                    check(id !in host.appWidgetIds,"Widget id leaked")
                }
            } finally {uiAutomation.dropShellPermissionIdentity()}
            val bitmap=NeonArt.wallpaper(1220,2712,android.graphics.Color.rgb(58,242,228));check(bitmap.width==1220 && bitmap.height==2712,"Wallpaper dimensions")
            File(dir,"NEON-CORE-1220x2712.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            assertions+=FeatureVerification(this,dir).run()
            assertions+=UpgradeVerification(this,dir).run()
            result.putString("stream","NEON emulator smoke passed: $assertions assertions. Actual images and process profiling exported. Physical HyperOS not tested.\n")
            result.putInt("assertions",assertions);finish(Activity.RESULT_OK,result)
        } catch(error:Throwable) {
            try {val folder=File(targetContext.getExternalFilesDir(null),"verification").apply{mkdirs()};uiAutomation.takeScreenshot()?.let{b->File(folder,"failure.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()};File(folder,"failure-window.txt").writeText(uiAutomation.rootInActiveWindow.toString())}catch(_:Exception){}
            result.putString("stream","FAIL: ${error.stackTraceToString()}");finish(Activity.RESULT_CANCELED,result)}
    }
}
