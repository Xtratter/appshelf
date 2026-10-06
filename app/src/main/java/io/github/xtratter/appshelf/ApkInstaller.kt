package io.github.xtratter.appshelf

import io.github.xtratter.uikit.Haptics
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Установка по прямой ссылке на .apk: скачать (с прогрессом и отменой), проверить, что в файле то самое приложение,
 * и передать системе (PackageInstaller — Android сам спросит подтверждение).
 * Нужно разрешение «Установка неизвестных приложений»: если его нет — просим и продолжаем, когда вернутся из настроек.
 */
object ApkInstaller {
    /** Что сделать, когда дадут разрешение на установку (вернулись из настроек). */
    private var pending: (() -> Unit)? = null

    /** Нет разрешения на установку — объяснить и открыть настройки; [then] выполнится, когда вернутся с разрешением. */
    internal fun needPermission(a: MainActivity, then: () -> Unit): Boolean {
        if (a.packageManager.canRequestPackageInstalls()) { pending = null; return false }
        pending = then
        AlertDialog.Builder(a)
            .setTitle(R.string.inst_perm_title)
            .setMessage(R.string.inst_perm_text)
            .setPositiveButton(R.string.inst_perm_open) { _, _ ->
                try {
                    a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName)))
                } catch (e: Exception) {
                    a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> pending = null }
            .show().also { Ui.glassDialog(it) }
        return true
    }

    /**
     * Поставить приложение из резервной копии [name] (см. [ApkBackup]): скачать с прогрессом и отдать установщику;
     * копия из нескольких частей (.apks) ставится одним сеансом.
     */
    fun fromBackup(a: MainActivity, pkg: String, label: String, name: String) {
        if (needPermission(a) { fromBackup(a, pkg, label, name) }) return
        val info = progressInfo(a)
        val stop = AtomicBoolean(false)
        val dialog = progressDialog(a, a.getString(R.string.bk_restoring, label),
            progressBox(a, progressBar(a) to 0, info to 6), android.R.string.cancel) { stop.set(true) }
        val file = newApkFile(a, name)
        Thread {
            var shown = 0L
            val err = try {
                ApkBackup.fetch(a, name, file) { done ->
                    if (stop.get()) throw java.io.InterruptedIOException()
                    if (done - shown > 512 * 1024) { shown = done; a.ui { info.text = Formatter.formatShortFileSize(a, done) } }
                }
                null
            } catch (e: Exception) { e }
            a.runOnUiThread {
                dialog.dismiss()
                when {
                    stop.get() -> file.delete()
                    err != null -> {
                        Haptics.play(Haptics.Kind.ERROR)
                        Toast.makeText(a, a.getString(R.string.inst_failed, Sync.error(a, err)), Toast.LENGTH_LONG).show()
                    }
                    name.endsWith(".apks") -> commit(a, file)
                    else -> check(a, file, pkg, label)
                }
            }
        }.start()
    }
    fun start(a: MainActivity, url: String, pkg: String?, label: String) {
        if (needPermission(a) { start(a, url, pkg, label) }) return
        download(a, url, pkg, label)
    }

    /** Вернулись на экран: если разрешение дали — продолжить отложенную установку. */
    fun resume(a: MainActivity) {
        val p = pending ?: return
        pending = null   // не разрешили — забываем, чтобы загрузка не началась неожиданно потом
        if (a.packageManager.canRequestPackageInstalls()) p()
    }

    private fun download(a: MainActivity, url: String, pkg: String?, label: String) {
        val bar = progressBar(a)
        val info = progressInfo(a)
        val head = TextView(a).apply { text = Links.short(url); textSize = 13f; setTextColor(Ui.TEXT3); maxLines = 2 }
        val stop = AtomicBoolean(false)
        val dialog = progressDialog(a, a.getString(R.string.inst_downloading, label),
            progressBox(a, head to 0, bar to 12, info to 6), android.R.string.cancel) { stop.set(true) }
        val file = newApkFile(a, "download.apk")
        Thread {
            val err = try {
                fetchUrl(url, file, stop) { d, total ->
                    a.runOnUiThread {
                        bar.setFraction(d, total)
                        info.text = if (total > 0) a.getString(R.string.inst_progress,
                            Formatter.formatShortFileSize(a, d), Formatter.formatShortFileSize(a, total))
                        else Formatter.formatShortFileSize(a, d)
                    }
                }
                null
            } catch (e: Exception) {
                e
            }
            a.runOnUiThread {
                dialog.dismiss()
                when {
                    stop.get() -> file.delete()
                    err != null -> Toast.makeText(a, a.getString(R.string.inst_failed, Sync.error(a, err)), Toast.LENGTH_LONG).show()
                    else -> check(a, file, pkg, label)
                }
            }
        }.start()
    }

    /** В файле должно быть то самое приложение; другое — только после подтверждения. */
    private fun check(a: MainActivity, file: File, pkg: String?, label: String) {
        val inFile = @Suppress("DEPRECATION") a.packageManager.getPackageArchiveInfo(file.path, 0)?.packageName
        when {
            inFile == null -> Toast.makeText(a, R.string.inst_not_apk, Toast.LENGTH_LONG).show()
            pkg != null && inFile != pkg -> {
                // экран мог пересоздаться, пока качали; без живого экрана чужой файл молча не ставим
                val h = liveHost(a)
                if (h == null) file.delete()
                else AlertDialog.Builder(h)
                    .setTitle(R.string.inst_other_title)
                    .setMessage(h.getString(R.string.inst_other_text, label, pkg, inFile))
                    .setPositiveButton(R.string.inst_anyway) { _, _ -> commit(h, file) }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> file.delete() }
                    .show().also { Ui.glassDialog(it) }
            }
            else -> commit(a, file)
        }
    }

    /** Скачать [url] в [file]; [progress] — примерно каждые 256 КБ (скачано, всего или −1). Только в фоне. */
    internal fun fetchUrl(url: String, file: File, stop: AtomicBoolean, progress: (Long, Long) -> Unit) {
        val c = URL(WebDav.encodeUrl(url.trim())).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "AppShelf")
        try {
            if (c.responseCode !in 200..299) throw WebDav.HttpError(c.responseCode, c.responseMessage.orEmpty())
            val total = c.contentLengthLong
            var done = 0L
            var shown = 0L
            c.inputStream.use { inp ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (!stop.get()) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - shown > 256 * 1024) { shown = done; progress(done, total) }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }

    /** Передать файл установщику; [update] — шаг «Обновить все» (ответ придёт в [UpdateAll.result]). false — не вышло. */
    internal fun commit(ctx: Context, file: File, update: Boolean = false): Boolean {
        try {
            val installer = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            // обновление приложения, которое поставил сам AppShelf, — без окна подтверждения (Android 12+)
            if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            val id = installer.createSession(params)
            installer.openSession(id).use { s ->
                if (file.name.endsWith(".apks")) {
                    // копия из нескольких частей: все .apk из архива — в один сеанс установки
                    java.util.zip.ZipFile(file).use { zip ->
                        for (e in zip.entries().asSequence().filter { it.name.endsWith(".apk") }) {
                            s.openWrite(e.name.substringAfterLast('/'), 0, e.size).use { out ->
                                zip.getInputStream(e).use { it.copyTo(out) }
                                s.fsync(out)
                            }
                        }
                    }
                } else s.openWrite("base.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    s.fsync(out)
                }
                val intent = Intent(ctx, InstallReceiver::class.java).putExtra(InstallReceiver.EXTRA_UPDATE, update)
                s.commit(PendingIntent.getBroadcast(ctx, id, intent, installerFlags()).intentSender)
            }
            return true
        } catch (e: IOException) {
            Toast.makeText(ctx, ctx.getString(R.string.inst_failed, e.message.orEmpty()), Toast.LENGTH_LONG).show()
            return false
        } finally {
            file.delete()
        }
    }
}
