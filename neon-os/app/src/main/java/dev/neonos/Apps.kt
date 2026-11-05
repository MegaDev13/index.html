package dev.neonos

import android.app.*
import android.appwidget.*
import android.content.*
import android.content.pm.*
import android.graphics.drawable.Drawable
import android.os.*
import android.widget.*
import org.xmlpull.v1.XmlPullParser

/** Component + profile serial keeps work and personal copies distinct. */
data class AppEntry(val component: ComponentName, val user: UserHandle, val serial: Long, val label: String, val category: Int) {
    val key: String get() = "${serial}|${component.flattenToString()}"
}
class AppRepository(private val context: Context) {
    private val launcher=context.getSystemService(LauncherApps::class.java)
    private val users=context.getSystemService(UserManager::class.java)
    fun load(): List<AppEntry> = launcher.profiles.flatMap { user ->
        try { launcher.getActivityList(null,user).map { a ->
            AppEntry(a.componentName,user,users.getSerialNumberForUser(user),a.label.toString(),a.applicationInfo.category)
        } } catch (_: SecurityException) { emptyList() }
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    fun open(app: AppEntry) = launcher.startMainActivity(app.component,app.user,null,null)
    fun info(app: AppEntry) = launcher.startAppDetailsActivity(app.component,app.user,null,null)
    fun icon(app: AppEntry): Drawable? = try { launcher.getActivityList(app.component.packageName,app.user).firstOrNull { it.componentName==app.component }?.getBadgedIcon(0) } catch (_: Exception) { null }
}
class IconPack(private val context: Context) {
    private var loaded="";private val map=mutableMapOf<String,String>();private var packRes: android.content.res.Resources?=null
    fun packs(): List<Pair<String,String>> = context.packageManager.queryIntentActivities(Intent("org.adw.launcher.THEMES"),0).map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }.distinctBy { it.first }
    fun icon(packageName: String, app: AppEntry): Drawable? {
        if(packageName.isBlank() || app.user!=android.os.Process.myUserHandle())return null
        if(loaded!=packageName) {
            loaded=packageName;map.clear();packRes=null
            try {
                val pc=context.createPackageContext(packageName,0);val r=pc.resources;packRes=r
                val id=r.getIdentifier("appfilter","xml",packageName)
                val parser=if(id!=0)r.getXml(id) else android.util.Xml.newPullParser().apply { setInput(pc.assets.open("appfilter.xml"),"UTF-8") }
                var count=0
                while(parser.eventType!=XmlPullParser.END_DOCUMENT && count++<100000) {
                    if(parser.eventType==XmlPullParser.START_TAG && parser.name=="item") {
                        val component=parser.getAttributeValue(null,"component");val drawable=parser.getAttributeValue(null,"drawable")
                        if(component!=null && drawable!=null)map[component]=drawable
                    };parser.next()
                }
            } catch (_: Exception) { map.clear() }
        }
        val name=map["ComponentInfo{${app.component.flattenToString()}}"]?:map["ComponentInfo{${app.component.flattenToShortString()}}"]?:return null
        return try { val r=packRes?:return null;val id=r.getIdentifier(name,"drawable",packageName);if(id==0)null else r.getDrawable(id,null) } catch (_: Exception) { null }
    }
}
class NeonWidget: AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for(id in ids) {
            val views=RemoteViews(context.packageName,R.layout.neon_widget)
            views.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            manager.updateAppWidget(id,views)
        }
    }
}
class PinActivity: Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val request=getSystemService(LauncherApps::class.java).getPinItemRequest(intent)
        val shortcut=request?.shortcutInfo
        if(request==null || !request.isValid || shortcut==null) {finish();return}
        AlertDialog.Builder(this).setTitle("Fixar atalho no NEON?").setMessage(shortcut.shortLabel)
            .setPositiveButton("Fixar") { _,_ ->
                try {
                    if(request.accept()) {
                        val prefs=getSharedPreferences("neon",0)
                        val arr=org.json.JSONArray(prefs.getString("shortcuts","[]"))
                        val obj=org.json.JSONObject().put("package",shortcut.`package`).put("id",shortcut.id).put("label",shortcut.shortLabel).put("serial",getSystemService(UserManager::class.java).getSerialNumberForUser(shortcut.userHandle))
                        arr.put(obj);prefs.edit().putString("shortcuts",arr.toString()).apply()
                    }
                } catch (_: Exception) { Toast.makeText(this,"Atalho não disponível neste perfil.",Toast.LENGTH_LONG).show() };finish()
            }.setNegativeButton("Cancelar") { _,_->finish() }.setOnCancelListener { finish() }.show()
    }
}
