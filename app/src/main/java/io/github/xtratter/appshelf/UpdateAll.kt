package io.github.xtratter.appshelf

import io.github.xtratter.uikit.Haptics
import android.app.AlertDialog
import android.content.Context
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean

/** «Обновить все»: приложения с новой версией на GitHub обновляются одно за другим. */
object UpdateAll {
    /** Очередь обновлений: пакет, название, ссылка на APK. */
    private val updates = ArrayDeque<UpdateItem>()
    private var upDialog: AlertDialog? = null
    private var upTitle: TextView? = null
    private var upBar: ProgressBar? = null
    private var upStop = AtomicBoolean(false)
    private var upTotal = 0
    private var upDone = 0
    private var upFailed = 0
    private var upCurrent = ""

    /**
     * Обновить приложения одно за другим: скачать APK релиза и поставить. Приложения, которые когда-то поставил сам
     * AppShelf, на Android 12+ обновляются без окна подтверждения; остальные Android попросит подтвердить.
     */
    fun start(a: MainActivity, items: List<UpdateItem>) {
        if (items.isEmpty()) return
        if (ApkInstaller.needPermission(a) { start(a, items) }) return
        val title = TextView(a).apply { textSize = 15f; setTextColor(Ui.TEXT) }
        val bar = progressBar(a)
        upStop = AtomicBoolean(false)
        val dialog = progressDialog(a, a.getString(R.string.upd_all_title),
            progressBox(a, title to 0, bar to 10), R.string.q_stop) { upStop.set(true); updates.clear() }
        upDialog = dialog; upTitle = title; upBar = bar
        updates.clear(); updates.addAll(items)
        upTotal = items.size; upDone = 0; upFailed = 0
        next(a)
    }

    /** Ответ системы про текущее обновление ([ok] — поставилось) — и дальше по очереди. */
    fun result(ok: Boolean) {
        if (ok) upDone++ else upFailed++
        val a = MainActivity.current?.get()
        if (a != null) a.runOnUiThread { next(a) } else updates.clear()
    }

    private fun next(a: MainActivity) {
        val item = if (upStop.get()) null else updates.removeFirstOrNull()
        if (item == null) {
            upDialog?.dismiss(); upDialog = null
            if (upTotal > 0) {
                Haptics.play(if (upFailed == 0) Haptics.Kind.SUCCESS else Haptics.Kind.ERROR)
                Toast.makeText(a, a.getString(R.string.upd_all_done, upDone, upTotal), Toast.LENGTH_LONG).show()
            }
            upTotal = 0
            a.onInstalled()
            return
        }
        val pkg = item.pkg; val label = item.label; val url = item.url
        upCurrent = label
        val n = upTotal - updates.size
        upTitle?.text = a.getString(R.string.upd_all_progress, n, upTotal, label, item.from, item.to)
        upBar?.apply { isIndeterminate = true }
        val file = newApkFile(a, "update.apk")
        Thread {
            val err = try {
                ApkInstaller.fetchUrl(url, file, upStop) { d, total ->
                    a.runOnUiThread { upBar?.setFraction(d, total) }
                }
                null
            } catch (e: Exception) { e }
            a.runOnUiThread {
                when {
                    upStop.get() -> { file.delete(); next(a) }
                    err != null -> {
                        Toast.makeText(a, a.getString(R.string.inst_failed, label + ": " + Sync.error(a, err)), Toast.LENGTH_LONG).show()
                        upFailed++; next(a)
                    }
                    @Suppress("DEPRECATION") a.packageManager.getPackageArchiveInfo(file.path, 0)?.packageName != pkg -> {
                        Toast.makeText(a, a.getString(R.string.inst_failed, label + ": " + a.getString(R.string.inst_not_apk)), Toast.LENGTH_LONG).show()
                        file.delete(); upFailed++; next(a)
                    }
                    else -> if (!ApkInstaller.commit(a, file, update = true)) { upFailed++; next(a) }
                }
            }
        }.start()
    }
}
