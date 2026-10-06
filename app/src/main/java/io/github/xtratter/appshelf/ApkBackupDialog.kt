package io.github.xtratter.appshelf

import io.github.xtratter.uikit.Haptics
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.net.Uri
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean

/** «Копии APK»: куда (WebDAV или папка), что (из APK-файлов или все), обновлять ли при отправке; «Сделать копию сейчас». */
object ApkBackupDialog {
    fun show(a: MainActivity) {
        val p = Prefs(a)
        val dp = a.resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()
        val tint = ColorStateList.valueOf(Ui.primary)
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(22f), px(22f), px(22f), px(8f))
        }
        fun label(text: String, size: Float = 13.5f, color: Int = Ui.TEXT2, top: Float = 0f) = TextView(a).apply {
            this.text = text; textSize = size; setTextColor(color); setLineSpacing(0f, 1.1f); setPadding(0, px(top), 0, px(4f))
        }
        fun header(text: Int) = box.addView(label(a.getString(text), 15f, Ui.primary, 12f).apply { typeface = Ui.medium })
        fun radio(text: String, checked: Boolean, enabled: Boolean = true, click: () -> Unit) = RadioButton(a).apply {
            id = View.generateViewId(); this.text = text; textSize = 15f
            setTextColor(if (enabled) Ui.TEXT else Ui.TEXT3); buttonTintList = tint
            minHeight = px(44f); isChecked = checked; isEnabled = enabled
            setOnClickListener { click() }
        }

        box.addView(TextView(a).apply { setText(R.string.bk_title); textSize = 20f; typeface = Ui.medium; setTextColor(Ui.TEXT) })
        box.addView(label(a.getString(R.string.bk_explain), top = 4f))

        val dialog = AlertDialog.Builder(a).setView(ScrollView(a).apply { addView(box) })
            .setPositiveButton(R.string.bk_now, null)
            .setNegativeButton(R.string.close, null)
            .create()

        // куда
        header(R.string.bk_where)
        val where = RadioGroup(a)
        val davOk = p.davUrl.isNotEmpty()
        where.addView(radio(a.getString(R.string.bk_webdav) + if (davOk) "" else " — " + a.getString(R.string.bk_webdav_off),
            p.apkDest == ApkBackup.Dest.WEBDAV.name && davOk, davOk) { p.apkDest = ApkBackup.Dest.WEBDAV.name })
        val folderName = p.apkFolderUri.takeIf { it.isNotEmpty() }?.let { Uri.parse(it).lastPathSegment?.substringAfterLast(':') }
        where.addView(radio(a.getString(R.string.bk_folder) + (folderName?.let { " — $it" } ?: ""),
            p.apkDest == ApkBackup.Dest.FOLDER.name && p.apkFolderUri.isNotEmpty()) {
            if (p.apkFolderUri.isEmpty()) { dialog.dismiss(); a.pickApkFolder() } else p.apkDest = ApkBackup.Dest.FOLDER.name
        })
        box.addView(where)
        box.addView(label(a.getString(R.string.bk_folder_other), 13f, Ui.primary).apply {
            setOnClickListener { dialog.dismiss(); a.pickApkFolder() }
        })

        // что
        header(R.string.bk_what)
        val what = RadioGroup(a)
        val counts = label("", 12.5f, Ui.TEXT3)
        what.addView(radio(a.getString(R.string.bk_scope_apk), p.apkScope == ApkBackup.Scope.APK_ONLY.name) {
            p.apkScope = ApkBackup.Scope.APK_ONLY.name })
        what.addView(radio(a.getString(R.string.bk_scope_all), p.apkScope == ApkBackup.Scope.ALL.name) {
            p.apkScope = ApkBackup.Scope.ALL.name })
        box.addView(what)
        box.addView(counts)

        box.addView(CheckBox(a).apply {
            setText(R.string.bk_auto); textSize = 15f; setTextColor(Ui.TEXT); buttonTintList = tint
            isChecked = p.apkAuto; minHeight = px(44f)
            setOnCheckedChangeListener { _, on -> p.apkAuto = on }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8f) })
        val status = label("", 13.5f, Ui.TEXT2, 8f)
        box.addView(status)

        dialog.show()
        Ui.glassDialog(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (ApkBackup.dest(p) == null) { status.setTextColor(Ui.WARN); status.setText(R.string.bk_no_dest); return@setOnClickListener }
            dialog.dismiss()
            runNow(a)
        }
        // сколько приложений и сколько копий уже есть — считаем в фоне
        Thread {
            val apk = ApkBackup.targets(a, ApkBackup.Scope.APK_ONLY).size
            val all = ApkBackup.targets(a, ApkBackup.Scope.ALL).size
            val have = runCatching { ApkBackup.index(a).size }.getOrNull()
            a.ui {
                counts.text = a.getString(R.string.bk_counts, apk, all)
                status.text = if (have == null) "" else a.resources.getQuantityString(R.plurals.bk_have, have, have)
            }
        }.start()
    }

    /** Сделать копии сейчас: окно с прогрессом «3 из 24 — Telegram» и «Стоп». */
    fun runNow(a: MainActivity) {
        val p = Prefs(a)
        val dp = a.resources.displayMetrics.density
        val bar = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            progressTintList = ColorStateList.valueOf(Ui.primary); indeterminateTintList = progressTintList
        }
        val info = TextView(a).apply { textSize = 13.5f; setTextColor(Ui.TEXT2); setText(R.string.dav_connecting) }
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), 0)
            addView(bar)
            addView(info, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (6 * dp).toInt() })
            addView(TextView(a).apply {
                setText(R.string.bk_keep_open); textSize = 12.5f; setTextColor(Ui.TEXT3); setPadding(0, (8 * dp).toInt(), 0, 0)
            })
        }
        val stop = AtomicBoolean(false)
        val dialog = AlertDialog.Builder(a).setTitle(R.string.bk_title).setView(box)
            .setNegativeButton(R.string.q_stop) { _, _ -> stop.set(true) }.setCancelable(false).create()
        dialog.show()
        Ui.glassDialog(dialog)
        a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Thread {
            val scope = runCatching { ApkBackup.Scope.valueOf(p.apkScope) }.getOrDefault(ApkBackup.Scope.APK_ONLY)
            val r = ApkBackup.run(a, ApkBackup.targets(a, scope), stop) { i, n, label ->
                a.ui {
                    bar.isIndeterminate = false; bar.max = n; bar.progress = i
                    info.text = a.getString(R.string.bk_progress, i, n, label)
                }
            }
            a.ui {
                a.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                dialog.dismiss()
                Haptics.play(if (r.failed == 0 && r.error == null) Haptics.Kind.SUCCESS else Haptics.Kind.ERROR)
                val msg = r.error?.takeIf { r.saved + r.skipped == 0 } ?: a.getString(R.string.bk_done, r.saved, r.skipped, r.failed)
                Toast.makeText(a, msg, Toast.LENGTH_LONG).show()
                a.refreshBackups()
            }
        }.start()
    }
}
