package dev.neonos

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.*
import android.view.*
import android.widget.*
import org.json.JSONObject
import java.io.File

class FeatureVerification(private val test:Instrumentation,private val dir:File) {
    private var checks=0
    private lateinit var activity:MainActivity
    private val context get()=test.targetContext
    private fun check(value:Boolean,description:String){checks++;if(!value)throw AssertionError(description)}
    private fun find(view:View,predicate:(View)->Boolean):View? {if(predicate(view))return view;if(view is ViewGroup)for(i in 0 until view.childCount){val item=find(view.getChildAt(i),predicate);if(item!=null)return item};return null}
    private fun label(value:String)=find(activity.window.decorView){it is TextView&&it.text.toString()==value} as? TextView
    private fun part(value:String)=find(activity.window.decorView){it.tag=="theme:${Themes.current(context).id}:$value"}
    private fun main(block:()->Unit)=test.runOnMainSync(block)
    private fun foreground(){check(test.uiAutomation.rootInActiveWindow?.packageName?.toString()==context.packageName,"Foreign window obstructs feature verification")}
    private fun tap(value:String) {
        foreground();val xy=IntArray(2)
        main{val v=label(value);check(v!=null,"Missing $value");v!!.getLocationOnScreen(xy);xy[0]+=v.width/2;xy[1]+=v.height/2;check(v.isShown,"Hidden control $value")}
        val now=SystemClock.uptimeMillis();test.sendPointerSync(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,xy[0].toFloat(),xy[1].toFloat(),0));test.sendPointerSync(MotionEvent.obtain(now,now+25,MotionEvent.ACTION_UP,xy[0].toFloat(),xy[1].toFloat(),0));test.waitForIdleSync();Thread.sleep(250)
    }
    private fun screenshot(name:String){Thread.sleep(400);foreground();val b=test.uiAutomation.takeScreenshot();check(b!=null,"Screenshot unavailable");b!!.let{File(dir,name+".png").outputStream().use {s->it.compress(Bitmap.CompressFormat.PNG,100,s)};it.recycle()}}
    private fun startMain():MainActivity=(test.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity).also{test.waitForIdleSync();Thread.sleep(600)}
    fun run():Int {
        activity=startMain()
        val prefs=context.getSharedPreferences("neon",0)
        val apps=AppRepository(context).load();val saved=apps.take(2).map{it.key}.toSet();prefs.edit().putStringSet("favorites",saved).commit()
        val originalWidgetIds=(prefs.getStringSet("widgets",emptySet())?:emptySet()).toSet()
        for(id in listOf(Themes.TERMINAL,Themes.CORPORATE,Themes.NEON)) {
            tap("CONFIG");tap(Themes.spec(id).title)
            check(Themes.current(context).id==id,"Theme not persisted")
            main{check(part("root")!=null,"Root theme missing");check(part("dock")!=null,"Dock theme missing");check(label("CONFIGURAR ATLAS")!!.currentTextColor==Themes.spec(id).accent,"Config colors missing")}
            screenshot("${id}-config")
            tap("HOME")
            main{val clock=part("clock") as TextView;check(clock.currentTextColor==Themes.spec(id).text,"Clock colors not themed");if(id==Themes.TERMINAL)check(clock.typeface==Typeface.MONOSPACE,"Terminal clock font missing");check(part("atlas-home")!=null,"ATLAS Home entry missing")}
            screenshot("${id}-home")
            tap("GRID")
            main{(find(activity.window.decorView){it is EditText} as? EditText)?.setText("")}
            test.waitForIdleSync()
            main{check(part("search")!=null,"Search theme missing");check(part("icon-frame")!=null,"Icon frame missing");val icon=part("icon") as? ImageView;check(icon!=null,"App icons missing");if(id==Themes.TERMINAL)check(icon!!.colorFilter!=null,"Terminal monochrome icon missing")}
            screenshot("${id}-grid")
            tap("STATUS");screenshot("${id}-status")
            check(prefs.getStringSet("favorites",emptySet())==saved,"Favorites changed during theme switch")
            check(prefs.getStringSet("widgets",emptySet()).orEmpty()==originalWidgetIds,"Widget ids changed during theme switch")
            main {
                val overlay=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;setPadding(20,100,20,20);setBackgroundColor(Color.BLACK)}
                val clock=NeonWidget.views(activity).apply(activity,overlay);val atlas=AtlasWidget.views(activity).apply(activity,overlay)
                overlay.addView(clock,LinearLayout.LayoutParams(-1,350));overlay.addView(atlas,LinearLayout.LayoutParams(-1,180))
                val brand=clock.findViewById<TextView>(R.id.widget_brand);check(brand.currentTextColor==Themes.spec(id).accent,"Clock widget theme missing")
                val title=atlas.findViewById<TextView>(R.id.atlas_widget_title);check(title.currentTextColor==Themes.spec(id).accent,"ATLAS widget theme missing")
                (activity.window.decorView as ViewGroup).addView(overlay,ViewGroup.LayoutParams(-1,-1));overlay.tag="test-widgets"
            }
            screenshot("${id}-widgets")
            main{val decor=activity.window.decorView as ViewGroup;decor.removeView(find(decor){it.tag=="test-widgets"})}
            val atlas=test.startActivitySync(Intent(context,AtlasActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AtlasActivity
            test.waitForIdleSync();Thread.sleep(400)
            check(Themes.current(context).id==id,"ATLAS theme mismatch");screenshot("${id}-atlas")
            main{atlas.finish()};test.waitForIdleSync()
        }
        main{Themes.select(context,Themes.TERMINAL);activity.finish()};test.waitForIdleSync();activity=startMain()
        check(Themes.current(context).id==Themes.TERMINAL,"Theme lost after actual Activity restart")
        main{check(part("root")!=null,"Restarted launcher did not apply Terminal theme")};screenshot("terminal-after-restart")
        val official=OfficialChatGpt.launchIntent(context)
        val evidence=JSONObject().put("official_chatgpt_installed",official!=null).put("official_launch_verified",false).put("official_reason","Official ChatGPT not installed in AVD; no fake package installed").put("assistant_role_available",context.getSystemService(android.app.role.RoleManager::class.java).isRoleAvailable(android.app.role.RoleManager.ROLE_ASSISTANT))
        check(official==null,"Unexpected ChatGPT installation: this fixture expects the genuine app absent")
        main{prefs.edit().putBoolean("atlas_integrated",true).remove("atlas_url").commit();AtlasStorage(context).setToken("")}
        val atlas=test.startActivitySync(Intent(context,AtlasActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AtlasActivity
        test.waitForIdleSync();main{atlas.submit("Olá, como você está?")};test.waitForIdleSync()
        fun atlasText(fragment:String):Boolean {var found=false;main{found=find(atlas.window.decorView){it is TextView&&it.text.toString().contains(fragment)}!=null};return found}
        check(atlasText("integrado não configurado"),"Unconfigured integration does not explain error");screenshot("atlas-not-configured")
        main{atlas.submit("Mostre minha bateria")};test.waitForIdleSync();check(atlasText("Bateria:"),"Local battery tool failed")
        main{atlas.submit("Mude para Corporate Dystopia")};test.waitForIdleSync();check(Themes.current(context).id==Themes.CORPORATE,"Local theme command failed")
        main{atlas.submit("Volte para Cyberpunk Neon")};test.waitForIdleSync();check(Themes.current(context).id==Themes.NEON,"Return theme command failed")
        val api=AtlasApi(context)
        main{prefs.edit().putString("atlas_url","http://10.0.2.2:8787").commit()}
        val login=api.request("/v1/login",JSONObject().put("username","neon_ci").put("password","ci-fixture-password-only"))
        main{AtlasStorage(context).setToken(login.getString("token"));atlas.submit("Confirme o teste de integração")}
        val until=SystemClock.elapsedRealtime()+15000
        while(!atlasText("FIXTURE DE INTEGRAÇÃO")&&SystemClock.elapsedRealtime()<until)Thread.sleep(100)
        check(atlasText("FIXTURE DE INTEGRAÇÃO"),"Android/authenticated backend integration failed")
        screenshot("atlas-integrated-fixture")
        val secureBytes=File(context.filesDir,"atlas.secure").readBytes().toString(Charsets.ISO_8859_1)
        check(!secureBytes.contains(login.getString("token")),"Bearer token stored as plaintext")
        check(!secureBytes.contains("FIXTURE"),"History stored as plaintext")
        evidence.put("backend_fixture_verified",true).put("live_openai_verified",false).put("physical_hyperos_verified",false)
        main{prefs.edit().putInt("atlas_retention",0).commit();AtlasStorage(context).clearHistory()}
        check(AtlasStorage(context).history().length()==0,"History deletion failed")
        check(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED,"Microphone enabled without explicit request")
        main{prefs.edit().putBoolean("atlas_integrated",false).remove("atlas_url").putInt("atlas_retention",7).commit();AtlasStorage(context).setToken("");atlas.finish();Themes.select(context,Themes.TERMINAL)}
        File(dir,"features.json").writeText(evidence.put("feature_assertions",checks).toString(2))
        return checks
    }
}
