package io.github.xtratter.appshelf

import android.app.AlertDialog
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Перед очередью восстановления: что откуда поставится и сколько останется вручную. */
object RestorePlanDialog {
    private const val SHOWN_NAMES = 5

    private fun title(w: Way) = when (w) {
        Way.BACKUP -> R.string.rp_backup
        Way.DIRECT -> R.string.rp_direct
        Way.PAGE -> R.string.rp_page
        Way.STORE -> R.string.rp_store
        Way.SEARCH -> R.string.rp_search
    }

    /** [apps] — недостающие приложения; [onStart] получает те, что поставим (без тех, что пользователь пропустил). */
    fun show(a: MainActivity, apps: List<AppInfo>, onStart: (List<AppInfo>) -> Unit) {
        val kit = DialogKit(a)
        val groups = RestorePlan.group(apps, { a.backupFor(it) != null }, { p -> LinkStore.forApp(a, p).map { it.first } })
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(kit.px(22f), kit.px(18f), kit.px(22f), kit.px(4f))
        }
        box.addView(kit.label(a.getString(R.string.rp_hint, apps.size)))
        for ((way, list) in groups) {
            val names = list.take(SHOWN_NAMES).joinToString(", ") { it.label } + if (list.size > SHOWN_NAMES) ", …" else ""
            box.addView(TextView(a).apply {
                text = a.getString(title(way)) + " · " + list.size
                textSize = 15f; typeface = Ui.medium
                setTextColor(if (way == Way.SEARCH) Ui.WARN else Ui.primary)
            }, kit.gap(12f))
            box.addView(kit.label(names, 13f, Ui.TEXT3))
        }
        val manual = groups[Way.SEARCH].orEmpty()
        val skip = CheckBox(a).apply {
            text = a.getString(R.string.rp_skip, manual.size); textSize = 14.5f; setTextColor(Ui.TEXT)
            buttonTintList = android.content.res.ColorStateList.valueOf(Ui.primary)
            visibility = if (manual.isEmpty() || manual.size == apps.size) android.view.View.GONE else android.view.View.VISIBLE
        }
        box.addView(skip, kit.gap(10f))
        AlertDialog.Builder(a)
            .setView(ScrollView(a).apply { addView(box) })
            .setPositiveButton(R.string.rp_go) { _, _ ->
                onStart(if (skip.isChecked) apps.filter { it !in manual } else apps)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show().also { Ui.glassDialog(it) }
    }
}
