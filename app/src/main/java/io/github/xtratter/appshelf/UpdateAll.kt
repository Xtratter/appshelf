package io.github.xtratter.appshelf

import io.github.xtratter.uikit.Haptics
import android.app.AlertDialog
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean

/**
 * «Обновить все»: приложения с новой версией на GitHub обновляются одно за другим.
 * Очередь живёт в самом объекте, а окно прогресса — на текущем экране: при повороте и пересоздании экрана
 * окно закрывается ([detach]) и открывается на новом ([attach]); если экрана в этот момент нет — очередь ждёт ([parked]).
 */
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
    /** Подпись над полосой («2 из 5: DroidTop 1.11 → 1.12.1») — чтобы показать её и в окне на новом экране. */
    private var upText = ""
    /** Очередь дошла до шага, но живого экрана не было: продолжим, когда он появится. */
    private var parked = false

    private val running get() = upTotal > 0 && !upStop.get()

    /**
     * Обновить приложения одно за другим: скачать APK релиза и поставить. Приложения, которые когда-то поставил сам
     * AppShelf, на Android 12+ обновляются без окна подтверждения; остальные Android попросит подтвердить.
     */
    fun start(a: MainActivity, items: List<UpdateItem>) {
        if (items.isEmpty()) return
        if (ApkInstaller.needPermission(a) { start(a, items) }) return
        upStop = AtomicBoolean(false)
        updates.clear(); updates.addAll(items)
        upTotal = items.size; upDone = 0; upFailed = 0; upText = ""; parked = false
        showDialog(a)
        next(a)
    }

    private fun showDialog(a: MainActivity) {
        val title = TextView(a).apply { textSize = 15f; setTextColor(Ui.TEXT); text = upText }
        val bar = progressBar(a)
        val stop = upStop
        upDialog = progressDialog(a, a.getString(R.string.upd_all_title),
            progressBox(a, title to 0, bar to 10), R.string.q_stop) { stop.set(true); updates.clear() }
        upTitle = title; upBar = bar
    }

    /** Экран открылся (в том числе новый после поворота): окно прогресса — на него, и если очередь ждала, продолжаем. */
    fun attach(a: MainActivity) {
        if (!running) return
        if (upDialog?.isShowing != true) showDialog(a)
        if (parked) { parked = false; next(a) }
    }

    /** Экран закрывается: окно закрыть, чтобы оно не утекло вместе с ним; очередь остаётся. */
    fun detach() {
        runCatching { upDialog?.dismiss() }
        upDialog = null; upTitle = null; upBar = null
    }

    /** Ответ системы про текущее обновление ([ok] — поставилось) — и дальше по очереди. */
    fun result(ok: Boolean) {
        if (ok) upDone++ else upFailed++
        val a = MainActivity.current?.get()?.takeIf { !it.isDestroyed }
        if (a != null) a.runOnUiThread { next(a) } else if (running) parked = true else updates.clear()
    }

    private fun next(from: MainActivity) {
        val a = liveHost(from) ?: run { parked = true; return }
        val item = if (upStop.get()) null else updates.removeFirstOrNull()
        if (item == null) {
            detach()
            if (upTotal > 0) {
                Haptics.play(if (upFailed == 0) Haptics.Kind.SUCCESS else Haptics.Kind.ERROR)
                Toast.makeText(a, a.getString(R.string.upd_all_done, upDone, upTotal), Toast.LENGTH_LONG).show()
            }
            upTotal = 0
            a.onInstalled()
            return
        }
        val pkg = item.pkg; val label = item.label; val url = item.url
        val n = upTotal - updates.size
        upText = a.getString(R.string.upd_all_progress, n, upTotal, label, item.from, item.to)
        upTitle?.text = upText
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
                val h = liveHost(a)
                if (h == null) {
                    // экрана нет (пересоздаётся): файл не теряем, но ставить без экрана не будем — шаг считаем пропущенным
                    file.delete(); upFailed++; parked = true
                    return@runOnUiThread
                }
                when {
                    upStop.get() -> { file.delete(); next(h) }
                    err != null -> {
                        Toast.makeText(h, h.getString(R.string.inst_failed, label + ": " + Sync.error(h, err)), Toast.LENGTH_LONG).show()
                        upFailed++; next(h)
                    }
                    @Suppress("DEPRECATION") h.packageManager.getPackageArchiveInfo(file.path, 0)?.packageName != pkg -> {
                        Toast.makeText(h, h.getString(R.string.inst_failed, label + ": " + h.getString(R.string.inst_not_apk)), Toast.LENGTH_LONG).show()
                        file.delete(); upFailed++; next(h)
                    }
                    else -> if (!ApkInstaller.commit(h, file, update = true)) { upFailed++; next(h) }
                }
            }
        }.start()
    }
}
