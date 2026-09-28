package io.github.xtratter.appshelf

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.text.TextUtils
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Карточка приложения: сведения и действия (открыть, магазин, системные настройки, копировать пакет). */
object DetailsDialog {
    fun show(a: MainActivity, r: Row, source: String) {
        val app = r.app
        val dp = a.resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()
        val installedNow = r.installed ?: true
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(22f), px(22f), px(22f), px(8f))
        }

        // шапка: значок, название, пакет
        val head = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        val icon = if (installedNow) Icons.get(a, app.pkg, px(56f)) {} else null
        head.addView(if (icon != null) ImageView(a).apply { setImageBitmap(icon) } else TextView(a).apply {
            text = app.label.trim().take(1).uppercase()
            gravity = Gravity.CENTER
            textSize = 22f
            typeface = Ui.medium
            setTextColor(Ui.TEXT)
            background = Ui.pill(a, Ui.withAlpha(app.source.color, 0.5f))
        }, LinearLayout.LayoutParams(px(56f), px(56f)))
        head.addView(LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(14f), 0, 0, 0)
            addView(TextView(a).apply {
                text = app.label; textSize = 20f; typeface = Ui.medium; setTextColor(Ui.TEXT)
                maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            })
            addView(TextView(a).apply {
                text = app.pkg; textSize = 13f; setTextColor(Ui.TEXT2); maxLines = 2
                ellipsize = TextUtils.TruncateAt.MIDDLE; setTextIsSelectable(true)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(head)

        // сведения: строки «название — значение»
        val info = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = GlassDrawable(a, 18f)
            setPadding(px(16f), px(12f), px(16f), px(12f))
        }
        fun line(label: Int, value: String) {
            if (value.isBlank()) return
            info.addView(LinearLayout(a).apply {
                setPadding(0, px(4f), 0, px(4f))
                addView(TextView(a).apply { setText(label); textSize = 13.5f; setTextColor(Ui.TEXT2) },
                    LinearLayout.LayoutParams(px(120f), -2))
                addView(TextView(a).apply { text = value; textSize = 13.5f; setTextColor(Ui.TEXT); setTextIsSelectable(true) },
                    LinearLayout.LayoutParams(0, -2, 1f))
            })
        }
        line(R.string.d_source, source)
        if (app.installer.isNotEmpty() && app.source != Source.OTHER) line(R.string.d_installer, app.installer)
        line(R.string.d_version, listOf(app.versionName, app.versionCode.takeIf { it > 0 }?.let { "($it)" }.orEmpty())
            .joinToString(" ").trim())
        line(R.string.d_installed, a.dateText(app.firstInstall))
        if (app.lastUpdate > app.firstInstall + 60_000) line(R.string.d_updated, a.dateText(app.lastUpdate))
        if (app.system) line(R.string.d_type, a.getString(R.string.d_system))
        if (r.installed != null) line(R.string.d_status, a.getString(if (installedNow) R.string.st_installed else R.string.st_missing))
        box.addView(info, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(16f) })

        val dialog = AlertDialog.Builder(a)
            .setView(ScrollView(a).apply { addView(box) })
            .setNegativeButton(R.string.close, null)
            .create()

        // действия — тональные кнопки-«пилюли»; главная — залитая
        fun action(text: String, main: Boolean = false, block: () -> Unit) {
            box.addView(TextView(a).apply {
                this.text = text
                gravity = Gravity.CENTER
                textSize = 15f
                typeface = Ui.medium
                setTextColor(if (main) Ui.ON_ACCENT else Ui.primary)
                background = if (main) Ui.pill(a, Ui.primary)
                else Ui.pill(a, Ui.withAlpha(Ui.primary, 0.12f), Ui.withAlpha(Ui.primary, 0.3f))
                foreground = Ui.ripple(a, 100f)
                setOnClickListener { dialog.dismiss(); block() }
            }, LinearLayout.LayoutParams(-1, px(48f)).apply { topMargin = px(8f) })
        }
        box.addView(android.view.View(a), LinearLayout.LayoutParams(1, px(6f)))
        if (!installedNow) {
            action(a.getString(R.string.install_from, storeName(a, app, source)), main = true) { Store.open(a, app) }
        } else {
            if (a.packageManager.getLaunchIntentForPackage(app.pkg) != null)
                action(a.getString(R.string.open_app), main = true) { Store.launch(a, app.pkg) }
            action(a.getString(R.string.open_store)) { Store.open(a, app) }
            action(a.getString(R.string.app_settings)) { Store.details(a, app.pkg) }
        }
        action(a.getString(R.string.copy_pkg)) {
            a.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(app.label, app.pkg))
            Toast.makeText(a, R.string.copied, Toast.LENGTH_SHORT).show()
        }

        dialog.show()
        Ui.glassDialog(dialog)
    }

    /** Куда поведёт кнопка «Установить из …». */
    private fun storeName(a: MainActivity, app: AppInfo, source: String) = when (app.source) {
        Source.APK, Source.PREINSTALLED, Source.UNKNOWN -> a.getString(R.string.store_any)
        Source.OTHER -> source
        else -> a.getString(app.source.title)
    }
}
