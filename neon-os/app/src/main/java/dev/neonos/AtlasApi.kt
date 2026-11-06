package dev.neonos

import android.content.Context
import android.net.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AtlasApi(private val context:Context) {
    @Volatile private var active:HttpURLConnection?=null
    fun connected():Boolean {val m=context.getSystemService(ConnectivityManager::class.java);val c=m.getNetworkCapabilities(m.activeNetwork)?:return false;return c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}
    fun cancel(){active?.disconnect();active=null}
    fun endpoint():String=context.getSharedPreferences("neon",0).getString("atlas_url","")?.trim()?.trimEnd('/')?:""
    fun validEndpoint(value:String):Boolean = try {
        val url=URL(value)
        (url.protocol=="https" || BuildConfig.DEBUG && url.protocol=="http" && url.host=="10.0.2.2") && url.userInfo==null && url.query==null && url.ref==null && url.path in listOf("","/") && url.host.isNotBlank()
    }catch(_:Exception){false}
    fun request(path:String,body:JSONObject,token:String=""):JSONObject {
        val endpoint=endpoint()
        if(!validEndpoint(endpoint))throw IllegalStateException("ATLAS integrado não configurado. Configure um backend HTTPS e faça login.")
        val c=URL(endpoint+path).openConnection() as HttpURLConnection
        active=c;c.connectTimeout=6000;c.readTimeout=25000;c.instanceFollowRedirects=false;c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json")
        if(token.isNotBlank())c.setRequestProperty("Authorization","Bearer $token")
        try {
            c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
            val code=c.responseCode
            val stream=if(code in 200..299)c.inputStream else c.errorStream
            val text=stream?.use{String(it.readNBytes(65537),Charsets.UTF_8)}?:"{}"
            if(text.length>65536)throw IllegalStateException("Resposta do backend excede o limite.")
            val json=try{JSONObject(text)}catch(_:Exception){JSONObject()}
            if(code !in 200..299)throw IllegalStateException(json.optString("error",when(code){401->"Sessão expirada. Faça login novamente.";429->"Limite de utilização atingido.";else->"Backend indisponível (HTTP $code)."}))
            return json
        } finally {c.disconnect();if(active===c)active=null}
    }
}
