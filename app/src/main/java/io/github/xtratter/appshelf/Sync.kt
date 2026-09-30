package io.github.xtratter.appshelf

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Отправка списка на WebDAV по расписанию.
 * Будильник (без точного времени, но срабатывает и в режиме сна) → [SyncReceiver] отправляет сразу;
 * если сети нет или нужен Wi-Fi — задача [SyncJob] дождётся подходящей сети и повторит.
 */
object Sync {
    const val ACTION_RUN = "io.github.xtratter.appshelf.SYNC"
    private const val JOB_ID = 1
    /** Периодическая проверка: не пропущена ли плановая отправка (будильник мог не сработать). */
    const val WATCH_ID = 2
    private const val WATCH_EVERY = 3 * 60 * 60 * 1000L
    /** Имя списка в папке телефона; с версиями к нему добавляются дата и время. */
    const val FILE = "AppShelf.json"

    class Result(val ok: Boolean, val message: String, val retry: Boolean)

    fun dav(p: Prefs) = WebDav(p.davUrl, p.davUser, p.davPass)

    /** Папка телефона на сервере: у каждого своя, новый телефон не затрёт списки старого. */
    fun deviceFolder(ctx: Context, p: Prefs) = p.davDevice.trim().trim('/').replace('/', '_').ifEmpty { Apps.shortName(ctx) }

    fun enabled(p: Prefs) = p.davUrl.isNotBlank() && p.schedule().repeat != Repeat.OFF

    /** Отправить список сейчас. Только в фоновом потоке. */
    @Synchronized
    fun run(ctx: Context): Result {
        val p = Prefs(ctx)
        val r = try {
            if (p.davUrl.isBlank()) throw IllegalStateException(ctx.getString(R.string.dav_no_server))
            val snap = Apps.snapshot(ctx, Apps.load(ctx), p)
            val device = deviceFolder(ctx, p)
            val dav = dav(p)
            val data = ListFile.write(snap, Format.JSON).toByteArray(Charsets.UTF_8)
            val keep = p.davKeep
            val name = if (keep <= 1) FILE else ListFile.versionName(FILE, snap.created)
            dav.put("$device/$name", data, Format.JSON.mime)
            // лишние старые версии удаляем; если не вышло — не страшно, список уже отправлен
            if (keep > 1) try {
                for (old in ListFile.oldVersions(dav.list(device).map { it.name }, FILE, keep)) dav.delete("$device/$old")
            } catch (e: Exception) {
            }
            p.lastSaved = snap.created
            p.lastSavedName = "WebDAV · $device/$name"
            p.syncPending = false
            Result(true, "$device/$name", false)
        } catch (e: Exception) {
            // сеть и ошибки сервера — повторим позже; неверный пароль или адрес — нет смысла
            val retry = (e is IOException && e !is WebDav.HttpError && e !is SSLException) ||
                (e is WebDav.HttpError && e.code >= 500 && e.code != 507)
            Result(false, error(ctx, e), retry)
        }
        p.syncLast = System.currentTimeMillis()
        p.syncOk = r.ok
        p.syncMsg = r.message
        return r
    }

    fun error(ctx: Context, e: Exception): String = when (e) {
        is WebDav.HttpError -> when (e.code) {
            401 -> ctx.getString(R.string.dav_e401)
            403 -> ctx.getString(R.string.dav_e403)
            404, 409 -> ctx.getString(R.string.dav_e404)
            507 -> ctx.getString(R.string.dav_e507)
            else -> e.message.orEmpty()
        }
        is UnknownHostException -> ctx.getString(R.string.dav_e_host)
        is SocketTimeoutException, is ConnectException -> ctx.getString(R.string.dav_e_timeout)
        is SSLException -> ctx.getString(R.string.dav_e_tls, e.message.orEmpty())
        is java.net.MalformedURLException -> ctx.getString(R.string.dav_e_url)
        // сокет не создаётся вовсе — сеть закрыта для приложения (настройки Android, VPN, файрвол)
        is java.net.SocketException -> if (e.message.orEmpty().startsWith("socket failed")) ctx.getString(R.string.dav_e_blocked)
            else ctx.getString(R.string.dav_e_net, e.message.orEmpty())
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun alarm(ctx: Context) = PendingIntent.getBroadcast(ctx, 0,
        Intent(ctx, SyncReceiver::class.java).setAction(ACTION_RUN),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Поставить будильник на следующий раз после [after] — или снять, если расписание выключено. */
    fun schedule(ctx: Context, after: Long = System.currentTimeMillis()) {
        val p = Prefs(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val next = if (p.davUrl.isBlank()) null else p.schedule().next(after)
        p.syncNext = next ?: 0
        val js = ctx.getSystemService(JobScheduler::class.java)
        if (next == null) {
            am.cancel(alarm(ctx))
            js.cancel(JOB_ID)
            js.cancel(WATCH_ID)
            p.syncPending = false
            return
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarm(ctx))
        // проверку ставим один раз (иначе её отсчёт начинался бы заново) — и заново, если поменялось «только Wi-Fi»
        val net = if (p.syncWifi) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY
        @Suppress("DEPRECATION")
        if (js.getPendingJob(WATCH_ID)?.networkType != net) js.schedule(
            JobInfo.Builder(WATCH_ID, ComponentName(ctx, SyncJob::class.java))
                .setPeriodic(WATCH_EVERY)
                .setRequiredNetworkType(net)
                .setPersisted(true)
                .build())
    }

    /** Отправка по расписанию пропущена (время прошло, а успешной отправки не было)? Тогда она становится «должна состояться». */
    private fun catchUp(p: Prefs): Boolean {
        if (!enabled(p)) return false
        if (p.syncNext in 1..System.currentTimeMillis()) p.syncPending = true
        return p.syncPending
    }

    /** Периодическая проверка: пропущенная отправка — выполнить сейчас, будильник — поставить заново. */
    fun watch(ctx: Context) {
        val p = Prefs(ctx)
        val missed = catchUp(p)
        schedule(ctx)
        if (missed) run(ctx)
    }

    /**
     * После перезагрузки, обновления, смены часов и при каждом открытии: если плановая отправка пропущена
     * (телефон был выключен) — отправить при первой возможности; будильник — поставить заново.
     */
    fun ensure(ctx: Context) {
        if (catchUp(Prefs(ctx))) enqueue(ctx)
        schedule(ctx)
    }

    /** Сработал будильник. */
    fun fromAlarm(ctx: Context) {
        val p = Prefs(ctx)
        p.syncPending = true   // снимется только успешной отправкой
        if (p.syncWifi && !unmetered(ctx)) return enqueue(ctx)
        val r = run(ctx)
        if (!r.ok && r.retry) enqueue(ctx)
    }

    /** Отправить, как только будет сеть (с Wi-Fi, если так настроено); при сбое сети — повтор с паузой. */
    fun enqueue(ctx: Context) {
        val p = Prefs(ctx)
        ctx.getSystemService(JobScheduler::class.java).schedule(
            JobInfo.Builder(JOB_ID, ComponentName(ctx, SyncJob::class.java))
                .setRequiredNetworkType(if (p.syncWifi) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(10 * 60_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPersisted(true)
                .build())
    }

    private fun unmetered(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/** Будильник расписания, а также загрузка телефона, обновление приложения и смена времени / часового пояса. */
class SyncReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Sync.ACTION_RUN) {
            // следующий раз ставим сразу: даже если отправка не удастся, расписание не собьётся
            Sync.schedule(ctx, System.currentTimeMillis() + 60_000)
            val pending = goAsync()
            Thread { try { Sync.fromAlarm(ctx) } finally { pending.finish() } }.start()
        } else Sync.ensure(ctx)
    }
}

/** Отправка, отложенная до появления сети. */
class SyncJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            if (params.jobId == Sync.WATCH_ID) {
                Sync.watch(applicationContext)
                jobFinished(params, false)
            } else {
                val r = Sync.run(applicationContext)
                jobFinished(params, !r.ok && r.retry)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters) = true
}
