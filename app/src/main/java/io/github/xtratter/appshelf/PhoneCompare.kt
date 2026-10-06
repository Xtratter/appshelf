package io.github.xtratter.appshelf

import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Сравнение этого телефона со списком другого: что только здесь, что только там и где версии разные. */
object PhoneCompare {
    /** Скачать список [path] с сервера (в фоне) и показать сравнение под именем [phone]. */
    fun fromServer(a: MainActivity, phone: String, path: String) {
        val p = Prefs(a)
        Thread {
            val r = runCatching { ListFile.read(Sync.dav(p).get(path).toString(Charsets.UTF_8)) }
            a.ui {
                val other = r.getOrElse { e -> Toast.makeText(a, Sync.error(a, e as? Exception ?: Exception(e)), Toast.LENGTH_LONG).show(); return@ui }
                show(a, phone, other)
            }
        }.start()
    }

    fun show(a: MainActivity, phone: String, other: Snapshot) {
        val here = a.installedApps().filter { !it.system }
        val there = other.apps.filter { !it.system }
        val (onlyHere, onlyThere) = History.compare(here, there)
        val differ = History.differ(here, there)
        val kit = DialogKit(a)
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(kit.px(22f), kit.px(20f), kit.px(22f), 0)
        }
        fun section(title: String, lines: List<String>) {
            box.addView(TextView(a).apply {
                text = title; textSize = 15f; typeface = Ui.medium; setTextColor(Ui.primary)
                setPadding(0, kit.px(12f), 0, kit.px(6f))
            })
            box.addView(TextView(a).apply {
                text = if (lines.isEmpty()) "—" else lines.joinToString("\n") { "• $it" }
                textSize = 14.5f; setTextColor(Ui.TEXT); setLineSpacing(0f, 1.2f)
            })
        }
        box.addView(TextView(a).apply {
            text = a.getString(R.string.hi_compare_title, phone); textSize = 20f; typeface = Ui.medium; setTextColor(Ui.TEXT)
        })
        section(a.getString(R.string.hi_only_here, onlyHere.size), onlyHere.map { it.label })
        section(a.getString(R.string.hi_only_there, phone, onlyThere.size), onlyThere.map { it.label })
        // «версии отличаются»: у кого новее — видно по стрелке
        section(a.getString(R.string.hi_differ, differ.size), differ.map { (x, y) ->
            val tail = when (Updates.newer(y.versionName, x.versionName)) {
                true -> " " + a.getString(R.string.hi_newer_there)
                false -> " " + a.getString(R.string.hi_newer_here)
                null -> ""
            }
            a.getString(R.string.hi_differ_row, x.label, Ui.versionShort(x.versionName), Ui.versionShort(y.versionName)) + tail
        })
        AlertDialog.Builder(a).setView(ScrollView(a).apply { addView(box) })
            .setPositiveButton(R.string.hi_open_restore) { _, _ -> a.showSnapshot(other) }
            .setNegativeButton(R.string.close, null)
            .show().also { Ui.glassDialog(it) }
    }
}
