package io.github.xtratter.appshelf

import android.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Списки на сервере WebDAV: найти по телефонам, выбрать телефон и файл, открыть в режиме восстановления. */
object ServerLists {
    /** Списки одного телефона: [folder] — его папка на сервере («» — файлы прямо в основной папке). */
    internal class Group(val folder: String, val files: List<WebDav.Entry>)

    private fun isList(e: WebDav.Entry) = !e.dir && e.name != "links.json" && e.name != SettingsIO.DAV_FILE && (e.name.endsWith(".json", true) || e.name.endsWith(".csv", true))

    private fun newestFirst(files: List<WebDav.Entry>) =
        files.sortedWith(compareByDescending<WebDav.Entry> { it.modified }.thenByDescending { it.name })

    /** Списки на сервере по телефонам (папкам), самые свежие сверху; файлы из основной папки (старые версии AppShelf) — отдельно. */
    internal fun load(p: Prefs): List<Group> {
        val dav = Sync.dav(p)
        val root = dav.list()
        val groups = root.filter { it.dir }.map { d -> Group(d.name, newestFirst(dav.list(d.name).filter(::isList))) }
            .filter { it.files.isNotEmpty() }
            .sortedByDescending { it.files.first().modified }
        val loose = newestFirst(root.filter(::isList))
        return if (loose.isEmpty()) groups else groups + Group("", loose)
    }

    /** Выбор телефона; если он один — сразу его списки. */
    internal fun pickGroup(a: MainActivity, p: Prefs, groups: List<Group>, fmt: SimpleDateFormat, opened: () -> Unit) {
        if (groups.size == 1) return pickFile(a, p, groups[0], fmt, opened)
        val items = groups.map { g ->
            val newest = g.files.first().modified.takeIf { it > 0 }?.let { a.getString(R.string.dav_newest, fmt.format(Date(it))) }
            listOfNotNull(g.folder.ifEmpty { a.getString(R.string.dav_root_files) },
                a.resources.getQuantityString(R.plurals.versions, g.files.size, g.files.size), newest).joinToString(" · ")
        }.toTypedArray()
        AlertDialog.Builder(a)
            .setTitle(R.string.dav_pick_device)
            .setItems(items) { _, i -> pickFile(a, p, groups[i], fmt, opened) }
            .setNegativeButton(android.R.string.cancel, null)
            .show().also { Ui.glassDialog(it) }
    }

    /** Открыть список с сервера (из меню «Открыть сохранённый список»). */
    fun open(a: MainActivity) {
        val p = Prefs(a)
        android.widget.Toast.makeText(a, R.string.dav_connecting, android.widget.Toast.LENGTH_SHORT).show()
        Thread {
            val r = try { load(p) } catch (e: Exception) { e }
            a.ui {
                when {
                    r is Exception -> android.widget.Toast.makeText(a, Sync.error(a, r), android.widget.Toast.LENGTH_LONG).show()
                    (r as List<*>).isEmpty() -> android.widget.Toast.makeText(a, R.string.dav_no_files, android.widget.Toast.LENGTH_LONG).show()
                    else -> @Suppress("UNCHECKED_CAST") pickGroup(a, p, r as List<Group>,
                        SimpleDateFormat("EEE, d MMM, HH:mm", Locale.getDefault())) {}
                }
            }
        }.start()
    }

    /** Выбор списка с сервера — самые свежие сверху; выбранный открывается в режиме восстановления. */
    private fun pickFile(a: MainActivity, p: Prefs, g: Group, fmt: SimpleDateFormat, opened: () -> Unit) {
        val files = g.files
        val items = files.map { f ->
            listOfNotNull(f.name, f.modified.takeIf { it > 0 }?.let { fmt.format(Date(it)) },
                f.size.takeIf { it > 0 }?.let { android.text.format.Formatter.formatShortFileSize(a, it) }).joinToString(" · ")
        }.toTypedArray()
        AlertDialog.Builder(a)
            .setTitle(g.folder.ifEmpty { a.getString(R.string.dav_pick) })
            .setItems(items) { _, i ->
                Thread {
                    val snap = try { ListFile.read(Sync.dav(p).get(if (g.folder.isEmpty()) files[i].name else g.folder + "/" + files[i].name).toString(Charsets.UTF_8)) } catch (e: Exception) { e }
                    a.ui {
                        if (snap is Snapshot && snap.apps.isNotEmpty()) { opened(); a.showSnapshot(snap) }
                        else android.widget.Toast.makeText(a, if (snap is Exception && snap !is IllegalArgumentException &&
                                snap !is org.json.JSONException) Sync.error(a, snap) else a.getString(R.string.open_failed),
                            android.widget.Toast.LENGTH_LONG).show()
                    }
                }.start()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show().also { Ui.glassDialog(it) }
    }
}
