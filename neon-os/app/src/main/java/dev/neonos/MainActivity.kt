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
import android.widget.*
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

@Suppress("DEPRECATION")
class MainActivity: Activity() {
    private val prefs by lazy { getSharedPreferences("neon",0) }
    private val repo by lazy { AppRepository(this) }
    private val packs by lazy { IconPack(this) }
    private val io=Executors.newSingleThreadExecutor()
    private val handler=Handler(Looper.getMainLooper())
    private val iconCache=android.util.LruCache<String,android.graphics.drawable.Drawable>(80)
    private var apps=emptyList<AppEntry>();private var page="HOME";private var query="";private var category=-2
    private lateinit var root: FrameLayout;private lateinit var column: LinearLayout;private lateinit var body: LinearLayout
    private lateinit var access: AccessView;private lateinit var art: ArtView
    private lateinit var diagnostics: Diagnostics
    private var clock: TextView?=null;private var date: TextView?=null;private var battery: TextView?=null
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
    private val tick=object: Runnable {override fun run() {if(!resumed)return;updateClock();handler.postDelayed(this,60000-System.currentTimeMillis()%60000)}}
    private val broadcast=object: BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when(i.action) {
                Intent.ACTION_BATTERY_CHANGED -> {val scale=i.getIntExtra("scale",100);batteryLevel=if(scale>0)i.getIntExtra("level",0)*100/scale else -1;battery?.text=if(batteryLevel>=0)"BATERIA  $batteryLevel%" else "BATERIA  —"}
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
        window.decorView.systemUiVisibility=0
        window.setDecorFitsSystemWindows(true)
        root=FrameLayout(this)
        art=ArtView(this,accent);root.addView(art,FrameLayout.LayoutParams(-1,-1))
        column=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(10),dp(20),0)}
        root.addView(column,FrameLayout.LayoutParams(-1,-1))
        root.setOnApplyWindowInsetsListener { _,insets ->
            val bar=insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            column.setPadding(dp(20)+bar.left,dp(10)+bar.top,dp(20)+bar.right,bar.bottom)
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
        registerReceiver(broadcast,IntentFilter().apply {addAction(Intent.ACTION_BATTERY_CHANGED);addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_USER_PRESENT);addAction(Intent.ACTION_TIME_CHANGED);addAction(Intent.ACTION_TIMEZONE_CHANGED);addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)},Context.RECEIVER_NOT_EXPORTED)
        getSystemService(LauncherApps::class.java).registerCallback(packageCallback,handler)
        try {widgetHost.startListening()} catch(_: Exception) {}
    }
    override fun onResume() {super.onResume();if(drawnTheme!=theme.id){art.themeId=theme.id;art.accent=accent;art.invalidate();show(page)};updateRefresh();resumed=true;updateClock();handler.removeCallbacks(tick);handler.post(tick);maybeAccess();loadApps()}
    override fun onPause() {if(getSystemService(KeyguardManager::class.java).isKeyguardLocked || !getSystemService(PowerManager::class.java).isInteractive)gate.observeLocked();resumed=false;handler.removeCallbacks(tick);access.stop();super.onPause()}
    override fun onStop() {diagnostics.stop();try {unregisterReceiver(broadcast)}catch(_:Exception){};getSystemService(LauncherApps::class.java).unregisterCallback(packageCallback);widgetHost.stopListening();super.onStop()}
    override fun onDestroy() {sessionGeneration++;io.shutdownNow();super.onDestroy()}
    override fun onWindowFocusChanged(hasFocus: Boolean) {super.onWindowFocusChanged(hasFocus);if(hasFocus)maybeAccess()}
    override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);setIntent(intent);if(intent.getStringExtra("neon.page")=="GRID"){query=intent.getStringExtra("neon.query")?.take(120)?:"";show("GRID")}else if(intent.hasCategory(Intent.CATEGORY_HOME))show("HOME")}
    override fun onBackPressed() {if(page!="HOME")show("HOME") else if(!isDefaultHome())super.onBackPressed()}
    private fun openAtlas() {startActivity(Intent(this,AtlasActivity::class.java))}
    private fun dp(v: Int)=(v*resources.displayMetrics.density).toInt()
    private fun isDefaultHome(): Boolean = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
    private fun label(text: String,size: Float=14f,color: Int=textColor): TextView = TextView(this).apply {this.text=text;textSize=size;setTextColor(color);setPadding(0,dp(5),0,dp(5));typeface=if(theme.mono || prefs.getBoolean("mono",false))Typeface.MONOSPACE else Typeface.create(theme.font,Typeface.NORMAL)}
    private fun mono(text: String,size: Float=11f,color: Int=accent)=label(text,size,color).apply {typeface=Typeface.MONOSPACE;letterSpacing=.12f}
    private fun panel(): LinearLayout = LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(17),dp(13),dp(17),dp(13));background=theme.background(this@MainActivity);Themes.tag(this,"panel",theme)}
    private fun add(view: View,parent: LinearLayout=body,weight: Float=0f) {parent.addView(view,LinearLayout.LayoutParams(if(weight>0)0 else -1,if(weight>0)-1 else -2,weight).apply {bottomMargin=dp(10)})}
    private fun button(text: String, action: ()->Unit): TextView = label(text,13f,accent).apply {gravity=Gravity.CENTER;setPadding(dp(12),dp(13),dp(12),dp(13));background=theme.background(this@MainActivity,true);Themes.tag(this,"button",theme);isClickable=true;isFocusable=true;setOnClickListener {action()}}
    private fun show(target: String) {
        page=target;drawnTheme=theme.id;Themes.tag(root,"root",theme);column.removeAllViews();clock=null;date=null;battery=null;gridBody=null
        val header=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        header.addView(mono(when(theme.id){Themes.TERMINAL->">_ NEON";Themes.CORPORATE->"NEON / INDUSTRIES";else->"N / OS"},14f),LinearLayout.LayoutParams(0,-2,1f));header.addView(mono(if(isDefaultHome())"LAUNCHER" else "COMPANION",9f,muted));column.addView(header)
        val scroll=ScrollView(this).apply {isFillViewport=true;isVerticalScrollBarEnabled=false}
        body=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(0,dp(16),0,dp(14))};scroll.addView(body)
        column.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        when(target) {"HOME"->home();"GRID"->grid();"STATUS"->status();else->config()}
        val nav=LinearLayout(this).apply {setPadding(0,dp(8),0,dp(8));gravity=Gravity.CENTER}
        for(name in listOf("HOME","GRID","ATLAS","STATUS","CONFIG")) {val b=label(name,12f,if(name==page)accent else muted).apply {gravity=Gravity.CENTER;setPadding(0,dp(15),0,dp(15));typeface=Typeface.MONOSPACE;isFocusable=true;setOnClickListener {if(name=="ATLAS")startActivity(Intent(this@MainActivity,AtlasEntryActivity::class.java))else show(name)};setOnLongClickListener {if(name=="ATLAS"){openAtlas();true}else false};Themes.tag(this,"dock",theme)};nav.addView(b,LinearLayout.LayoutParams(0,-2,1f))};column.addView(nav)
    }
    private fun home() {
        add(mono(when(theme.id){Themes.TERMINAL->"user@neon:~ / HOME";Themes.CORPORATE->"PERSONNEL / PRIVATE WORKSPACE";else->"PERSONAL TERMINAL   /   01"}))
        clock=label("",64f).apply {typeface=if(theme.id==Themes.NEON)Typeface.create("sans-serif-thin",Typeface.NORMAL)else Typeface.create(theme.font,Typeface.NORMAL);letterSpacing=if(theme.mono)0f else -.04f;Themes.tag(this,"clock",theme)};add(clock!!)
        date=mono("",12f,muted);add(date!!);updateClock()
        val badge=panel();badge.addView(mono(if(theme.mono)"[ SYSTEM / ONLINE ]" else "CORE ONLINE",11f));badge.addView(label(when(theme.id){Themes.TERMINAL->"session.local > ready_";Themes.CORPORATE->"Your time. Company standard.";else->"Seu mundo. Outra frequência."},18f));battery=mono(if(batteryLevel>=0)"BATERIA  $batteryLevel%" else "BATERIA  —",10f,muted);badge.addView(battery);add(badge)
        if(!isDefaultHome()) {add(label("Modo recomendado · HyperOS preservado",12f,muted));add(button("PERSONALIZAR O POCO LAUNCHER") {nativeSetup()})}
        val atlas=button("ATLAS") {startActivity(Intent(this,AtlasEntryActivity::class.java))}
        atlas.setOnLongClickListener {openAtlas();true};Themes.tag(atlas,"atlas-home",theme);add(atlas)
        add(mono("QUICK ACCESS",11f))
        val fav=prefs.getStringSet("favorites",emptySet())?:emptySet()
        val chosen=apps.filter {it.key in fav}.ifEmpty {apps.filter {it.component.packageName!=packageName}.take(8)}
        appGrid(chosen,body)
        add(button("TODOS OS APLICATIVOS  ↗") {show("GRID")})
        shortcuts()
        widgetViews()
    }
    private fun updateClock() {val now=Date();clock?.text=SimpleDateFormat("HH:mm",Locale.getDefault()).format(now);date?.text=SimpleDateFormat("EEEE, dd MMMM",Locale("pt","BR")).format(now).uppercase(Locale("pt","BR"))}
    private fun loadApps() {
        if(io.isShutdown)return
        val generation=++sessionGeneration
        io.execute {val result=try{repo.load()}catch(_:Exception){emptyList()};handler.post {if(!isDestroyed && generation==sessionGeneration) {val changed=apps!=result;apps=result;if(changed) {if(page=="GRID")renderGrid() else if(page=="HOME")show("HOME")}}}}
    }
    private fun grid() {
        add(mono("NEON GRID"));add(label("Aplicativos",32f))
        val search=EditText(this).apply {hint=if(theme.mono)"grep aplicativos..." else "Pesquisar aplicativos";background=theme.background(this@MainActivity,true);Themes.tag(this,"search",theme);setText(query);setTextColor(textColor);setHintTextColor(muted);textSize=16f;setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_TEXT;contentDescription="Pesquisar aplicativos";setPadding(dp(12),dp(12),dp(12),dp(12))}
        add(search)
        val cats=Spinner(this);val names=arrayOf("Todos","Jogos","Produtividade","Social","Mídia","Outros")
        cats.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,names)
        cats.setSelection(when(category){0->1;7->2;4->3;1->4;-1->5;else->0})
        cats.onItemSelectedListener=object: AdapterView.OnItemSelectedListener {override fun onNothingSelected(p: AdapterView<*>?){};override fun onItemSelected(p: AdapterView<*>?,v: View?,pos: Int,id: Long) {category=intArrayOf(-2,0,7,4,1,-1)[pos];renderGrid()}}
        add(cats);gridBody=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL};add(gridBody!!)
        search.addTextChangedListener(object: TextWatcher {override fun beforeTextChanged(s: CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s: CharSequence?,start:Int,before:Int,count:Int){query=s.toString();renderGrid()};override fun afterTextChanged(s:Editable?) {}})
        renderGrid()
    }
    private fun renderGrid() {
        val container=gridBody?:return;container.removeAllViews()
        val filtered=apps.filter {Policy.matches(it.label,query) && (category==-2 || if(category==1)it.category in 1..3 else it.category==category)}
        container.addView(mono("${filtered.size} ATIVIDADES  /  ${if(category==-1)"SEM CATEGORIA" else "PERFIS VISÍVEIS"}",9f,muted))
        appGrid(filtered,container)
        if(filtered.isEmpty())container.addView(label("Nenhum aplicativo encontrado.",14f,muted))
    }
    private fun appGrid(entries: List<AppEntry>,parent: LinearLayout) {
        val columns=if(resources.configuration.screenWidthDp>=600)6 else 4
        entries.chunked(columns).forEach { rowEntries ->
            val row=LinearLayout(this)
            for(app in rowEntries) {
                val tile=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(3),dp(8),dp(3),dp(8));isFocusable=true;contentDescription="Abrir ${app.label}"}
                val frame=FrameLayout(this).apply {background=theme.background(this@MainActivity,true);Themes.tag(this,"icon-frame",theme)}
                val icon=ImageView(this).apply {setPadding(dp(11),dp(11),dp(11),dp(11));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
                frame.addView(icon,FrameLayout.LayoutParams(-1,-1));tile.addView(frame,LinearLayout.LayoutParams(dp(58),dp(58)))
                val title=label(app.label,12f).apply {gravity=Gravity.CENTER;maxLines=2;ellipsize=TextUtils.TruncateAt.END;minHeight=dp(35)};tile.addView(title)
                tile.setOnClickListener {try {access.stop();repo.open(app)}catch(_:Exception){toast("Aplicativo indisponível ou perfil pausado.");loadApps()}}
                tile.setOnLongClickListener {appMenu(app);true}
                val key=prefs.getString("iconPack","")+app.key
                val cached=iconCache.get(key)
                if(cached!=null){icon.setImageDrawable(cached.constantState?.newDrawable(resources)?.mutate()?:cached);Themes.icon(icon,theme);Themes.tag(icon,"icon",theme)} else if(!io.isShutdown)io.execute {
                    val drawable=packs.icon(prefs.getString("iconPack","")?:"",app)?:repo.icon(app)
                    handler.post {if(!isDestroyed && drawable!=null){iconCache.put(key,drawable);icon.setImageDrawable(drawable.constantState?.newDrawable(resources)?.mutate()?:drawable);Themes.icon(icon,theme);Themes.tag(icon,"icon",theme)}}
                }
                row.addView(tile,LinearLayout.LayoutParams(0,-2,1f))
            }
            repeat(columns-rowEntries.size){row.addView(View(this),LinearLayout.LayoutParams(0,1,1f))};parent.addView(row)
        }
    }
    private fun appMenu(app: AppEntry) {
        val favorites=(prefs.getStringSet("favorites",emptySet())?:emptySet()).toMutableSet()
        AlertDialog.Builder(this).setTitle(app.label).setItems(arrayOf(if(app.key in favorites)"Remover dos favoritos" else "Adicionar aos favoritos","Informações do aplicativo")) {_,i ->
            if(i==0){if(!favorites.add(app.key))favorites.remove(app.key);prefs.edit().putStringSet("favorites",favorites).apply();if(page=="HOME")show("HOME")}else try{repo.info(app)}catch(_:Exception){toast("Informações indisponíveis.")}
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
        add(button("CONFIGURAR ATLAS") {openAtlas()})
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
            val b=label(name,10f,color).apply {gravity=Gravity.CENTER;setPadding(0,dp(18),0,dp(18));isFocusable=true;setOnClickListener {prefs.edit().putInt("accent",color).apply();art.accent=color;art.invalidate();iconCache.evictAll();show("CONFIG")}};palette.addView(b,LinearLayout.LayoutParams(0,-2,1f))
        };add(palette)
        add(button("WALLPAPER EXCLUSIVO  /  APLICAR") {wallpaperDialog()})
        add(button("EXPORTAR WALLPAPER PNG") {safeStartForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("image/png").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"NEON-CORE-1220x2712.png"),201)})
        add(button("PACOTE DE ÍCONES INSTALADO") {
            val options=listOf("" to "Ícones originais com moldura NEON")+packs.packs()
            AlertDialog.Builder(this).setTitle("Pacote compatível com appfilter.xml").setItems(options.map{it.second}.toTypedArray()){_,i->prefs.edit().putString("iconPack",options[i].first).apply();iconCache.evictAll();toast("Pacote selecionado para a Home NEON.")}.show()
        })
        add(label("Pacotes ADW/appfilter e ícones originais. Ícones do perfil de trabalho mantêm o distintivo do Android. Máscaras e formatos proprietários de temas não são importados.",12f,muted))
        add(button("ADICIONAR WIDGET À HOME NEON") {pickWidget()})
        add(button("ADICIONAR RELÓGIO AO LAUNCHER ATUAL") {
            val component=ComponentName(this,NeonWidget::class.java)
            if(widgetManager.isRequestPinAppWidgetSupported)widgetManager.requestPinAppWidget(component,null,null)else toast("No launcher original, mantenha a tela pressionada e abra Widgets → NEON OS.")
        })
        add(button("TEMAS DO HYPEROS") {val launch=packageManager.getLaunchIntentForPackage("com.android.thememanager");if(launch!=null)safeStart(launch)else safeStart(Intent(Settings.ACTION_SETTINGS))})
        add(label("NEON OS 0.2.0 · sem anúncios, câmera, acessibilidade ou serviço permanente. Rede apenas para funções solicitadas do ATLAS; microfone por acionamento manual.\nNão modifica apps, SystemUI, AOD ou o mecanismo de bloqueio.",12f,muted))
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
        io.execute {var ok=false;try {val image=NeonArt.wallpaper(1220,2712,accent,theme.id);try{WallpaperManager.getInstance(this).setBitmap(image,null,true,flags);ok=true}finally{image.recycle()}}catch(_:Exception){};handler.post {if(!isDestroyed)toast(if(ok)"Wallpaper aplicado." else "Aplicação bloqueada pelo sistema. Exporte o PNG e aplique pelo app Temas.")}}
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
