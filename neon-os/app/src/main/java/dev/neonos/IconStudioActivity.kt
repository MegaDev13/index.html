package dev.neonos

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.File

class IconStudioActivity : NeonPanel() {
    private val store by lazy { IconStore(this) }
    private val repository by lazy { AppRepository(this) }
    private var apps = emptyList<AppEntry>()
    private val selected = mutableSetOf<String>()
    private var imageTarget: String? = null
    private lateinit var inventory: ListView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        imageTarget = state?.getString("iconTarget")
        screen("NEON ICON STUDIO")
        add(text("Selecione apps para o inventário ZIP. Toque no nome de um app para personalizar seu ícone.", 13f))
        add(button("EXPORTAR INVENTÁRIO") {
            if (selected.size !in 1..250) message("Selecione entre 1 e 250 apps.")
            else startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip")
                .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "NEON_ICON_EXPORT.zip"), 401)
        })
        add(button("IMPORTAR PACOTE ZIP") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE), 402)
        })
        add(button("RESTAURAR CONJUNTO ANTERIOR") {
            worker.execute {
                val restored = runCatching { store.restorePrevious() }.getOrDefault(false)
                whenAlive { message(if (restored) "Conjunto anterior restaurado." else "Sem conjunto anterior disponível."); showInventory() }
            }
        })
        add(button("FAMÍLIA / FORMATO DOS ÍCONES") { choices() })
        add(button("SELECIONAR TODOS / LIMPAR") {
            if (selected.isEmpty()) selected.addAll(apps.take(250).map { it.key }) else selected.clear()
            showInventory()
        })
        inventory = ListView(this)
        body.addView(inventory, LinearLayout.LayoutParams(-1, dp(430)))
        inventory.setOnTouchListener { view, event ->
            view.parent.requestDisallowInterceptTouchEvent(event.actionMasked !in setOf(1, 3)); false
        }
        worker.execute {
            val entries = runCatching { repository.load() }.getOrDefault(emptyList())
            whenAlive { apps = entries; showInventory() }
        }
    }

    override fun onSaveInstanceState(state: Bundle) {
        state.putString("iconTarget", imageTarget)
        super.onSaveInstanceState(state)
    }

    private fun choices() {
        AlertDialog.Builder(this).setItems(arrayOf("Desenhos NEON", "Originais", "Pacote instalado em CONFIG", "Formato dos desenhos")) { _, index ->
            if (index < 3) {
                store.changeMode(listOf("neon", "original", "pack")[index])
                showInventory()
            } else AlertDialog.Builder(this).setItems(arrayOf("Padrão do tema", "Circular", "Hexagonal", "Retangular")) { _, shape ->
                preferences.edit().putString("icon_shape", listOf("theme", "circle", "hex", "square")[shape])
                    .putLong("icon_revision", store.revision + 1).apply()
                showInventory()
            }.show()
        }.show()
    }

    private fun showInventory() {
        inventory.adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(position: Int) = apps[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, recycled: View?, parent: ViewGroup?): View {
                val row = (recycled as? LinearLayout) ?: LinearLayout(this@IconStudioActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(CheckBox(this@IconStudioActivity))
                    addView(ImageView(this@IconStudioActivity), LinearLayout.LayoutParams(dp(50), dp(50)))
                    addView(text("", 13f), LinearLayout.LayoutParams(0, -2, 1f))
                }
                val app = getItem(position)
                row.tag = app.key
                val checkbox = row.getChildAt(0) as CheckBox
                checkbox.setOnCheckedChangeListener(null)
                checkbox.contentDescription = "Selecionar ${app.label}"
                checkbox.isChecked = app.key in selected
                checkbox.setOnCheckedChangeListener { _, on -> if (on) selected.add(app.key) else selected.remove(app.key) }
                val image = row.getChildAt(1) as ImageView
                image.setImageDrawable(null)
                val title = row.getChildAt(2) as TextView
                title.text = "${app.label}\n${app.component.packageName}\n${store.state(app, style.id)}"
                title.setOnClickListener { edit(app) }
                worker.execute {
                    val icon = store.drawable(app, style) ?: repository.icon(app)
                    whenAlive { if (row.tag == app.key) image.setImageDrawable(icon) }
                }
                return row
            }
        }
    }

    private fun edit(app: AppEntry) {
        AlertDialog.Builder(this).setTitle(app.label).setItems(arrayOf("Importar imagem", "Escolher desenho alternativo", "Restaurar original", "Usar seleção automática")) { _, index ->
            when (index) {
                0 -> {
                    imageTarget = app.key
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), 403)
                }
                1 -> AlertDialog.Builder(this).setTitle("Desenho alternativo").setItems(NeonIcons.glyphs.toTypedArray()) { _, glyph ->
                    save(app, "glyph:" + NeonIcons.glyphs[glyph])
                }.show()
                2 -> save(app, "original")
                else -> save(app, null)
            }
        }.show()
    }

    private fun save(app: AppEntry, choice: String?) {
        val theme = style.id
        worker.execute {
            val outcome = runCatching { store.choose(app, theme, choice) }
            whenAlive { outcome.exceptionOrNull()?.let { message(it.message ?: "Erro ao salvar.") }; showInventory() }
        }
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        val uri = data?.data ?: return
        if (result != RESULT_OK) return
        val entries = apps.filter { it.key in selected }
        val target = imageTarget
        val theme = style.id
        worker.execute {
            try {
                when (request) {
                    401 -> {
                        val count = contentResolver.openOutputStream(uri)?.use { store.export(it, entries) } ?: error("Destino indisponível.")
                        whenAlive { message("Inventário exportado com $count apps.") }
                    }
                    402 -> {
                        val draft = contentResolver.openInputStream(uri)?.use { store.stage(it) } ?: error("Arquivo indisponível.")
                        whenAlive { preview(draft) }
                    }
                    403 -> {
                        val app = repository.load().firstOrNull { it.key == target } ?: error("Aplicativo indisponível.")
                        val path = contentResolver.openInputStream(uri)?.use { store.importImage(it) } ?: error("Imagem indisponível.")
                        store.choose(app, theme, path)
                        whenAlive { showInventory() }
                    }
                }
            } catch (error: Exception) { whenAlive { message(error.message ?: "Não foi possível concluir.") } }
        }
    }

    fun preview(draft: IconPackDraft) {
        val chosen = draft.entries.indices.toMutableSet()
        val list = ListView(this).apply { minimumHeight = dp(280) }
        list.adapter = object : BaseAdapter() {
            override fun getCount() = draft.entries.size
            override fun getItem(position: Int) = draft.entries[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, recycled: View?, parent: ViewGroup?): View {
                val row = (recycled as? LinearLayout) ?: LinearLayout(this@IconStudioActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(ImageView(this@IconStudioActivity), LinearLayout.LayoutParams(dp(52), dp(52)))
                    addView(CheckBox(this@IconStudioActivity), LinearLayout.LayoutParams(0, -2, 1f))
                }
                val item = getItem(position)
                row.tag = position
                val image = row.getChildAt(0) as ImageView
                val checkbox = row.getChildAt(1) as CheckBox
                image.setImageDrawable(null)
                checkbox.setOnCheckedChangeListener(null)
                checkbox.text = "${item.label}\n${item.theme}"
                checkbox.isChecked = position in chosen
                checkbox.setOnCheckedChangeListener { _, on -> if (on) chosen.add(position) else chosen.remove(position) }
                worker.execute {
                    val bitmap = BitmapFactory.decodeFile(File(draft.directory, item.file).path)
                    whenAlive { if (row.tag == position) image.setImageBitmap(bitmap) else bitmap?.recycle() }
                }
                return row
            }
        }
        val dialog = AlertDialog.Builder(this).setTitle("Prévia do pacote de ícones").setView(list)
            .setPositiveButton("Aplicar selecionados", null)
            .setNegativeButton("Cancelar") { _, _ -> worker.execute { draft.directory.deleteRecursively() } }
            .setOnCancelListener { worker.execute { draft.directory.deleteRecursively() } }.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val entries = chosen.map { draft.entries[it] }
                val installed = apps.toList()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                dialog.setCancelable(false)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                worker.execute {
                    val result = runCatching { store.apply(draft, entries, installed) }
                    whenAlive {
                        result.onSuccess { count -> dialog.dismiss(); message("$count vínculos ativos. O pacote também vale para futuros apps mapeados."); showInventory() }
                            .onFailure {
                                message(it.message ?: "Pacote não aplicado.")
                                dialog.setCancelable(true)
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                            }
                    }
                }
            }
        }
        dialog.show()
    }
}
