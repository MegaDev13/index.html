package dev.neonos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Layout keys include the Android profile and launchable component. */
class HomeLayout(context: Context) {
    private val prefs = context.getSharedPreferences("neon", 0)
    private fun read() = runCatching { JSONObject(prefs.getString("home_layout", "{}")!!) }.getOrElse { JSONObject() }
    val configured get() = prefs.contains("home_layout")
    fun page(index: Int) = page(read(), index)
    private fun page(model: JSONObject, index: Int): List<String> {
        val entries = model.optJSONArray("page$index")
        return if (entries != null) (0 until entries.length()).map { entries.getString(it) }
        else if (index == 0) prefs.getStringSet("favorites", emptySet()).orEmpty().sorted()
        else emptyList()
    }

    fun move(key: String, page: Int, before: String? = null) {
        require(page in 0..5)
        if (key == before) return
        val model = read()
        remove(model, setOf(key))
        val destination = page(model, page).toMutableList()
        val index = before?.let(destination::indexOf)?.takeIf { it >= 0 } ?: destination.size
        destination.add(index, key)
        model.put("page$page", JSONArray(destination))
        save(model)
    }

    fun unpin(key: String) {
        val model = read()
        for (page in 0..5) model.put("page$page", JSONArray(page(model, page).filter { it != key }))
        save(model)
    }

    fun createFolder(name: String, keys: List<String>, page: Int): String {
        val selected = keys.distinct()
        require(name.trim().length in 1..30 && selected.size >= 2 && page in 0..5)
        val model = read()
        remove(model, selected.toSet())
        val id = "folder:" + UUID.randomUUID()
        model.put(id, JSONObject().put("name", name.trim()).put("apps", JSONArray(selected)))
        model.put("page$page", JSONArray(page(model, page) + id))
        save(model)
        return id
    }

    fun folderName(id: String) = read().optJSONObject(id)?.optString("name", "Pasta") ?: "Pasta"
    fun folderApps(id: String): List<String> {
        val entries = read().optJSONObject(id)?.optJSONArray("apps") ?: return emptyList()
        return (0 until entries.length()).map { entries.getString(it) }
    }

    fun dissolve(id: String, page: Int) {
        val apps = folderApps(id)
        val model = read()
        remove(model, setOf(id))
        model.remove(id)
        model.put("page$page", JSONArray((page(model, page) + apps).distinct()))
        save(model)
    }

    private fun remove(model: JSONObject, keys: Set<String>) {
        for (page in 0..5) model.put("page$page", JSONArray(page(model, page).filterNot { it in keys }))
        for (id in model.keys().asSequence().filter { it.startsWith("folder:") }.toList()) {
            val folder = model.optJSONObject(id) ?: continue
            val apps = folder.optJSONArray("apps") ?: continue
            folder.put("apps", JSONArray((0 until apps.length()).map { apps.getString(it) }.filterNot { it in keys }))
        }
    }

    private fun save(model: JSONObject) {
        check(prefs.edit().putString("home_layout", model.toString()).commit()) { "Não foi possível salvar a organização." }
    }
}
