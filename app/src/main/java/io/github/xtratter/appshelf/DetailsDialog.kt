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

        // ---------- заметка: зачем приложение, какой вход, что настроить ----------
        val note = LinkStore.note(a, app.pkg)
        box.addView(TextView(a).apply {
            setText(R.string.note_section); textSize = 15f; typeface = Ui.medium; setTextColor(Ui.primary)
            setPadding(px(4f), px(16f), 0, px(6f))
        })
        box.addView(TextView(a).apply {
            text = note.ifEmpty { a.getString(R.string.note_empty) }
            textSize = 14f; setLineSpacing(0f, 1.15f)
            setTextColor(if (note.isEmpty()) Ui.TEXT3 else Ui.TEXT)
            background = GlassDrawable(a, 18f)
            foreground = Ui.ripple(a, 18f)
            setPadding(px(16f), px(12f), px(16f), px(12f))
            setOnClickListener {
                dialog.dismiss(); NoteDialog.show(a, app.pkg, app.label) { a.refresh(); show(a, r, source) }
            }
        })

        // ---------- где скачать: свои ссылки, затем из каталога ----------
        val links = LinkStore.forApp(a, app.pkg)
        /** После правки ссылок — обновить список и открыть карточку заново. */
        val reopen = { a.refresh(); show(a, r, source) }
        box.addView(TextView(a).apply {
            setText(R.string.lk_section); textSize = 15f; typeface = Ui.medium; setTextColor(Ui.primary)
            setPadding(px(4f), px(16f), 0, px(6f))
        })
        val linksBox = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = GlassDrawable(a, 18f)
            setPadding(px(6f), px(6f), px(6f), px(6f))
        }
        fun linkRow(title: String, sub: String, subColor: Int, click: () -> Unit, longClick: (() -> Unit)?) =
            linksBox.addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                background = Ui.ripple(a, 14f)
                setPadding(px(10f), px(8f), px(10f), px(8f))
                addView(TextView(a).apply { text = title; textSize = 15f; setTextColor(Ui.TEXT); maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                if (sub.isNotEmpty()) addView(TextView(a).apply {
                    text = sub; textSize = 12.5f; setTextColor(subColor); maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE
                })
                setOnClickListener { click() }
                longClick?.let { lc -> setOnLongClickListener { lc(); true } }
            })
        for ((l, fromCatalog) in links) {
            val kind = Links.kind(l.url)
            linkRow(l.label.ifBlank { a.getString(kind.title) } + " ›",
                Links.short(l.url) + if (fromCatalog) " · " + a.getString(R.string.lk_from_catalog) else "",
                Ui.TEXT2, { LinkStore.open(a, l, app.pkg, app.label) },
                if (fromCatalog) null else ({ dialog.dismiss(); LinkEditDialog.show(a, app.pkg, app.label, l, reopen) }))
        }
        if (links.isEmpty()) linksBox.addView(TextView(a).apply {
            setText(R.string.lk_empty); textSize = 13f; setTextColor(Ui.TEXT3); setLineSpacing(0f, 1.1f)
            setPadding(px(10f), px(6f), px(10f), px(4f))
        })
        if (links.any { !it.second }) linksBox.addView(TextView(a).apply {
            setText(R.string.lk_long_press); textSize = 11.5f; setTextColor(Ui.TEXT3); setPadding(px(10f), px(2f), px(10f), px(2f))
        })
        linkRow("+ " + a.getString(R.string.lk_add), "", Ui.TEXT2,
            { dialog.dismiss(); LinkEditDialog.show(a, app.pkg, app.label, null, reopen) }, null)
        (linksBox.getChildAt(linksBox.childCount - 1) as LinearLayout).getChildAt(0).let { (it as TextView).setTextColor(Ui.primary) }
        box.addView(linksBox)

        // действия — тональные кнопки-«пилюли»; главная — залитая
        fun action(text: String, main: Boolean = false, danger: Boolean = false, help: Int = 0, block: () -> Unit) {
            box.addView(TextView(a).apply {
                this.text = text
                if (help != 0) Help.attach(this, text, a.getString(help))
                gravity = Gravity.CENTER
                textSize = 15f
                typeface = Ui.medium
                setTextColor(if (main) Ui.ON_ACCENT else if (danger) Ui.HOT else Ui.primary)
                background = if (main) Ui.pill(a, Ui.primary)
                else if (danger) Ui.pill(a, Ui.withAlpha(Ui.HOT, 0.12f), Ui.withAlpha(Ui.HOT, 0.35f))
                else Ui.pill(a, Ui.withAlpha(Ui.primary, 0.12f), Ui.withAlpha(Ui.primary, 0.3f))
                foreground = Ui.ripple(a, 100f)
                setOnClickListener { dialog.dismiss(); block() }
            }, LinearLayout.LayoutParams(-1, px(48f)).apply { topMargin = px(8f) })
        }
        box.addView(android.view.View(a), LinearLayout.LayoutParams(1, px(6f)))
        if (!installedNow) {
            // своя ссылка или из каталога — главнее магазина
            links.firstOrNull()?.let { (l, _) ->
                action(a.getString(R.string.install_link, l.label.ifBlank { a.getString(Links.kind(l.url).title) }), main = true) { LinkStore.open(a, l, app.pkg, app.label) }
            }
            // резервная копия APK — раньше магазина
            val backup = a.backupFor(app.pkg)
            if (backup != null) action(a.getString(R.string.bk_install), main = links.isEmpty()) {
                ApkInstaller.fromBackup(a, app.pkg, app.label, backup)
            }
            action(a.getString(R.string.install_from, storeName(a, app, source)), main = links.isEmpty() && backup == null, help = R.string.h_install) { Store.open(a, app) }
        } else {
            // на GitHub есть версия новее — обновить прямо отсюда (или открыть релиз, если подходящего APK нет)
            Updates.available(a, app)?.let { rel ->
                action(a.getString(R.string.upd_install, Updates.numbers(rel.tag).joinToString(".").ifEmpty { rel.tag }), main = true) {
                    if (rel.apkUrl != null) ApkInstaller.start(a, rel.apkUrl, app.pkg, app.label)
                    else LinkStore.open(a, Link(rel.page), app.pkg, app.label)
                }
            }
            if (a.packageManager.getLaunchIntentForPackage(app.pkg) != null)
                action(a.getString(R.string.open_app), main = true, help = R.string.h_open) { Store.launch(a, app.pkg) }
            action(a.getString(R.string.open_store), help = R.string.h_store) { Store.open(a, app) }
            action(a.getString(R.string.app_settings), help = R.string.h_sys_settings) { Store.details(a, app.pkg) }
            if (r.installed == null) {
                val out = a.isExcluded(app.pkg)
                action(a.getString(if (out) R.string.include else R.string.exclude), help = R.string.h_exclude) { a.toggleExcluded(app.pkg) }
            }
            // системные удалить нельзя; AppShelf сам себя не удаляет
            if (!app.system && app.pkg != a.packageName)
                action(a.getString(R.string.uninstall), danger = true, help = R.string.h_uninstall) { ApkInstaller.uninstall(a, app.pkg, app.label) }
        }
        action(a.getString(R.string.copy_pkg), help = R.string.h_copy) {
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
