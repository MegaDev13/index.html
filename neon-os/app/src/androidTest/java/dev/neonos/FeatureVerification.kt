package dev.neonos

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
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
    private fun find(view:View,predicate:(View)->Boolean):View? {if((view.tag as? String)?.startsWith("neon:page:")==true && view.visibility!=View.VISIBLE)return null;if(predicate(view))return view;if(view is ViewGroup)for(i in 0 until view.childCount){val item=find(view.getChildAt(i),predicate);if(item!=null)return item};return null}
    private fun label(value:String)=find(activity.window.decorView){it is TextView&&it.text.toString()==value} as? TextView
    private fun part(value:String)=find(activity.window.decorView){it.tag=="theme:${Themes.current(context).id}:$value"}
    private fun main(block: () -> Unit) = test.onUi(block)
    private fun foreground() {
        val deadline = SystemClock.elapsedRealtime() + 10000
        fun visible() = test.uiAutomation.rootInActiveWindow?.packageName?.toString() == context.packageName
        while (!visible() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        check(visible(), "Foreign window obstructs feature verification")
    }
    private fun tap(value: String) {
        foreground()
        val bounds = Rect()
        val deadline = SystemClock.elapsedRealtime() + 10000
        var ready: Boolean
        do {
            ready = false
            main {
                val control = label(value)
                ready = activity.hasWindowFocus() && control != null && control.isShown && control.getGlobalVisibleRect(bounds)
            }
            if (!ready) Thread.sleep(100)
        } while (!ready && SystemClock.elapsedRealtime() < deadline)
        check(ready, "Control ${value} not visible in focused NEON window")
        val now = SystemClock.uptimeMillis()
        val x = bounds.exactCenterX()
        val y = bounds.exactCenterY()
        test.sendPointerSync(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0))
        test.sendPointerSync(MotionEvent.obtain(now, now+25, MotionEvent.ACTION_UP, x, y, 0))
        test.waitForIdleSync()
        Thread.sleep(420)
    }
    private fun screenshot(name:String){Thread.sleep(400);foreground();val b=test.uiAutomation.takeScreenshot();check(b!=null,"Screenshot unavailable");b!!.let{File(dir,name+".png").outputStream().use {s->it.compress(Bitmap.CompressFormat.PNG,100,s)};it.recycle()}}
    private fun startMain():MainActivity=(test.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity).also{test.waitForIdleSync();Thread.sleep(600)}
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
            main{check(part("search")!=null,"Search theme missing");check(part("icon-frame")!=null,"Icon frame missing");val icon=part("icon") as? ImageView;check(icon!=null,"App icons missing");if(id==Themes.TERMINAL)check(icon!!.drawable is NeonGlyph,"Terminal vector icon missing")}
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
        test.waitForIdleSync()
        fun imeVisible():Boolean {var visible=false;main{visible=atlas.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true};return visible}
        main{val input=find(atlas.window.decorView){it is EditText}!!;input.requestFocus();atlas.getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)}
        val keyboardDeadline=SystemClock.elapsedRealtime()+5000
        while(!imeVisible()&&SystemClock.elapsedRealtime()<keyboardDeadline)Thread.sleep(100)
        check(imeVisible(),"Real keyboard did not open; typing visibility cannot be claimed")
        test.waitForIdleSync()
        main{
            val decor=atlas.window.decorView;val bottom=decor.height-decor.rootWindowInsets.getInsets(WindowInsets.Type.ime()).bottom
            for(predicate in listOf<(View)->Boolean>({it is EditText},{it is TextView&&it.text.toString()=="ENVIAR"})){
                val v=find(decor,predicate)!!;val r=android.graphics.Rect()
                check(v.getGlobalVisibleRect(r)&&r.height()==v.height&&r.bottom<=bottom,"Composer or send button covered by keyboard")
            }
        }
        val automation = test.uiAutomation
        val info = automation.serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        fun letterA(): android.view.accessibility.AccessibilityNodeInfo? =
            automation.windows.asSequence()
                .filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                .mapNotNull { window ->
                    window.root?.findAccessibilityNodeInfosByText("a")?.firstOrNull {
                        it.text?.toString() == "a" && it.isClickable && it.isVisibleToUser
                    }
                }.firstOrNull()
        val drawnDeadline = SystemClock.elapsedRealtime() + 6500
        var key = letterA()
        while (key == null && SystemClock.elapsedRealtime() < drawnDeadline) {
            Thread.sleep(140)
            key = letterA()
        }
        screenshot("atlas-keyboard")
        fun keyboardPoint(fractionX: Float, yDp: Float): FloatArray {
            val coords = FloatArray(2)
            main {
                val decor = atlas.window.decorView
                val insets = decor.rootWindowInsets
                val density = atlas.resources.displayMetrics.density
                coords[0] = decor.width * fractionX
                coords[1] = decor.height - insets.getInsets(WindowInsets.Type.ime()).bottom + yDp * density
                check(coords[1] < decor.height - insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
                    "Keyboard key overlaps Android navigation bar")
            }
            return coords
        }
        fun keyboardEvent(down: Long, action: Int, coords: FloatArray) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, coords[0], coords[1], 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(automation.injectInputEvent(event, true), "NEON IME touch injection failed") }
            finally { event.recycle() }
        }
        fun tapKeyboard(fractionX: Float, yDp: Float) {
            val coords = keyboardPoint(fractionX, yDp)
            val down = SystemClock.uptimeMillis()
            keyboardEvent(down, MotionEvent.ACTION_DOWN, coords)
            keyboardEvent(down, MotionEvent.ACTION_UP, coords)
        }
        fun checkEditor(expected: String, message: String, clear: Boolean = false) {
            Thread.sleep(200)
            main {
                val editor = find(atlas.window.decorView) { it is EditText } as EditText
                check(editor.text.toString() == expected, message)
                if (clear) editor.setText("")
            }
        }
        if (key?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) != true) tapKeyboard(1/18f, 123f)
        Thread.sleep(150)
        tapKeyboard(.53f, 224f)
        checkEditor("a ", "NEON IME did not deliver letter and space", true)
        val held = keyboardPoint(1/18f, 123f)
        val moved = keyboardPoint(.30f, 95f)
        val heldAt = SystemClock.uptimeMillis()
        keyboardEvent(heldAt, MotionEvent.ACTION_DOWN, held)
        Thread.sleep(420)
        keyboardEvent(heldAt, MotionEvent.ACTION_MOVE, moved)
        screenshot("v033-keyboard-accent")
        keyboardEvent(heldAt, MotionEvent.ACTION_UP, moved)
        checkEditor("â", "Slide across accent variants did not insert the selected letter", true)
        tapKeyboard(.50f, 174f)
        tapKeyboard(.85f, 71f)
        tapKeyboard(.395f, 174f)
        tapKeyboard(.25f, 71f)
        checkEditor("voce", "Keyboard changed the typed word without a tap")
        screenshot("v033-keyboard-suggestions")
        tapKeyboard(.165f, 26f)
        checkEditor("você ", "Tap on suggestion did not replace the typed token", true)
        evidence.put("keyboard_accent_hold_slide", true).put("keyboard_suggestions_require_tap", true)
        main{val input=find(atlas.window.decorView){it is EditText}!!;atlas.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken,0);input.clearFocus()}
        val hideDeadline=SystemClock.elapsedRealtime()+5000
        while(imeVisible()&&SystemClock.elapsedRealtime()<hideDeadline)Thread.sleep(100)
        check(!imeVisible(),"Keyboard did not close after typing verification")
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
        test.waitForIdleSync()
        main{val reply=find(atlas.window.decorView){it is TextView&&it.text.toString().contains("FIXTURE DE INTEGRAÇÃO")};check(reply?.getGlobalVisibleRect(android.graphics.Rect())==true,"Newest API reply is outside the visible transcript")}
        screenshot("atlas-integrated-fixture")
        fun shell(command:String){test.uiAutomation.executeShellCommand(command).use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use{stream->stream.readBytes()}}}
        try {
            shell("svc wifi disable");shell("svc data disable")
            val deadline=SystemClock.elapsedRealtime()+6000
            while(api.connected()&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(150)
            check(!api.connected(),"Emulator network did not turn off; offline test cannot be claimed")
            main{atlas.submit("Pergunta de teste sem rede")};test.waitForIdleSync()
            check(atlasText("Sem conexão. A IA está indisponível"),"Offline AI did not report unavailable")
            main{atlas.submit("Mostre minha bateria")};test.waitForIdleSync()
            val h=AtlasStorage(context).history();check(h.getJSONObject(h.length()-1).getString("text").startsWith("Bateria:"),"Local action failed offline")
            screenshot("atlas-offline")
            evidence.put("offline_local_action_verified",true)
        }finally{shell("svc wifi enable");shell("svc data enable")}
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
