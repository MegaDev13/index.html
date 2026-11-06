package dev.neonos

import android.app.*
import android.app.role.RoleManager
import android.appwidget.*
import android.content.*
import android.content.pm.LauncherApps
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.*
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.abs

@Suppress("DEPRECATION")
class MainActivity: Activity() {
    private val prefs by lazy { getSharedPreferences("neon",0) }
    private val repo by lazy { AppRepository(this) }
    private val packs by lazy { IconPack(this) }
    private val iconStore by lazy { IconStore(this) }
    private val io=Executors.newSingleThreadExecutor()
    private val handler=Handler(Looper.getMainLooper())
    private val iconCache=android.util.LruCache<String,android.graphics.drawable.Drawable>(80)
    private var apps=emptyList<AppEntry>();private var page="HOME";private var query="";private var category=-2
    private lateinit var root: FrameLayout;private lateinit var column: LinearLayout;private lateinit var body: LinearLayout
    private lateinit var access: AccessView;private lateinit var art: ArtView
    private lateinit var diagnostics: Diagnostics
    private var clock: TextView?=null;private var date: TextView?=null;private var battery: TextView?=null
    private var atlasHomeButton:TextView?=null
    private var hud:HudHomeView?=null
    private val homeLayout by lazy { HomeLayout(this) }
    private var homePage=0
    private var lastHomePage=0
    private var wallpaperKey=""
    private data class AppDrag(val key: String, val tile: View)
    private var appDrag: AppDrag? = null
    private var dragCandidate: AppDrag? = null
    private val dragSources = linkedMapOf<View, String>()
    private val dropTargets = linkedMapOf<View, (String) -> Unit>()
    private var touchX=0f
    private var touchY=0f
    private var touchInHud=false
    private var touchInBody=false
    private var pinchHandled=false
    private var doubleTap=false
    private var gridSearch:EditText?=null
    private lateinit var viewport: FrameLayout
    private lateinit var pageScroll: ScrollView
    private val navTabs = mutableMapOf<String, TextView>()
    private var drawerPanel: FrameLayout? = null
    private var drawerDragging = false
    private var drawerVelocity: VelocityTracker? = null
    private var refreshAfterDrawer = false
    private val drawerDragSources = linkedMapOf<View, String>()
    private val drawerDropTargets = linkedMapOf<View, (String) -> Unit>()
    private val tapDetector by lazy {
        GestureDetector(this,object:GestureDetector.SimpleOnGestureListener(){
            override fun onDown(event:MotionEvent)=true
            override fun onDoubleTap(event:MotionEvent):Boolean {
                val surface=hud?:return false
                doubleTap=inside(surface,event.x,event.y)&&!surface.actionAt(event.x,event.y)
                return doubleTap
            }
        })
    }
    private val pinchDetector by lazy {
        ScaleGestureDetector(this,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
            override fun onScaleBegin(detector:ScaleGestureDetector):Boolean {
                if(!touchInHud)return false
                pinchHandled=true
                editHome()
                return true
            }
        })
    }
    private var plugged=false
    private var uiSettingsHash=0
    private var networkState=""
    private val weather by lazy { WeatherClient(this) }
    private val weatherWorker=Executors.newSingleThreadExecutor()
    private var weatherBusy=false
    private var weatherGeneration=0
    private var weatherFailed=false
    private var gridBody: LinearLayout?=null;private var batteryLevel=-1
    private var resumed=false;private var sessionGeneration=0
    private val gate=UnlockGate()
    private val widgetHost by lazy { AppWidgetHost(this,7161) }
    private val widgetManager by lazy { AppWidgetManager.getInstance(this) }
    private var pendingWidget=-1
    private var widgetConfiguring=false
        private var diagnosticText=""
    private val theme:ThemeSpec get()=Themes.current(this)
    private var drawnTheme=""
    private val accent:Int get()=theme.accent
    private val textColor:Int get()=theme.text
    private val muted:Int get()=theme.muted
    private val tick=object: Runnable {override fun run() {if(!resumed)return;updateClock();refreshWeather();handler.postDelayed(this,60000-System.currentTimeMillis()%60000)}}
    private val networkCallback=object:android.net.ConnectivityManager.NetworkCallback(){override fun onAvailable(network:android.net.Network){handler.post{if(resumed)networkChanged()}};override fun onLost(network:android.net.Network){handler.post{if(resumed)networkChanged()}}}
    private fun updateAtlasState(){hud?.updateClockAndNetwork();if(hud!=null)return;val mode=if(prefs.getBoolean("atlas_integrated",false))"INTEGRADO"else"CHATGPT OFICIAL";val online=AtlasApi(this).connected();atlasHomeButton?.text="ATLAS\n$mode / ${if(online)"REDE DISPONÍVEL"else"OFFLINE"}"}
    private val broadcast=object: BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when(i.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val scale=i.getIntExtra("scale",100)
                    val next=if(scale>0)i.getIntExtra("level",-1)*100/scale else -1
                    val connected=i.getIntExtra("plugged",0)!=0
                    if(next>=0 && next!=batteryLevel)EventJournal.record("BATTERY","$next%")
                    if(connected!=plugged)EventJournal.record("POWER",if(connected)"Carregador conectado" else "Carregador desconectado")
                    batteryLevel=next
                    plugged=connected
                    battery?.text=if(next>=0)"BATERIA  $next%" else "BATERIA  —"
                    hud?.updateBattery(next,connected)
                }
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> updateRefresh()
                Intent.ACTION_SCREEN_OFF -> gate.observeLocked()
                Intent.ACTION_USER_PRESENT -> {gate.userPresent(SystemClock.elapsedRealtime());maybeAccess()}
                Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED -> updateClock()
            }
        }
    }
    private val packageCallback=object: LauncherApps.Callback() {
        override fun onPackageRemoved(p: String,u: UserHandle) = loadApps()
        override fun onPackageAdded(p: String,u: UserHandle) = loadApps()
        override fun onPackageChanged(p: String,u: UserHandle) { iconCache.evictAll();loadApps() }
        override fun onPackagesAvailable(p: Array<out String>,u: UserHandle,r: Boolean)=loadApps()
        override fun onPackagesUnavailable(p: Array<out String>,u: UserHandle,r: Boolean)=loadApps()
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        pendingWidget=state?.getInt("pendingWidget",-1)?:-1
        widgetConfiguring=state?.getBoolean("widgetConfiguring",false)?:false
        diagnostics=Diagnostics(this)
        EventJournal.record("NEON_OS","Inicializado / ${BuildConfig.VERSION_NAME}")
        window.decorView.systemUiVisibility=0
        window.setDecorFitsSystemWindows(false)
        root=FrameLayout(this)
        art=ArtView(this,accent);root.addView(art,FrameLayout.LayoutParams(-1,-1))
        column=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(10),dp(20),0)}
        root.addView(column,FrameLayout.LayoutParams(-1,-1))
        root.setOnApplyWindowInsetsListener { _,insets ->
            val bar=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            column.setPadding(dp(20)+bar.left,dp(10)+bar.top,dp(20)+bar.right,maxOf(bar.bottom,insets.getInsets(WindowInsets.Type.ime()).bottom))
            insets
        }
        access=AccessView(this);root.addView(access,FrameLayout.LayoutParams(-1,-1))
        setContentView(root)
        for(id in widgetHost.appWidgetIds)if(id!=pendingWidget && id.toString() !in savedWidgets())widgetHost.deleteAppWidgetId(id)
        if(intent.getStringExtra("neon.page")=="GRID"){query=intent.getStringExtra("neon.query")?.take(120)?:"";show("GRID")}else show("HOME");loadApps()
    }
    override fun onSaveInstanceState(out: Bundle) {out.putInt("pendingWidget",pendingWidget);out.putBoolean("widgetConfiguring",widgetConfiguring);super.onSaveInstanceState(out)}
    override fun onStart() {
        super.onStart()
        getSystemService(android.net.ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
        registerReceiver(broadcast,IntentFilter().apply {addAction(Intent.ACTION_BATTERY_CHANGED);addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_USER_PRESENT);addAction(Intent.ACTION_TIME_CHANGED);addAction(Intent.ACTION_TIMEZONE_CHANGED);addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)},Context.RECEIVER_NOT_EXPORTED)
        getSystemService(LauncherApps::class.java).registerCallback(packageCallback,handler)
        try {widgetHost.startListening()} catch(_: Exception) {}
    }
    override fun onResume() {super.onResume();if(drawnTheme!=theme.id||uiSettingsHash!=prefs.all.hashCode())show(page);updateRefresh();resumed=true;EventJournal.record("LAUNCHER","Ativo");networkChanged();updateClock();hud?.updateBattery(batteryLevel,plugged);handler.removeCallbacks(tick);handler.post(tick);maybeAccess();loadApps()}
    override fun onPause() {if(getSystemService(KeyguardManager::class.java).isKeyguardLocked || !getSystemService(PowerManager::class.java).isInteractive)gate.observeLocked();resumed=false;clearAppDrag();if(drawerDragging)finishDrawer(false, animate=false);clearDrawerVelocity();handler.removeCallbacks(tick);weatherGeneration++;weatherBusy=false;weather.cancel();hud?.stopMotion();access.stop();super.onPause()}
    override fun onStop() {TerminalWidget.refresh(this);getSystemService(android.net.ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback);diagnostics.stop();try {unregisterReceiver(broadcast)}catch(_:Exception){};getSystemService(LauncherApps::class.java).unregisterCallback(packageCallback);widgetHost.stopListening();super.onStop()}
    override fun onDestroy() {sessionGeneration++;weatherWorker.shutdownNow();io.shutdownNow();super.onDestroy()}
    override fun onWindowFocusChanged(hasFocus: Boolean) {super.onWindowFocusChanged(hasFocus);if(hasFocus)maybeAccess()}
    override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);setIntent(intent);if(intent.getStringExtra("neon.page")=="GRID"){query=intent.getStringExtra("neon.query")?.take(120)?:"";show("GRID")}else if(intent.hasCategory(Intent.CATEGORY_HOME)){homePage=0;show("HOME")}}
    override fun onBackPressed() {if(page!="HOME"||homePage!=0){homePage=0;show("HOME")} else if(!isDefaultHome())super.onBackPressed()}
    private fun openAtlas() {startActivity(Intent(this,AtlasActivity::class.java))}
    private fun dp(v: Int)=(v*resources.displayMetrics.density).toInt()
    private fun isDefaultHome(): Boolean = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
    private fun label(text: String,size: Float=14f,color: Int=textColor): TextView = TextView(this).apply {this.text=text;textSize=size;setTextColor(color);setPadding(0,dp(5),0,dp(5));typeface=if(theme.mono || prefs.getBoolean("mono",false))Typeface.MONOSPACE else Typeface.create(theme.font,Typeface.NORMAL)}
    private fun mono(text: String,size: Float=11f,color: Int=accent)=label(text,size,color).apply {typeface=Typeface.MONOSPACE;letterSpacing=.12f}
    private fun panel(): LinearLayout = LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(17),dp(13),dp(17),dp(13));background=theme.background(this@MainActivity);Themes.tag(this,"panel",theme)}
    private fun add(view: View,parent: LinearLayout=body,weight: Float=0f) {parent.addView(view,LinearLayout.LayoutParams(if(weight>0)0 else -1,if(weight>0)-1 else -2,weight).apply {bottomMargin=dp(10)})}
    private fun button(text: String, action: ()->Unit): TextView = label(text,13f,accent).apply {gravity=Gravity.CENTER;setPadding(dp(12),dp(13),dp(12),dp(13));background=theme.background(this@MainActivity,true);Themes.tag(this,"button",theme);isClickable=true;isFocusable=true;setOnClickListener {action()}}
    private fun show(target: String) {
        clearAppDrag()
        clearDrawerVelocity()
        drawerPanel?.animate()?.cancel()
        if (::pageScroll.isInitialized) pageScroll.animate().cancel()
        drawerPanel = null
        drawerDragging = false
        refreshAfterDrawer = false
        dragSources.clear()
        dropTargets.clear()
        drawerDragSources.clear()
        drawerDropTargets.clear()
        navTabs.clear()
        val animate=page!=target||lastHomePage!=homePage
        lastHomePage=homePage;gridSearch=null;uiSettingsHash=prefs.all.hashCode();hud?.stopMotion();hud=null;page=target;art.hudMode=target=="HOME";drawnTheme=theme.id;art.themeId=theme.id;art.accent=accent;art.invalidate();Themes.tag(root,"root",theme);atlasHomeButton=null;column.removeAllViews();clock=null;date=null;battery=null;gridBody=null
        val header=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        header.addView(mono(when(theme.id){Themes.TERMINAL->">_ NEON";Themes.CORPORATE->"NEON / INDUSTRIES";else->"N / OS"},14f),LinearLayout.LayoutParams(0,-2,1f));header.addView(mono(if(isDefaultHome())"LAUNCHER" else "COMPANION",9f,muted));column.addView(header)
        viewport = FrameLayout(this)
        pageScroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = target == "GRID" }
        body = scrollBody(pageScroll)
        viewport.addView(pageScroll, FrameLayout.LayoutParams(-1, -1))
        column.addView(viewport, LinearLayout.LayoutParams(-1, 0, 1f))
        when(target) {"HOME"->home();"GRID"->grid(body, query);"STATUS"->status();else->config()}
        refreshWallpaper()
        if(animate&&motionEnabled()){
            body.alpha=.6f
            body.translationY=dp(15).toFloat()
            body.animate().alpha(1f).translationY(0f).setDuration(240)
                .setInterpolator { progress -> if(progress>=1f)1f else (1-Math.exp(-7.0*progress)*Math.cos(10.0*progress)).toFloat() }.start()
        }
        val nav=LinearLayout(this).apply {setPadding(0,dp(8),0,dp(8));gravity=Gravity.CENTER}
        for(name in listOf("HOME","GRID","ATLAS","STATUS","CONFIG")) {val b=label(name,12f,if(name==page)accent else muted).apply {gravity=Gravity.CENTER;setPadding(0,dp(15),0,dp(15));typeface=Typeface.MONOSPACE;isFocusable=true;setOnClickListener {if(name=="ATLAS")startActivity(Intent(this@MainActivity,AtlasEntryActivity::class.java))else show(name)};setOnLongClickListener {if(name=="ATLAS"){openAtlas();true}else false};Themes.tag(this,"dock",theme)};navTabs[name]=b;nav.addView(b,LinearLayout.LayoutParams(0,-2,1f))};column.addView(nav)
        if (target == "HOME" && hud != null && !prefs.getBoolean("home_edit", false)) {
            val host = viewport
            host.post { if (viewport === host && page == "HOME" && !isDestroyed) prepareDrawer() }
        }
    }

    private fun scrollBody(scroll: ScrollView) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(16), 0, dp(14))
        scroll.addView(this)
    }

    private fun prepareDrawer(): FrameLayout {
        drawerPanel?.let { return it }
        val sheet = FrameLayout(this).apply {
            visibility = View.INVISIBLE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            tag = "neon:swipe-drawer"
        }
        sheet.addView(ArtView(this, accent).apply {
            systemWallpaper = art.systemWallpaper
            wallpaperImage = art.wallpaperImage
        }, FrameLayout.LayoutParams(-1, -1))
        val scroll = ScrollView(this).apply { isFillViewport = true }
        grid(scrollBody(scroll), "")
        sheet.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        drawerPanel = sheet
        viewport.addView(sheet, FrameLayout.LayoutParams(-1, -1))
        return sheet
    }

    private fun clearDrawerVelocity() {
        drawerVelocity?.recycle()
        drawerVelocity = null
    }

    private fun finishDrawer(open: Boolean, animate: Boolean = motionEnabled()) {
        val sheet = drawerPanel ?: return
        drawerDragging = false
        clearDrawerVelocity()
        if (open) {
            query = ""
            page = "GRID"
            navTabs.forEach { (name, tab) -> tab.setTextColor(if (name == page) accent else muted) }
            pageScroll.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            sheet.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
        val height = viewport.height.toFloat()
        val end = if (open) 0f else height
        val homeEnd = end - height
        val settle: () -> Unit = {
            if (open) {
                pageScroll.visibility = View.INVISIBLE
                hud?.stopMotion()
            } else sheet.visibility = View.INVISIBLE
            if (refreshAfterDrawer) {
                refreshAfterDrawer = false
                if (open) renderGrid() else show("HOME")
            }
        }
        if (animate && height > 0f && abs(sheet.translationY - end) > 1f) {
            val duration = (70f + 110f * abs(sheet.translationY - end) / height).toLong()
            val interpolator = DecelerateInterpolator()
            pageScroll.animate().translationY(homeEnd).setDuration(duration).setInterpolator(interpolator).start()
            sheet.animate().translationY(end).setDuration(duration)
                .setInterpolator(interpolator).withEndAction { settle() }.start()
        } else {
            sheet.translationY = end
            pageScroll.translationY = homeEnd
            settle()
        }
    }

    private fun dragDrawer(event: MotionEvent): Boolean {
        val sheet = drawerPanel ?: return false
        drawerVelocity?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val height = viewport.height.toFloat()
                val reveal = (touchY - event.y).coerceIn(0f, height)
                sheet.translationY = height - reveal
                pageScroll.translationY = -reveal
            }
            MotionEvent.ACTION_UP -> {
                val travel = (touchY - event.y).coerceAtLeast(0f)
                val velocity = drawerVelocity?.let { it.computeCurrentVelocity(1000); it.yVelocity } ?: 0f
                val open = travel >= minOf(viewport.height * .28f, dp(160).toFloat()) ||
                    (travel >= dp(24) && velocity < -dp(700))
                finishDrawer(open)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> finishDrawer(false)
        }
        return true
    }

    private fun home() {
        if(resources.configuration.fontScale>1.5f){legacyHome();return}
        val keys=homeLayout.page(homePage)
        val selected=keys.mapNotNull { key -> apps.firstOrNull { it.key==key } }
        if(homePage>0){
            add(mono("HOME / PÁGINA ${homePage+1}"))
            add(button("EDITAR PÁGINA"){editHome()})
            if(selected.isEmpty())add(label("Mova aplicativos para esta página pelo menu de pressão longa.",14f,muted))
            appGrid(selected,body)
            homeTools(keys)
            return
        }
        val chosen=selected.ifEmpty{if(homeLayout.configured)emptyList() else apps.filter{it.component.packageName!=packageName}.take(8)}
        val view=HudHomeView(this,chosen,::loadHudIcon,::launchApp,::appMenu,::hudRoute,::hudDock)
        hud=view
        for((index,icon) in view.dockViews.withIndex()) {
            val app=apps.firstOrNull { it.key==prefs.getString("dock_$index",null) }
            if(app!=null){icon.contentDescription="Dock / ${app.label}";loadHudIcon(app,icon)}
        }
        val ownBody=body
        val width=(resources.configuration.screenWidthDp-40).coerceAtLeast(250)
        val scale=prefs.getInt("hud_scale",1)
        ownBody.addView(view,LinearLayout.LayoutParams(-1,dp((width*when(scale){0->1.75f;2->2.5f;else->2.06f}).toInt())))
        if(scale==1)(ownBody.parent as? ScrollView)?.addOnLayoutChangeListener{_,_,top,_,bottom,_,_,_,_->
            val available=bottom-top-ownBody.paddingTop-ownBody.paddingBottom
            if(available>=dp(490)&&view.layoutParams.height!=available)view.layoutParams=view.layoutParams.apply{height=available}
        }
        view.updateBattery(batteryLevel,plugged)
        view.updateClockAndNetwork()
        renderWeather()
        homeTools(keys)
    }
    private fun homeTools(keys:List<String>){
        for(folder in keys.filter{it.startsWith("folder:")})add(button("▦  "+homeLayout.folderName(folder)){openFolder(folder)})
        val pages=LinearLayout(this)
        for(index in 0..5) {
            val tab=button(if(index==homePage)"●"else(index+1).toString()){homePage=index;show("HOME")}
            tab.contentDescription="Página ${index+1}"
            if (prefs.getBoolean("home_edit", false)) dropTargets[tab] = { key ->
                homeLayout.move(key, index)
                homePage = index
                show("HOME")
            }
            pages.addView(tab,LinearLayout.LayoutParams(0,dp(48),1f))
        }
        add(pages)
        add(button("TODOS OS APLICATIVOS  ↗"){show("GRID")})
        shortcuts()
        widgetViews()
    }
    private fun loadHudIcon(app:AppEntry,view:ImageView) {
        val style=theme
        val key="${style.id}:${style.accent}:${iconStore.revision}:${prefs.getString("iconPack","")}:${app.key}"
        val inset=if(iconStore.state(app,style.id)=="Original")dp(7)else dp(2)
        view.setPadding(inset,inset,inset,inset)
        view.tag=key
        fun display(drawable:android.graphics.drawable.Drawable){
            view.setImageDrawable(drawable.constantState?.newDrawable(resources)?.mutate()?:drawable)
            Themes.tag(view,"icon",style)
        }
        iconCache.get(key)?.let {display(it);return}
        if(io.isShutdown)return
        io.execute {
            val icon=runCatching {
                iconStore.drawable(app,style)
                    ?:if(iconStore.state(app,style.id)=="Original")repo.icon(app)
                    else packs.icon(prefs.getString("iconPack","").orEmpty(),app)?:repo.icon(app)
            }.getOrNull()
            handler.post {
                if(!isDestroyed&&icon!=null){
                    iconCache.put(key,icon)
                    if(view.tag==key)display(icon)
                }
            }
        }
    }
    private fun launchApp(app:AppEntry) {
        try{access.stop();repo.open(app)}
        catch(_:Exception){EventJournal.record("ERROR","Aplicativo indisponível");toast("Aplicativo indisponível ou perfil pausado.");loadApps()}
    }
    private fun hudRoute(target:String) {
        when(target) {
            "weather"->safeStart(Intent(this,HudSettingsActivity::class.java))
            "battery"->safeStart(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            "calendar"->safeStart(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))
            "connectivity"->safeStart(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            "atlas"->safeStart(Intent(this,AtlasEntryActivity::class.java))
            "atlas-panel"->openAtlas()
            "terminal"->safeStart(Intent(this,TerminalActivity::class.java))
            "edit"->editHome()
        }
    }
    private fun hudDock(index:Int,long:Boolean) {
        if(long){
            val names=listOf("Padrão","ATLAS","Gaveta","Configuração")+apps.map{it.label}
            AlertDialog.Builder(this).setTitle("Atalho ${index+1} da dock").setItems(names.toTypedArray()){_,choice->
                val key=when(choice){0->"";1->"atlas";2->"grid";3->"config";else->apps[choice-4].key}
                prefs.edit().putString("dock_$index",key).apply()
                show("HOME")
            }.show()
            return
        }
        val key=prefs.getString("dock_$index",null)
        apps.firstOrNull{it.key==key}?.let{launchApp(it);return}
        when(key){"atlas"->{hudRoute("atlas");return};"grid"->{show("GRID");return};"config"->{show("CONFIG");return}}
        when(index) {
            0->safeStart(Intent(Intent.ACTION_DIAL))
            1->safeStart(Intent(Intent.ACTION_SENDTO,Uri.parse("sms:")))
            2->show("GRID")
            3->hudRoute("atlas")
            else->show("CONFIG")
        }
    }
    private fun motionEnabled()=android.animation.ValueAnimator.areAnimatorsEnabled()&&
        !prefs.getBoolean("instant",false)&&!prefs.getBoolean("saver",false)&&!getSystemService(PowerManager::class.java).isPowerSaveMode
    private fun inside(view:View,x:Float,y:Float):Boolean {
        val bounds=Rect()
        return view.getGlobalVisibleRect(bounds)&&bounds.contains(x.toInt(),y.toInt())
    }
    private fun touchesWidget(view:View,x:Float,y:Float):Boolean {
        if(view is AppWidgetHostView)return inside(view,x,y)
        if(view is ViewGroup)for(index in 0 until view.childCount)if(touchesWidget(view.getChildAt(index),x,y))return true
        return false
    }
    private fun clearAppDrag() {
        appDrag?.tile?.apply {
            alpha = 1f
            translationX = 0f
            translationY = 0f
            translationZ = 0f
            isPressed = false
        }
        appDrag = null
        dragCandidate = null
    }

    private fun handleAppDrag(event: MotionEvent, drag: AppDrag): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                drag.tile.translationX = event.x - touchX
                drag.tile.translationY = event.y - touchY
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                val target = if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val targets = if (page == "GRID") drawerDropTargets else dropTargets
                    targets.entries.firstOrNull { (view, _) -> view !== drag.tile && inside(view, event.rawX, event.rawY) }
                } else null
                clearAppDrag()
                val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                try { super.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
                target?.value?.invoke(drag.key)
            }
        }
        return true
    }

    override fun dispatchTouchEvent(event:MotionEvent):Boolean {
        appDrag?.let { return handleAppDrag(event, it) }
        if (drawerDragging) return dragDrawer(event)
        val sources = if (page == "GRID") drawerDragSources else dragSources
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchX = event.x
                touchY = event.y
                dragCandidate = sources.entries.firstOrNull { (view, _) ->
                    inside(view, event.rawX, event.rawY)
                }?.let { (view, key) -> AppDrag(key, view) }
            }
            MotionEvent.ACTION_MOVE -> {
                val candidate = dragCandidate
                if (candidate != null && candidate.tile.isAttachedToWindow) {
                    val slop = ViewConfiguration.get(this).scaledTouchSlop
                    if (abs(event.x - touchX) > slop || abs(event.y - touchY) > slop) {
                        dragCandidate = null
                        if (event.eventTime - event.downTime >= ViewConfiguration.getLongPressTimeout()) {
                            appDrag = candidate
                            candidate.tile.alpha = .65f
                            candidate.tile.translationZ = dp(8).toFloat()
                            return handleAppDrag(event, candidate)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> dragCandidate = null
        }
        if(page!="HOME"||prefs.getBoolean("home_edit",false))return super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            touchInHud = hud?.let { inside(it, touchX, touchY) } == true
            touchInBody = inside(body, touchX, touchY) && !touchesWidget(body, touchX, touchY)
            pinchHandled = false
            clearDrawerVelocity()
            if (touchInHud && touchInBody) drawerVelocity = VelocityTracker.obtain().also { it.addMovement(event) }
        }
        val dx = event.x - touchX
        val dy = event.y - touchY
        if (event.actionMasked == MotionEvent.ACTION_MOVE && drawerVelocity != null) {
            if (!pinchHandled && event.pointerCount == 1 && -dy > ViewConfiguration.get(this).scaledTouchSlop &&
                -dy > abs(dx) * 1.35f && viewport.height > 0
            ) {
                val sheet = prepareDrawer()
                val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                try {
                    super.dispatchTouchEvent(cancel)
                    tapDetector.onTouchEvent(cancel)
                    pinchDetector.onTouchEvent(cancel)
                } finally {
                    cancel.recycle()
                }
                doubleTap = false
                drawerDragging = true
                sheet.animate().cancel()
                pageScroll.animate().cancel()
                (sheet.getChildAt(0) as ArtView).apply {
                    systemWallpaper = art.systemWallpaper
                    wallpaperImage = art.wallpaperImage
                    invalidate()
                }
                sheet.visibility = View.VISIBLE
                return dragDrawer(event)
            }
            drawerVelocity?.addMovement(event)
        }
        if (event.actionMasked in setOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN)) {
            clearDrawerVelocity()
        }
        if(touchInHud){
            pinchDetector.onTouchEvent(event)
            tapDetector.onTouchEvent(event)
        }
        if(event.actionMasked!=MotionEvent.ACTION_UP||!touchInBody)return super.dispatchTouchEvent(event)
        if(pinchHandled){pinchHandled=false;doubleTap=false;return true}
        if(doubleTap){
            doubleTap=false
            when(prefs.getInt("double_tap",0)){
                1->openAtlas()
                2->hudRoute("terminal")
                3->safeStart(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                else->openSearch()
            }
            return true
        }
        val distance=dp(72)
        if(abs(dx)>distance&&abs(dx)>abs(dy)*1.35f){
            homePage=(homePage+if(dx<0)1 else 5)%6
            show("HOME")
            return true
        }
        if(touchInHud&&dy>distance&&dy>abs(dx)*1.35f){
            openSearch()
            return true
        }
        return super.dispatchTouchEvent(event)
    }
    private fun openSearch(){
        query=""
        show("GRID")
        gridSearch?.let{field->field.requestFocus();field.post{
            getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(field,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }}
    }
    private fun editHome(){
        val editing=prefs.getBoolean("home_edit",false)
        val options=arrayOf("HUD / clima",if(editing)"Encerrar edição por arraste"else"Editar por arraste","Criar pasta","Widgets e atalhos","Wallpaper próprio","Ação de dois toques")
        AlertDialog.Builder(this).setTitle("Organizar a Home").setItems(options){_,index->
            when(index){
                0->hudRoute("weather")
                1->{
                    prefs.edit().putBoolean("home_edit",!editing).apply()
                    if(!editing&&homePage==0)homePage=1
                    show("HOME")
                    toast(if(editing)"Edição encerrada."else"Pressione um ícone e arraste sobre outro ou sobre o número da página.")
                }
                2->createFolder()
                3->{homePage=5;show("HOME")}
                4->safeStart(Intent(this,WallpaperActivity::class.java))
                5->AlertDialog.Builder(this).setTitle("Dois toques na área livre").setItems(arrayOf("Pesquisa","ATLAS","Terminal","Configuração de tela")){_,choice->prefs.edit().putInt("double_tap",choice).apply()}.show()
            }
        }.show()
    }
    private fun createFolder(){
        val selected=mutableSetOf<Int>()
        AlertDialog.Builder(this).setTitle("Selecione os apps da pasta").setMultiChoiceItems(apps.map{it.label}.toTypedArray(),null){_,index,on->
            if(on)selected.add(index) else selected.remove(index)
        }.setPositiveButton("Continuar"){_,_->
            if(selected.size<2)toast("Selecione dois apps ou mais.")
            else {
                val input=EditText(this).apply{hint="Nome da pasta";setSingleLine()}
                AlertDialog.Builder(this).setTitle("Nova pasta").setView(input).setPositiveButton("Criar"){_,_->
                    runCatching{homeLayout.createFolder(input.text.toString(),selected.map{apps[it].key},homePage)}
                        .onSuccess{show("HOME")}.onFailure{toast("Escolha um nome de 1 a 30 caracteres.")}
                }.setNegativeButton("Cancelar",null).show()
            }
        }.setNegativeButton("Cancelar",null).show()
    }
    private fun openFolder(id:String){
        val entries=homeLayout.folderApps(id).mapNotNull{key->apps.firstOrNull{it.key==key}}
        val holder=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        appGrid(entries,holder,false)
        val scroll=ScrollView(this).apply{addView(holder)}
        val dialog=AlertDialog.Builder(this).setTitle(homeLayout.folderName(id)).setView(scroll)
            .setNeutralButton("Desfazer pasta"){_,_->homeLayout.dissolve(id,homePage);show("HOME")}
            .setNegativeButton("Fechar",null).create()
        dialog.setOnShowListener{if(motionEnabled()){scroll.scaleX=.95f;scroll.scaleY=.95f;scroll.animate().scaleX(1f).scaleY(1f).setDuration(190).start()}}
        dialog.show()
    }
    private fun refreshWallpaper(){
        val id=theme.id
        val custom=prefs.getBoolean("wallpaper_use_$id",false)
        val system=prefs.getBoolean("system_wallpaper",false)
        val key="$id:$custom:$system:${prefs.getLong("wallpaper_revision",0)}"
        if(key==wallpaperKey)return
        wallpaperKey=key
        if(system){
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }else{
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
        }
        art.systemWallpaper=system
        art.wallpaperImage=null
        art.invalidate()
        if(custom&&!system&&!io.isShutdown)io.execute{
            val bitmap=WallpaperStore.load(this,id)
            handler.post{if(!isDestroyed&&key==wallpaperKey){art.wallpaperImage=bitmap;art.invalidate()}}
        }
    }
    private fun networkChanged() {
        val value=if(AtlasApi(this).connected())"Rede disponível" else "Offline"
        if(value!=networkState){networkState=value;EventJournal.record("NETWORK",value)}
        updateAtlasState()
        renderWeather()
    }
    private fun renderWeather() {
        val view=hud?:return
        val reading=weather.cached()
        val age=reading?.let{System.currentTimeMillis()-it.fetched}?:0L
        view.updateWeather(reading,weatherFailed||!AtlasApi(this).connected()||age>prefs.getInt("weather_interval",60).coerceAtLeast(30)*60000L)
    }
    private fun refreshWeather() {
        if(weatherBusy||!weather.due()||!AtlasApi(this).connected())return
        weatherBusy=true
        val generation=weatherGeneration
        weatherWorker.execute {
            val result=runCatching{weather.refresh()}
            handler.post {
                if(resumed&&generation==weatherGeneration){
                    weatherBusy=false
                    weatherFailed=result.isFailure
                    renderWeather()
                    if(weatherFailed)EventJournal.record("WEATHER","Consulta indisponível")
                }
            }
        }
    }
    private fun legacyHome() {
        add(mono(when(theme.id){Themes.TERMINAL->"user@neon:~ / HOME";Themes.CORPORATE->"PERSONNEL / PRIVATE WORKSPACE";else->"PERSONAL TERMINAL   /   01"}))
        clock=label("",64f).apply {typeface=if(theme.id==Themes.NEON)Typeface.create("sans-serif-thin",Typeface.NORMAL)else Typeface.create(theme.font,Typeface.NORMAL);letterSpacing=if(theme.mono)0f else -.04f;Themes.tag(this,"clock",theme)};add(clock!!)
        date=mono("",12f,muted);add(date!!);updateClock()
        val badge=panel();badge.addView(mono(if(theme.mono)"[ SYSTEM / ONLINE ]" else "CORE ONLINE",11f));badge.addView(label(when(theme.id){Themes.TERMINAL->"session.local > ready_";Themes.CORPORATE->"Your time. Company standard.";else->"Seu mundo. Outra frequência."},18f));battery=mono(if(batteryLevel>=0)"BATERIA  $batteryLevel%" else "BATERIA  —",10f,muted);badge.addView(battery);add(badge)
        if(!isDefaultHome()) {add(label("Modo recomendado · HyperOS preservado",12f,muted));add(button("PERSONALIZAR O POCO LAUNCHER") {nativeSetup()})}
        val atlas=button("ATLAS") {startActivity(Intent(this,AtlasEntryActivity::class.java))}
        atlas.setOnLongClickListener {openAtlas();true};Themes.tag(atlas,"atlas-home",theme);atlasHomeButton=atlas;updateAtlasState();add(atlas)
        add(mono("QUICK ACCESS",11f))
        val fav=prefs.getStringSet("favorites",emptySet())?:emptySet()
        val chosen=apps.filter {it.key in fav}.ifEmpty {apps.filter {it.component.packageName!=packageName}.take(8)}
        appGrid(chosen,body)
        add(button("TODOS OS APLICATIVOS  ↗") {show("GRID")})
        shortcuts()
        widgetViews()
    }
    private fun updateClock() {hud?.updateClockAndNetwork();val now=Date();clock?.text=SimpleDateFormat("HH:mm",Locale.getDefault()).format(now);date?.text=SimpleDateFormat("EEEE, dd MMMM",Locale("pt","BR")).format(now).uppercase(Locale("pt","BR"))}
    private fun loadApps() {
        if(io.isShutdown)return
        val generation=++sessionGeneration
        io.execute {
            val result = runCatching { repo.load() }.getOrDefault(emptyList())
            handler.post {
                if (isDestroyed || generation != sessionGeneration || apps == result) return@post
                apps = result
                when {
                    drawerDragging -> refreshAfterDrawer = true
                    page == "GRID" -> renderGrid()
                    page == "HOME" -> show("HOME")
                }
            }
        }
    }
    private fun grid(parent: LinearLayout, initialQuery: String) {
        add(mono("NEON GRID"), parent)
        add(label("Aplicativos",32f), parent)
        val search=EditText(this).apply {hint=if(theme.mono)"grep aplicativos..." else "Pesquisar aplicativos";background=theme.background(this@MainActivity,true);Themes.tag(this,"search",theme);setText(initialQuery);setTextColor(textColor);setHintTextColor(muted);textSize=16f;setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_TEXT;contentDescription="Pesquisar aplicativos";setPadding(dp(12),dp(12),dp(12),dp(12))}
        gridSearch=search
        add(search, parent)
        val cats=Spinner(this);val names=arrayOf("Todos","Jogos","Produtividade","Social","Mídia","Outros")
        cats.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,names)
        cats.setSelection(when(category){0->1;7->2;4->3;1->4;-1->5;else->0})
        cats.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = intArrayOf(-2, 0, 7, 4, 1, -1)[position]
                if (gridSearch === search && category != selected) {
                    category = selected
                    renderGrid()
                }
            }
        }
        add(cats, parent)
        add(button("ORDENAR / GRADE / OCULTOS"){gridOptions()}, parent)
        gridBody=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        add(gridBody!!, parent)
        search.addTextChangedListener(object: TextWatcher {override fun beforeTextChanged(s: CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s: CharSequence?,start:Int,before:Int,count:Int){query=s.toString();renderGrid()};override fun afterTextChanged(s:Editable?) {}})
        renderGrid()
    }
    private fun renderGrid() {
        val container=gridBody?:return
        clearAppDrag()
        drawerDragSources.clear()
        drawerDropTargets.clear()
        container.removeAllViews()
        val hidden=prefs.getStringSet("hidden_apps",emptySet()).orEmpty()
        val favorites=prefs.getStringSet("favorites",emptySet()).orEmpty()
        val order=gridOrder().withIndex().associate { it.value to it.index }
        val mode=prefs.getString("grid_sort","alpha")
        val searchText = gridSearch?.text?.toString().orEmpty()
        val filtered=apps.filter {it.key !in hidden && Policy.matches(it.label,searchText) && (category==-2 || if(category==1)it.category in 1..3 else it.category==category)}
            .sortedWith(compareBy<AppEntry>{when(mode){"manual"->order[it.key]?:Int.MAX_VALUE;"favorites"->if(it.key in favorites)0 else 1;else->0}}
                .thenBy(String.CASE_INSENSITIVE_ORDER){it.label})
        container.addView(mono("${filtered.size} ATIVIDADES  /  ${if(category==-1)"SEM CATEGORIA" else "PERFIS VISÍVEIS"}",9f,muted))
        appGrid(filtered,container, drawer=true)
        if(filtered.isEmpty())container.addView(label("Nenhum aplicativo encontrado.",14f,muted))
    }
    private fun appGrid(entries: List<AppEntry>,parent: LinearLayout,allowDrag:Boolean=true,drawer:Boolean=false) {
        val columns=prefs.getInt("grid_columns",if(resources.configuration.screenWidthDp>=600)6 else 4).coerceIn(3,6)
        val size=prefs.getInt("icon_size",58).coerceAtMost((resources.configuration.screenWidthDp-48)/columns-6).coerceAtLeast(30)
        val draggable=allowDrag&&if(drawer)prefs.getString("grid_sort","alpha")=="manual" else prefs.getBoolean("home_edit",false)
        val sources = if (drawer) drawerDragSources else dragSources
        val targets = if (drawer) drawerDropTargets else dropTargets
        if (draggable) parent.clipChildren = false
        entries.chunked(columns).forEach { rowEntries ->
            val row=LinearLayout(this).apply { clipChildren = !draggable }
            for(app in rowEntries) {
                val tile=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(3),dp(8),dp(3),dp(8));isFocusable=true;contentDescription="Abrir ${app.label}"}
                val frame=FrameLayout(this).apply {background=theme.background(this@MainActivity,true);Themes.tag(this,"icon-frame",theme)}
                val icon=ImageView(this).apply {setPadding(dp(11),dp(11),dp(11),dp(11));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
                frame.addView(icon,FrameLayout.LayoutParams(-1,-1));tile.addView(frame,LinearLayout.LayoutParams(dp(size),dp(size)))
                val title=label(app.label,12f).apply {gravity=Gravity.CENTER;maxLines=2;ellipsize=TextUtils.TruncateAt.END;minHeight=dp(35)};tile.addView(title)
                tile.setOnClickListener {launchApp(app)}
                tile.setOnLongClickListener {
                    if (dragCandidate?.tile !== tile) appMenu(app)
                    true
                }
                if (draggable) sources[tile] = app.key
                if (draggable) targets[tile] = { key ->
                    if (!drawer) {
                        homeLayout.move(key, homePage, app.key)
                        show("HOME")
                    } else {
                        val keys = (gridOrder() + apps.map { it.key }).distinct().toMutableList()
                        keys.remove(key)
                        keys.add(keys.indexOf(app.key).coerceAtLeast(0), key)
                        prefs.edit().putString("grid_order", JSONArray(keys).toString()).apply()
                        renderGrid()
                    }
                }
                loadHudIcon(app,icon)
                row.addView(tile,LinearLayout.LayoutParams(0,-2,1f))
            }
            repeat(columns-rowEntries.size){row.addView(View(this),LinearLayout.LayoutParams(0,1,1f))};parent.addView(row)
        }
    }
    private fun gridOrder():List<String>{
        val array=runCatching{JSONArray(prefs.getString("grid_order","[]"))}.getOrElse{JSONArray()}
        return (0 until array.length()).map{array.getString(it)}
    }
    private fun gridOptions(){
        AlertDialog.Builder(this).setItems(arrayOf("Alfabética","Manual por arraste","Favoritos primeiro","Colunas","Tamanho dos ícones","Reexibir apps ocultos")){_,index->
            when(index){
                0,1,2->{prefs.edit().putString("grid_sort",listOf("alpha","manual","favorites")[index]).apply();renderGrid()}
                3->AlertDialog.Builder(this).setItems(arrayOf("3","4","5","6")){_,n->prefs.edit().putInt("grid_columns",n+3).apply();renderGrid()}.show()
                4->AlertDialog.Builder(this).setItems(arrayOf("Compacto","Padrão","Grande")){_,n->prefs.edit().putInt("icon_size",intArrayOf(44,58,72)[n]).apply();renderGrid()}.show()
                5->{
                    val hidden=prefs.getStringSet("hidden_apps",emptySet()).orEmpty()
                    val choices=apps.filter{it.key in hidden}
                    if(choices.isEmpty())toast("Nenhum aplicativo oculto.")
                    else AlertDialog.Builder(this).setTitle("Toque para reexibir").setItems(choices.map{it.label}.toTypedArray()){_,n->
                        prefs.edit().putStringSet("hidden_apps",hidden-choices[n].key).apply()
                        renderGrid()
                    }.show()
                }
            }
        }.show()
    }
    private fun appMenu(app:AppEntry){
        val favorites=prefs.getStringSet("favorites",emptySet()).orEmpty().toMutableSet()
        val title=if(app.key in favorites)"Remover dos favoritos"else"Adicionar aos favoritos"
        AlertDialog.Builder(this).setTitle(app.label).setItems(arrayOf(title,"Informações do aplicativo","Mover para página","Ocultar da gaveta","Personalizar ícone")){_,index->
            when(index){
                0->{
                    if(favorites.add(app.key))homeLayout.move(app.key,0)
                    else {favorites.remove(app.key);homeLayout.unpin(app.key)}
                    prefs.edit().putStringSet("favorites",favorites).apply()
                    show(page)
                }
                1->runCatching{repo.info(app)}.onFailure{toast("Informações indisponíveis.")}
                2->AlertDialog.Builder(this).setTitle("Página de destino").setItems((1..6).map{"Página $it"}.toTypedArray()){_,n->homeLayout.move(app.key,n);show(page)}.show()
                3->{prefs.edit().putStringSet("hidden_apps",prefs.getStringSet("hidden_apps",emptySet()).orEmpty()+app.key).apply();renderGrid()}
                4->safeStart(Intent(this,IconStudioActivity::class.java))
            }
        }.show()
    }
    private fun shortcuts() {
        val array=JSONArray(prefs.getString("shortcuts","[]"));if(array.length()==0)return
        add(mono("ATALHOS FIXADOS"))
        for(i in 0 until array.length()) {
            val s=array.getJSONObject(i)
            val b=button(s.optString("label","Atalho")) {
                try {val user=getSystemService(UserManager::class.java).getUserForSerialNumber(s.getLong("serial"))?:throw IllegalStateException();getSystemService(LauncherApps::class.java).startShortcut(s.getString("package"),s.getString("id"),null,null,user)}catch(_:Exception){toast("Atalho indisponível. Pressione para remover.")}
            }
            b.setOnLongClickListener {AlertDialog.Builder(this).setMessage("Remover atalho?").setPositiveButton("Remover"){_,_->array.remove(i);prefs.edit().putString("shortcuts",array.toString()).apply();show("HOME")}.setNegativeButton("Cancelar",null).show();true};add(b)
        }
    }
    private fun status() {
        add(mono("NEON STATUS"));add(label("Sinais reais.",32f))
        val fs=StatFs(filesDir.absolutePath);val gb=1024.0*1024*1024
        val card=panel();card.addView(mono("DISPOSITIVO",10f));card.addView(label(Build.MODEL,22f));card.addView(label("Android ${Build.VERSION.RELEASE} · SDK ${Build.VERSION.SDK_INT}\nBuild: ${Build.DISPLAY}",12f,muted));card.addView(label("HyperOS: confirme a versão em Sobre o telefone. A build pública não identifica sempre a versão comercial.",12f,muted));add(card)
        battery=label(if(batteryLevel>=0)"BATERIA  $batteryLevel%" else "BATERIA  —",20f);add(battery!!)
        add(label(String.format(Locale.getDefault(),"Armazenamento da partição de dados\n%.1f GiB livres / %.1f GiB disponíveis ao sistema",fs.availableBytes/gb,fs.totalBytes/gb),16f))
        add(label("Tela ativa: ${display?.refreshRate?.toInt()?:0} Hz\nModos: ${display?.supportedModes?.map {it.refreshRate.toInt()}?.distinct()?.joinToString()}",14f,muted))
        add(button("ABRIR SOBRE O TELEFONE") {safeStart(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))})
        add(button("INICIAR MEDIÇÃO LOCAL") {diagnostics.start();toast("Medição iniciada. Navegue pelo app e volte aqui.")})
        add(button("VER / EXPORTAR DIAGNÓSTICO") {diagnosticText=diagnostics.report().toString(2);diagnostics.stop();AlertDialog.Builder(this).setTitle("Medição do processo NEON").setMessage(diagnosticText).setPositiveButton("Exportar JSON"){_,_->safeStartForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"neon-diagnostico.json"),200)}.setNegativeButton("Fechar",null).show()})
        add(label("A medição termina quando o app fica invisível. Não mede a autenticação nem o desempenho de outros apps.",12f,muted))
    }
    private fun config() {
        add(mono("NEON CONFIG"));add(label("Sua frequência.",32f))
        add(mono("TEMA ATIVO / ${theme.title}"))
        for(id in Themes.ids)add(button(Themes.spec(id).title) {Themes.select(this,id);art.themeId=id;art.accent=accent;art.invalidate();show("CONFIG")})
        add(button("WALLPAPER PRÓPRIO"){safeStart(Intent(this,WallpaperActivity::class.java))})
        add(button("ORGANIZAR HOME / GESTOS"){editHome()})
        add(button("VERIFICAR ATUALIZAÇÕES"){safeStart(Intent(this,ReleaseActivity::class.java))})
        add(button("MODO DE DESEMPENHO"){
            AlertDialog.Builder(this).setItems(arrayOf("Visual completo","Equilibrado","Econômico")){_,i->
                prefs.edit().putString("performance",listOf("full","balanced","economy")[i]).putBoolean("saver",i==2).apply()
                updateRefresh()
                show("CONFIG")
            }.show()
        })
        add(button("NEON ICON STUDIO"){safeStart(Intent(this,IconStudioActivity::class.java))})
        add(button("HUD / CLIMA"){hudRoute("weather")})
        add(button("TERMINAL / EVENTOS REAIS"){hudRoute("terminal")})
        add(button("CONFIGURAR ATLAS") {openAtlas()})
        add(button("WIDGET TERMINAL"){if(widgetManager.isRequestPinAppWidgetSupported)widgetManager.requestPinAppWidget(ComponentName(this,TerminalWidget::class.java),null,null)else toast("Adicione NEON Terminal pelo menu Widgets.")})
        add(button("WIDGET COMPACTO ATLAS") {if(widgetManager.isRequestPinAppWidgetSupported)widgetManager.requestPinAppWidget(ComponentName(this,AtlasWidget::class.java),null,null)else toast("Adicione ATLAS pelo menu Widgets do launcher.")})
        add(button("GUIA NATIVO / PRESERVAR GESTOS") {nativeSetup()})
        add(button("TESTAR COMO LAUNCHER PADRÃO") {
            AlertDialog.Builder(this).setTitle("Compatibilidade HyperOS").setMessage("O HyperOS pode mudar a navegação para botões ao selecionar um launcher externo. Não há correção segura por API pública. Continue apenas para testar. Para preservar os gestos, mantenha o POCO Launcher. Seus ícones e widgets antigos permanecem no launcher original; não são importados automaticamente.")
                .setPositiveButton("Abrir escolha do Android") {_,_->val role=getSystemService(RoleManager::class.java);if(role.isRoleAvailable(RoleManager.ROLE_HOME))safeStart(role.createRequestRoleIntent(RoleManager.ROLE_HOME))else safeStart(Intent(Settings.ACTION_HOME_SETTINGS))}.setNegativeButton("Manter atual",null).show()
        })
        add(button("RESTAURAR POCO LAUNCHER") {safeStart(Intent(Settings.ACTION_HOME_SETTINGS))})
        add(mono("NEON ACCESS  /  TRANSIÇÃO VISUAL"))
        add(label("A autenticação pertence ao HyperOS. Este efeito decorativo só aparece sobre a Home NEON quando um desbloqueio é observado; não aparece sobre outros aplicativos. No modo nativo, use o efeito oficial de digital.",12f,muted))
        val effects=Spinner(this).apply {adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,Policy.effects);setSelection(prefs.getInt("effect",2).coerceIn(0,4))}
        effects.onItemSelectedListener=object: AdapterView.OnItemSelectedListener {override fun onNothingSelected(p:AdapterView<*>?){};override fun onItemSelected(p:AdapterView<*>?,v:View?,pos:Int,id:Long){prefs.edit().putInt("effect",pos).apply()}};add(effects)
        switch("Modo instantâneo", "instant",false)
        switch("Modo econômico · sem animação", "saver",false)
        switch("Som curto opcional", "sound",false)
        switch("Solicitar até 120 Hz", "highRefresh",false)
        switch("Fonte monoespaçada", "mono",false)
        slider("Duração", "duration",200,350,280," ms")
        slider("Brilho do efeito", "brightness",10,100,80,"%")
        slider("Intensidade", "intensity",10,100,65,"%")
        add(button("PRÉVIA DO EFEITO") {playAccess()})
        add(mono("PALETA"))
        val palette=LinearLayout(this)
        for((name,color) in listOf("CYAN" to Color.rgb(58,242,228),"VIOLET" to Color.rgb(176,122,255),"MAGENTA" to Color.rgb(255,91,197),"RED" to Color.rgb(255,105,115))) {
            val b=label(name,10f,color).apply {gravity=Gravity.CENTER;setPadding(0,dp(18),0,dp(18));isFocusable=true;setOnClickListener {prefs.edit().putInt("accent",color).apply();NeonWidget.refresh(this@MainActivity);AtlasWidget.refresh(this@MainActivity);art.accent=color;art.invalidate();iconCache.evictAll();show("CONFIG")}};palette.addView(b,LinearLayout.LayoutParams(0,-2,1f))
        };add(palette)
        add(button("WALLPAPER EXCLUSIVO  /  APLICAR") {wallpaperDialog()})
        add(button("EXPORTAR WALLPAPER PNG") {safeStartForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("image/png").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"NEON-CORE-1220x2712.png"),201)})
        add(button("PACOTE DE ÍCONES INSTALADO") {
            val options=listOf("" to "Ícones originais com moldura NEON")+packs.packs()
            AlertDialog.Builder(this).setTitle("Pacote compatível com appfilter.xml").setItems(options.map{it.second}.toTypedArray()){_,i->prefs.edit().putString("iconPack",options[i].first).apply();iconStore.changeMode(if(options[i].first.isBlank())"original" else "pack");iconCache.evictAll();toast("Pacote selecionado para a Home NEON.")}.show()
        })
        add(label("Pacotes ADW/appfilter e ícones originais. Ícones do perfil de trabalho mantêm o distintivo do Android. Máscaras e formatos proprietários de temas não são importados.",12f,muted))
        add(button("ADICIONAR WIDGET À HOME NEON") {pickWidget()})
        add(button("ADICIONAR RELÓGIO AO LAUNCHER ATUAL") {
            val component=ComponentName(this,NeonWidget::class.java)
            if(widgetManager.isRequestPinAppWidgetSupported)widgetManager.requestPinAppWidget(component,null,null)else toast("No launcher original, mantenha a tela pressionada e abra Widgets → NEON OS.")
        })
        add(button("TEMAS DO HYPEROS") {val launch=packageManager.getLaunchIntentForPackage("com.android.thememanager");if(launch!=null)safeStart(launch)else safeStart(Intent(Settings.ACTION_SETTINGS))})
        add(label("NEON OS ${BuildConfig.VERSION_NAME} · clima e atualizações opcionais. Microfone, Bluetooth e localização aproximada dependem de autorização.\nNão modifica apps, SystemUI, AOD ou o mecanismo de bloqueio.",12f,muted))
    }
    private fun switch(title:String,key:String,default:Boolean) {
        val control=Switch(this).apply {text=title;setTextColor(textColor);textSize=14f;isChecked=prefs.getBoolean(key,default);setPadding(0,dp(6),0,dp(6));setOnCheckedChangeListener{_,checked->prefs.edit().putBoolean(key,checked).apply();if(key=="highRefresh"||key=="saver")updateRefresh()}};add(control)
    }
    private fun updateRefresh() {
        val power=getSystemService(PowerManager::class.java).isPowerSaveMode||prefs.getBoolean("saver",false)
        val requested=if(prefs.getBoolean("highRefresh",false)&&!power)minOf(120f,display?.supportedModes?.maxOfOrNull {it.refreshRate}?:60f)else 0f
        window.attributes=window.attributes.apply {preferredRefreshRate=requested}
    }
    private fun slider(title:String,key:String,min:Int,max:Int,default:Int,suffix:String) {
        val caption=label("$title: ${prefs.getInt(key,default)}$suffix",13f,muted);add(caption)
        val slider=SeekBar(this).apply {this.max=max-min;progress=prefs.getInt(key,default)-min;contentDescription=title}
        slider.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {override fun onStartTrackingTouch(s:SeekBar?){};override fun onStopTrackingTouch(s:SeekBar?){};override fun onProgressChanged(s:SeekBar?,p:Int,user:Boolean){if(user){prefs.edit().putInt(key,p+min).apply();caption.text="$title: ${p+min}$suffix"}}});add(slider)
    }
    private fun nativeSetup() {
        AlertDialog.Builder(this).setTitle("Visual NEON com gestos originais").setMessage("1. Confira HyperOS em Configurações → Sobre o telefone.\n\n2. Mantenha POCO Launcher como app inicial.\n\n3. Aplique o wallpaper NEON na tela inicial e no bloqueio (opções abaixo).\n\n4. Adicione o widget Relógio NEON pelo menu Widgets.\n\n5. No app Temas/Personalização, ajuste relógio, ícones, notificações e profundidade conforme as opções da sua versão.\n\n6. Em Impressões digitais, dados faciais e bloqueio, escolha uma animação de digital oficial que combine. Os nomes e opções variam por build.\n\n7. Para AOD, use apenas o painel nativo, se disponível.\n\nNão há tema MTZ instalado automaticamente nem integração pública para substituir o relógio real ou animações biométricas.")
            .setPositiveButton("Aplicar wallpaper") {_,_->wallpaperDialog()}.setNeutralButton("Sobre o telefone"){_,_->safeStart(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))}.setNegativeButton("Fechar",null).show()
    }
    private fun wallpaperDialog() {
        AlertDialog.Builder(this).setTitle("Aplicar wallpaper NEON").setMessage("Altera somente a imagem. A restauração do wallpaper anterior é feita em Temas/Fundos do HyperOS. O relógio e a autenticação permanecem sob controle do sistema.")
            .setPositiveButton("Inicial + bloqueio"){_,_->applyWallpaper(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)}
            .setNeutralButton("Só bloqueio"){_,_->applyWallpaper(WallpaperManager.FLAG_LOCK)}.setNegativeButton("Cancelar",null).show()
    }
    private fun applyWallpaper(flags:Int) {
        toast("Gerando wallpaper…")
        io.execute {var ok=false;try {val image=NeonArt.wallpaper(1220,2712,accent,theme.id);try{WallpaperManager.getInstance(this).setBitmap(image,null,true,flags);ok=true;EventJournal.record("WALLPAPER","Wallpaper NEON aplicado")}finally{image.recycle()}}catch(_:Exception){};handler.post {if(!isDestroyed)toast(if(ok)"Wallpaper aplicado." else "Aplicação bloqueada pelo sistema. Exporte o PNG e aplique pelo app Temas.")}}
    }
    private fun maybeAccess() {
        if(!resumed)return
        val locked=getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if(!locked && window.decorView.hasWindowFocus())gate.visibleUnlocked(SystemClock.elapsedRealtime())
        if(gate.consume(SystemClock.elapsedRealtime(),locked) && isDefaultHome() && page=="HOME")playAccess()
    }
    private fun playAccess() {
        val saver=prefs.getBoolean("saver",false)||getSystemService(PowerManager::class.java).isPowerSaveMode
        val animations=Settings.Global.getFloat(contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0
        val ms=Policy.duration(prefs.getInt("duration",280),prefs.getBoolean("instant",false),saver,animations)
        access.accent=accent;access.effect=prefs.getInt("effect",2);access.intensity=prefs.getInt("intensity",65)/100f;access.luminance=prefs.getInt("brightness",80)/100f;access.play(ms)
        if(ms>0 && prefs.getBoolean("sound",false)) {val audio=getSystemService(AudioManager::class.java);if(audio.ringerMode==AudioManager.RINGER_MODE_NORMAL){try {val tone=ToneGenerator(AudioManager.STREAM_SYSTEM,18);tone.startTone(ToneGenerator.TONE_PROP_ACK,60);handler.postDelayed({tone.release()},120)}catch(_:Exception){}}}
    }
    private fun savedWidgets(): MutableSet<String> =(prefs.getStringSet("widgets",emptySet())?:emptySet()).toMutableSet()
    private fun pickWidget() {
        if(pendingWidget!=-1){toast("Conclua a seleção atual de widget.");return}
        val providers=widgetManager.installedProviders.sortedBy {it.loadLabel(packageManager)}
        if(providers.isEmpty()){toast("Nenhum provedor de widgets disponível.");return}
        AlertDialog.Builder(this).setTitle("Widget Android").setItems(providers.map{it.loadLabel(packageManager)}.toTypedArray()) {_,index ->
            val provider=providers[index];pendingWidget=widgetHost.allocateAppWidgetId()
            if(widgetManager.bindAppWidgetIdIfAllowed(pendingWidget,provider.provider))configureWidget()
            else safeStartForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidget).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,provider.provider),100)
        }.show()
    }
    private fun configureWidget() {
        val info=widgetManager.getAppWidgetInfo(pendingWidget)
        if(info==null){cancelWidget();return}
        if(info.configure!=null) {
            widgetConfiguring=true
            try {widgetHost.startAppWidgetConfigureActivityForResult(this,pendingWidget,0,101,null)}catch(_:Exception){cancelWidget();toast("Configuração do widget indisponível.")}
        }else finishWidget()
    }
    private fun cancelWidget() {if(pendingWidget!=-1)widgetHost.deleteAppWidgetId(pendingWidget);pendingWidget=-1;widgetConfiguring=false}
    private fun finishWidget() {if(pendingWidget!=-1){val all=savedWidgets();all.add(pendingWidget.toString());prefs.edit().putStringSet("widgets",all).apply()};pendingWidget=-1;widgetConfiguring=false;show("HOME")}
    private fun widgetViews() {
        for(key in savedWidgets()) {
            val id=key.toIntOrNull()?:continue;val info=widgetManager.getAppWidgetInfo(id)
            if(info==null){widgetHost.deleteAppWidgetId(id);val all=savedWidgets();all.remove(key);prefs.edit().putStringSet("widgets",all).apply();continue}
            try {
                val card=panel();val view=widgetHost.createView(this,id,info)
                val width=resources.configuration.screenWidthDp-74;val height=maxOf(110,(info.minHeight.coerceAtMost(dp(340))/resources.displayMetrics.density).toInt())
                view.updateAppWidgetSize(null,width,height,width,height)
                card.addView(view,LinearLayout.LayoutParams(-1,dp(height)))
                card.addView(button("REMOVER WIDGET") {AlertDialog.Builder(this).setMessage("Remover este widget da Home NEON?").setPositiveButton("Remover"){_,_->widgetHost.deleteAppWidgetId(id);val all=savedWidgets();all.remove(key);prefs.edit().putStringSet("widgets",all).apply();show("HOME")}.setNegativeButton("Cancelar",null).show()});add(card)
            }catch(_:Exception){add(label("Widget indisponível nesta sessão.",12f,muted));add(button("REMOVER WIDGET INDISPONÍVEL"){widgetHost.deleteAppWidgetId(id);val all=savedWidgets();all.remove(key);prefs.edit().putStringSet("widgets",all).apply();show("HOME")})}
        }
    }
    override fun onActivityResult(request:Int,result:Int,data:Intent?) {
        super.onActivityResult(request,result,data)
        when(request) {
            100->if(result==RESULT_OK)configureWidget()else cancelWidget()
            101->if(result==RESULT_OK)finishWidget()else cancelWidget()
            200,201 -> if(result==RESULT_OK && data?.data!=null)writeDocument(data.data!!,request)
        }
    }
    private fun writeDocument(uri:Uri,request:Int) {
        io.execute {var ok=false;try {contentResolver.openOutputStream(uri)?.use {stream->if(request==200)stream.write(diagnosticText.toByteArray()) else {val image=NeonArt.wallpaper(1220,2712,accent,theme.id);try {image.compress(Bitmap.CompressFormat.PNG,100,stream)}finally{image.recycle()}};ok=true}}catch(_:Exception){};handler.post{if(!isDestroyed)toast(if(ok)"Arquivo exportado." else "Não foi possível exportar.")}}
    }
    private fun safeStart(intent:Intent) {try{startActivity(intent)}catch(_:Exception){toast("Esta tela não está disponível nesta versão do sistema.")}}
    private fun safeStartForResult(intent:Intent,code:Int) {try{startActivityForResult(intent,code)}catch(_:Exception){if(code==100||code==101)cancelWidget();toast("Ação não disponível no sistema.")}}
    private fun toast(text:String)=Toast.makeText(this,text,Toast.LENGTH_LONG).show()
}
