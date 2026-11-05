package dev.neonos

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.os.*
import android.view.*
import android.widget.*
import java.io.File

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
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            waitForIdleSync()
            val dir=File(targetContext.getExternalFilesDir(null),"verification").apply{mkdirs()}
            val window=activity.window.decorView
            fun click(text:String) {runOnMainSync {val view=find(window,text);check(view!=null,"Missing control: $text");view!!.performClick()};waitForIdleSync()}
            fun screenshot(name:String) {Thread.sleep(350);uiAutomation.takeScreenshot()?.let {image->File(dir,"$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()}}
            Thread.sleep(1300);waitForIdleSync()
            check(targetContext.packageManager.getPackageInfo(targetContext.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.toSet()==setOf("android.permission.SET_WALLPAPER"),"Unexpected permission")
            runOnMainSync {check(find(window,"HOME")!=null,"Home missing");check(find(window,"QUICK ACCESS")!=null,"Favorites missing")}
            screenshot("01-home")
            click("GRID");runOnMainSync {check(find(window,"Aplicativos")!=null,"Grid title missing")};screenshot("02-grid")
            runOnMainSync {
                fun edit(view:View):EditText? {if(view is EditText)return view;if(view is ViewGroup)for(i in 0 until view.childCount){val v=edit(view.getChildAt(i));if(v!=null)return v};return null}
                val search=edit(window);check(search!=null,"Search missing");search!!.setText("zzzz-no-result")
            };waitForIdleSync();runOnMainSync {check(find(window,"Nenhum aplicativo encontrado.")!=null,"Search does not filter")}
            click("STATUS");screenshot("03-status")
            click("CONFIG");screenshot("04-config")
            runOnMainSync {
                check(find(window,"Modo instantâneo") is Switch,"Instant control missing")
                val instant=find(window,"Modo instantâneo") as Switch;instant.isChecked=true
            };click("PRÉVIA DO EFEITO")
            runOnMainSync {(find(window,"Modo instantâneo") as Switch).isChecked=false}
            val measurement=Diagnostics(activity)
            runOnMainSync {measurement.start()}
            for(effect in 0..4) {
                runOnMainSync {
                    val view=AccessView(activity).apply {this.effect=effect}
                    val holder=window as ViewGroup;holder.addView(view,ViewGroup.LayoutParams(-1,-1));view.play(280)
                    Handler(Looper.getMainLooper()).postDelayed({holder.removeView(view)},320)
                };Thread.sleep(130)
                uiAutomation.takeScreenshot()?.let {image->File(dir,"effect-${effect+1}.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()};Thread.sleep(250)
            }
            runOnMainSync {
                val json=measurement.report();check(json.getLong("pss_kib")>0,"Memory not measured");check(json.getInt("frames")>0,"No frames recorded");File(dir,"profiling-emulator.json").writeText(json.toString(2));measurement.stop()
            }
            click("HOME")
            runOnMainSync {check(find(window,"QUICK ACCESS")!=null,"Return home failed")}
            val entries=AppRepository(targetContext).load();check(entries.isNotEmpty(),"No launchable apps discovered")
            val settings=entries.firstOrNull {it.component.packageName=="com.android.settings"}
            check(settings!=null,"Settings launcher entry missing")
            runOnMainSync {AppRepository(targetContext).open(settings!!)};Thread.sleep(350)
            val bitmap=NeonArt.wallpaper(122,271,android.graphics.Color.CYAN);check(bitmap.width==122 && bitmap.height==271,"Wallpaper dimensions");bitmap.recycle()
            result.putString("stream","NEON emulator smoke passed: $assertions assertions. Actual images and process profiling exported. Physical HyperOS not tested.\n")
            result.putInt("assertions",assertions);finish(Activity.RESULT_OK,result)
        } catch(error:Throwable) {result.putString("stream","FAIL: ${error.stackTraceToString()}");finish(Activity.RESULT_CANCELED,result)}
    }
}
