package io.github.xtratter.appshelf

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Установка по прямой ссылке на .apk: скачать (с прогрессом и отменой), проверить, что в файле то самое приложение,
 * и передать системе (PackageInstaller — Android сам спросит подтверждение).
 * Нужно разрешение «Установка неизвестных приложений»: если его нет — просим и продолжаем, когда вернутся из настроек.
 */
object ApkInstaller {
    private class Pending(val url: String, val pkg: String?, val label: String)

    private var pending: Pending? = null

    fun start(a: MainActivity, url: String, pkg: String?, label: String) {
        if (!a.packageManager.canRequestPackageInstalls()) {
            pending = Pending(url, pkg, label)
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
            return
        }
        pending = null
        download(a, url, pkg, label)
    }

    /** Вернулись на экран: если разрешение дали — продолжить отложенную установку. */
    fun resume(a: MainActivity) {
        val p = pending ?: return
        // не разрешили — забываем, чтобы загрузка не началась неожиданно потом
        if (a.packageManager.canRequestPackageInstalls()) start(a, p.url, p.pkg, p.label) else pending = null
    }

    private fun download(a: MainActivity, url: String, pkg: String?, label: String) {
        val dp = a.resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()
        val bar = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            progressTintList = android.content.res.ColorStateList.valueOf(Ui.primary)
            indeterminateTintList = progressTintList
        }
        val info = TextView(a).apply { textSize = 13.5f; setTextColor(Ui.TEXT2); setText(R.string.dav_connecting) }
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24f), px(8f), px(24f), 0)
            addView(TextView(a).apply { text = Links.short(url); textSize = 13f; setTextColor(Ui.TEXT3); maxLines = 2 })
            addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12f) })
            addView(info, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(6f) })
        }
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val dialog = AlertDialog.Builder(a)
            .setTitle(a.getString(R.string.inst_downloading, label))
            .setView(box)
            .setNegativeButton(android.R.string.cancel) { _, _ -> stop.set(true) }
            .setCancelable(false)
            .create()
        dialog.show()
        Ui.glassDialog(dialog)

        val dir = File(a.cacheDir, "apk").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val file = File(dir, "download.apk")
        Thread {
            val err = try {
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
                                if (done - shown > 256 * 1024) {
                                    shown = done
                                    val d = done
                                    a.runOnUiThread {
                                        if (total > 0) { bar.isIndeterminate = false; bar.max = 1000; bar.progress = (d * 1000 / total).toInt() }
                                        info.text = if (total > 0) a.getString(R.string.inst_progress,
                                            Formatter.formatShortFileSize(a, d), Formatter.formatShortFileSize(a, total))
                                        else Formatter.formatShortFileSize(a, d)
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    c.disconnect()
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
            pkg != null && inFile != pkg -> AlertDialog.Builder(a)
                .setTitle(R.string.inst_other_title)
                .setMessage(a.getString(R.string.inst_other_text, label, pkg, inFile))
                .setPositiveButton(R.string.inst_anyway) { _, _ -> commit(a, file) }
                .setNegativeButton(android.R.string.cancel) { _, _ -> file.delete() }
                .show().also { Ui.glassDialog(it) }
            else -> commit(a, file)
        }
    }

    /** Удалить приложение: Android сам спросит подтверждение; ответ — в [InstallReceiver]. */
    fun uninstall(ctx: Context, pkg: String, label: String) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val intent = Intent(ctx, InstallReceiver::class.java).putExtra(InstallReceiver.EXTRA_REMOVED, label)
        try {
            ctx.packageManager.packageInstaller.uninstall(pkg,
                PendingIntent.getBroadcast(ctx, pkg.hashCode(), intent, flags).intentSender)
        } catch (e: Exception) {
            Toast.makeText(ctx, ctx.getString(R.string.uninstall_failed, e.message.orEmpty()), Toast.LENGTH_LONG).show()
        }
    }

    private fun commit(ctx: Context, file: File) {
        try {
            val installer = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val id = installer.createSession(params)
            installer.openSession(id).use { s ->
                s.openWrite("base.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    s.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                s.commit(PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java), flags).intentSender)
            }
        } catch (e: IOException) {
            Toast.makeText(ctx, ctx.getString(R.string.inst_failed, e.message.orEmpty()), Toast.LENGTH_LONG).show()
        } finally {
            file.delete()
        }
    }
}

/** Ответ системы об установке: попросить подтверждение, сообщить результат. */
class InstallReceiver : BroadcastReceiver() {
    companion object {
        /** Это ответ на удаление; значение — название приложения. */
        const val EXTRA_REMOVED = "io.github.xtratter.appshelf.REMOVED"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val removed = intent.getStringExtra(EXTRA_REMOVED)
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Toast.makeText(ctx, if (removed != null) ctx.getString(R.string.uninstall_done, removed)
                    else ctx.getString(R.string.inst_done), Toast.LENGTH_SHORT).show()
                MainActivity.current?.get()?.let { m -> m.runOnUiThread { m.onInstalled() } }
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {}   // отменили в системном окне
            else -> Toast.makeText(ctx, ctx.getString(if (removed != null) R.string.uninstall_failed else R.string.inst_failed,
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()), Toast.LENGTH_LONG).show()
        }
    }
}
