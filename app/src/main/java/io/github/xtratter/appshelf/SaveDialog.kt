package io.github.xtratter.appshelf

import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «Сохранение и восстановление»: сверху — что защищено и когда, затем две большие кнопки «Сохранить» и
 * «Восстановить» (каждая — свой выбор), ниже — сворачиваемые «Автоматика» и «Что сохранять».
 */
object SaveDialog {
    fun show(a: MainActivity) {
        val (included, total) = a.includedCount() ?: return
        val p = Prefs(a)
        val dp = a.resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()
        val fmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
        fun whenText(ms: Long) = if (ms > 0) fmt.format(Date(ms)) else ""

        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20f), px(22f), px(20f), px(8f))
        }
        box.addView(TextView(a).apply {
            setText(R.string.save_restore); textSize = 20f; typeface = Ui.medium; setTextColor(Ui.TEXT)
            setPadding(px(4f), 0, 0, px(12f))
        })
        val dialog = AlertDialog.Builder(a)
            .setView(ScrollView(a).apply { addView(box) })
            .setNegativeButton(R.string.close, null)
            .create()

        // ---------- что защищено ----------
        val status = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = GlassDrawable(a, 22f)
            setPadding(px(16f), px(12f), px(16f), px(12f))
        }
        fun statusLine(ok: Boolean, text: String) = status.addView(TextView(a).apply {
            this.text = (if (ok) "✓  " else "○  ") + text
            textSize = 14f; setLineSpacing(0f, 1.1f)
            setTextColor(if (ok) Ui.TEXT else Ui.TEXT3)
            setPadding(0, px(3f), 0, px(3f))
        })
        statusLine(p.lastSaved > 0, if (p.lastSaved > 0) a.getString(R.string.sv_list_ok, whenText(p.lastSaved), p.lastSavedName)
            else a.getString(R.string.sv_list_none))
        val apkOn = ApkBackup.dest(p) != null
        statusLine(p.apkLast > 0, when {
            p.apkLast > 0 -> a.getString(R.string.sv_apk_ok, p.apkLastCount, whenText(p.apkLast))
            apkOn -> a.getString(R.string.sv_apk_never)
            else -> a.getString(R.string.sv_apk_off)
        })
        statusLine(p.settingsSaved > 0, if (p.settingsSaved > 0) a.getString(R.string.sv_settings_ok, whenText(p.settingsSaved))
            else a.getString(R.string.sv_settings_none))
        a.syncLine()?.takeIf { it.second }?.let { (line, _) ->
            status.addView(TextView(a).apply { text = line; textSize = 13f; setTextColor(Ui.WARN); setPadding(0, px(4f), 0, 0) })
        }
        Help.attach(status, R.string.h_status_t, R.string.h_status)
        box.addView(status)

        // ---------- две большие кнопки ----------
        fun big(text: Int, filled: Boolean, action: () -> Unit) = TextView(a).apply {
            setText(text)
            Help.attach(this, text, if (filled) R.string.h_save else R.string.h_restore); textSize = 16f; typeface = Ui.medium; gravity = Gravity.CENTER
            setTextColor(if (filled) Ui.ON_ACCENT else Ui.primary)
            background = if (filled) Ui.pill(a, Ui.primary) else Ui.pill(a, Ui.withAlpha(Ui.primary, 0.14f), Ui.withAlpha(Ui.primary, 0.35f))
            foreground = Ui.ripple(a, 100f)
            setOnClickListener { dialog.dismiss(); action() }
            Haptics.onClick(this)
        }
        val row = LinearLayout(a).apply { isBaselineAligned = false; setPadding(0, px(14f), 0, px(6f)) }
        row.addView(big(R.string.sv_save, true) { saveMenu(a) }, LinearLayout.LayoutParams(0, px(Ui.buttonDp), 1f))
        row.addView(big(R.string.sv_restore, false) { restoreMenu(a) },
            LinearLayout.LayoutParams(0, px(Ui.buttonDp), 1f).apply { leftMargin = px(10f) })
        box.addView(row)

        // ---------- сворачиваемые блоки ----------
        fun item(parent: LinearLayout, title: Int, sub: String, on: Boolean = false, warn: Boolean = false, action: () -> Unit) {
            parent.addView(LinearLayout(a).apply {
                Help.attach(this, title, when (title) {
                    R.string.dav_title -> R.string.h_dav
                    R.string.autosave -> R.string.h_autosave
                    R.string.bk_title -> R.string.h_apk
                    else -> R.string.h_apps_in_list
                })
                orientation = LinearLayout.VERTICAL
                background = GlassDrawable(a, 18f)
                foreground = Ui.ripple(a, 18f)
                setPadding(px(16f), px(12f), px(16f), px(12f))
                addView(TextView(a).apply {
                    text = a.getString(title) + if (on) "  ✓" else ""
                    textSize = 16f; typeface = Ui.medium; setTextColor(Ui.TEXT)
                })
                addView(TextView(a).apply {
                    text = sub; textSize = 13f; setLineSpacing(0f, 1.1f)
                    setTextColor(when { warn -> Ui.WARN; on -> Ui.primary; else -> Ui.TEXT2 })
                    setPadding(0, px(2f), 0, 0)
                })
                setOnClickListener { dialog.dismiss(); action() }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(8f) })
        }
        fun section(title: Int, open: Boolean, fill: (LinearLayout) -> Unit) {
            val body = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; visibility = if (open) View.VISIBLE else View.GONE }
            val head = TextView(a).apply {
                textSize = 14f; typeface = Ui.medium; setTextColor(Ui.primary)
                setPadding(px(4f), px(14f), 0, px(8f))
                fun label() { text = (if (body.visibility == View.VISIBLE) "▾  " else "▸  ") + a.getString(title) }
                label()
                setOnClickListener {
                    body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    label(); Haptics.play(Haptics.Kind.TICK)
                }
            }
            box.addView(head); box.addView(body)
            fill(body)
        }
        section(R.string.sv_auto, false) { b ->
            val sync = a.syncLine()
            item(b, R.string.dav_title, when {
                sync != null -> sync.first.removePrefix("WebDAV: ")
                p.davUrl.isNotEmpty() -> a.getString(R.string.dav_sub_manual)
                else -> a.getString(R.string.dav_sub_off)
            }, on = sync != null && !sync.second, warn = sync?.second == true) { SyncDialog.show(a) }
            val auto = a.autosaveName()
            item(b, R.string.autosave, auto?.let { a.getString(R.string.autosave_sub_on, it) } ?: a.getString(R.string.autosave_sub_off),
                on = auto != null) { a.autosaveDialog() }
            item(b, R.string.bk_title, when (ApkBackup.dest(p)) {
                ApkBackup.Dest.WEBDAV -> a.getString(R.string.bk_sub_webdav)
                ApkBackup.Dest.FOLDER -> a.getString(R.string.bk_sub_folder)
                null -> a.getString(R.string.bk_sub_off)
            }, on = apkOn) { ApkBackupDialog.show(a) }
        }
        section(R.string.sv_what, false) { b ->
            item(b, R.string.select_title, a.getString(R.string.select_sub, included, total)) { a.selectApps { show(a) } }
        }

        dialog.show()
        Ui.glassDialog(dialog)
    }

    /** «Сохранить»: что и куда — разовые действия. */
    private fun saveMenu(a: MainActivity) {
        val p = Prefs(a)
        val items = ArrayList<Pair<String, () -> Unit>>()
        items += a.getString(R.string.sv_m_file) to { a.saveToFile() }
        items += a.getString(R.string.sv_m_share) to { a.share() }
        if (p.davUrl.isNotEmpty()) items += a.getString(R.string.sv_m_server) to {
            Toast.makeText(a, R.string.dav_connecting, Toast.LENGTH_SHORT).show()
            Thread {
                val r = Sync.run(a)
                a.runOnUiThread {
                    Haptics.play(if (r.ok) Haptics.Kind.SUCCESS else Haptics.Kind.ERROR)
                    Toast.makeText(a, if (r.ok) a.getString(R.string.sv_sent, r.message) else r.message, Toast.LENGTH_LONG).show()
                    a.refreshSummary()
                }
            }.start()
        }
        if (ApkBackup.dest(p) != null) items += a.getString(R.string.sv_m_apk) to { ApkBackupDialog.runNow(a) }
        items += a.getString(R.string.sv_m_settings) to { SettingsDialog.save(a) }
        val withLinks = LinkStore.mine(a).count { it.value.links.isNotEmpty() }
        if (withLinks > 0) items += a.getString(R.string.links_export) to { a.exportLinks() }
        menu(a, R.string.sv_save, items)
    }

    /** «Восстановить»: список или настройки — из файла или с сервера. */
    private fun restoreMenu(a: MainActivity) {
        val p = Prefs(a)
        val items = ArrayList<Pair<String, () -> Unit>>()
        items += a.getString(R.string.sv_r_list_file) to { a.openList() }
        if (p.davUrl.isNotEmpty()) items += a.getString(R.string.sv_r_list_server) to { SyncDialog.openFromServer(a) }
        items += a.getString(R.string.sv_r_settings) to { SettingsDialog.restore(a) }
        menu(a, R.string.sv_restore, items)
    }

    private fun menu(a: MainActivity, title: Int, items: List<Pair<String, () -> Unit>>) {
        AlertDialog.Builder(a).setTitle(title)
            .setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
            .setNegativeButton(android.R.string.cancel, null)
            .show().also { Ui.glassDialog(it) }
    }
}
