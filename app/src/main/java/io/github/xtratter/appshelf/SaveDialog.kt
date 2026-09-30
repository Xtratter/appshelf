package io.github.xtratter.appshelf

import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * «Сохранение и экспорт» — все способы сохранить список в одном окне:
 * файл, «Поделиться», автосохранение, WebDAV и выбор приложений. У каждого пункта видно его состояние.
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
            setText(R.string.save_export); textSize = 20f; typeface = Ui.medium; setTextColor(Ui.TEXT)
            setPadding(px(4f), 0, 0, px(12f))
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

        dialog.show()
        Ui.glassDialog(dialog)
    }
}
