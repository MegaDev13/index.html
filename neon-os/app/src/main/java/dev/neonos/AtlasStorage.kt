package dev.neonos

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** App-private encrypted tokens/history; excluded from backup by the application manifest. */
class AtlasStorage(private val context:Context) {
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey("atlas-local-v1",null) as? SecretKey)?.let{return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {init(KeyGenParameterSpec.Builder("atlas-local-v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()
    }
    @Synchronized fun read():JSONObject = try {
        val bytes=context.openFileInput("atlas.secure").use{it.readBytes()}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8))
    }catch(_:Exception){JSONObject()}
    @Synchronized fun write(value:JSONObject) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val bytes=cipher.iv+cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8))
        val file=android.util.AtomicFile(java.io.File(context.filesDir,"atlas.secure"))
        val stream=file.startWrite();try{stream.write(bytes);file.finishWrite(stream)}catch(e:Exception){file.failWrite(stream);throw e}
    }
    fun token():String=read().optString("token","")
    fun setToken(token:String){val state=read();state.put("token",token);write(state)}
    fun history():JSONArray {
        val state=read();val days=context.getSharedPreferences("neon",0).getInt("atlas_retention",7)
        val old=state.optJSONArray("history")?:JSONArray();val result=JSONArray()
        if(days>0)for(i in 0 until old.length()){val item=old.getJSONObject(i);if(System.currentTimeMillis()-item.optLong("time")<days*86400000L)result.put(item)}
        if(result.length()!=old.length()){state.put("history",result);write(state)}
        return result
    }
    fun setHistory(history:JSONArray){val state=read();state.put("history",history);write(state)}
    fun clearHistory(){val state=read();state.remove("history");write(state)}
}
