package io.github.xtratter.appshelf

import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * «Сохранение и восстановление» — единственная точка входа: все способы сохранить список
 * (файл, «Поделиться», автосохранение, WebDAV, выбор приложений, ссылки для каталога) и открыть сохранённый
 * (из файла или с сервера). У каждого пункта видно его состояние.
 */
object SaveDialog {
    fun show(a: MainActivity) {
        val (included, total) = a.includedCount() ?: return
        val p = Prefs(a)
        val dp = a.resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()

        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20f), px(22f), px(20f), px(8f))
        }
        box.addView(TextView(a).apply {
            setText(R.string.save_restore); textSize = 20f; typeface = Ui.medium; setTextColor(Ui.TEXT)
            setPadding(px(4f), 0, 0, px(4f))
        })
        fun header(text: Int) = box.addView(TextView(a).apply {
            setText(text); textSize = 14f; typeface = Ui.medium; setTextColor(Ui.primary)
            setPadding(px(4f), px(12f), 0, px(8f))
        })
        val dialog = AlertDialog.Builder(a)
            .setView(ScrollView(a).apply { addView(box) })
            .setNegativeButton(R.string.close, null)
            .create()

        /** Пункт: название и строка состояния под ним; [on] — функция включена (галочка и цвет). */
        fun item(title: Int, sub: String, on: Boolean = false, warn: Boolean = false, action: () -> Unit) {
            box.addView(LinearLayout(a).apply {
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

        header(R.string.sec_save)
        item(R.string.save_file, a.getString(R.string.save_file_sub)) { a.saveToFile() }
        item(R.string.share_list, a.getString(R.string.share_sub)) { a.share() }
        val auto = a.autosaveName()
        item(R.string.autosave, auto?.let { a.getString(R.string.autosave_sub_on, it) } ?: a.getString(R.string.autosave_sub_off),
            on = auto != null) { a.autosaveDialog() }
        val sync = a.syncLine()
        item(R.string.dav_title, when {
            sync != null -> sync.first.removePrefix("WebDAV: ")
            p.davUrl.isNotEmpty() -> a.getString(R.string.dav_sub_manual)
            else -> a.getString(R.string.dav_sub_off)
        }, on = sync != null && !sync.second, warn = sync?.second == true) { SyncDialog.show(a) }
        item(R.string.select_title, a.getString(R.string.select_sub, included, total)) { a.selectApps { show(a) } }
        val withLinks = LinkStore.mine(a).count { it.value.links.isNotEmpty() }
        item(R.string.links_export, a.resources.getQuantityString(R.plurals.links_export_sub, withLinks, withLinks)) { a.exportLinks() }

        header(R.string.sec_restore)
        item(R.string.open_from_file, a.getString(R.string.open_file_sub)) { a.openList() }
        if (p.davUrl.isNotEmpty()) item(R.string.open_from_server, a.getString(R.string.open_server_sub)) { SyncDialog.openFromServer(a) }
        else item(R.string.open_from_server, a.getString(R.string.open_server_off)) { SyncDialog.show(a) }

        dialog.show()
        Ui.glassDialog(dialog)
    }
}
