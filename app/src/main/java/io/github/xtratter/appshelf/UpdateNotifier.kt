package io.github.xtratter.appshelf

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Уведомление «Доступны обновления» после плановой отправки списка: релизы GitHub проверяются тем же кэшем, что и в списке
 * (не чаще раза в 6 часов на репозиторий), а уведомление приходит только о том, что появилось впервые.
 */
object UpdateNotifier {
    const val EXTRA_UPDATES = "io.github.xtratter.appshelf.UPDATES"
    private const val CHANNEL = "updates"
    private const val NOTIFICATION_ID = 10
    private const val SHOWN = 5

    data class Item(val pkg: String, val label: String, val from: String, val to: String, val tag: String) {
        val key get() = "$pkg:$tag"
    }

    // ---------- без Android: проверяется unit-тестами ----------

    /** Обновления, о которых ещё не сообщали. */
    fun fresh(items: List<Item>, notified: Set<String>): List<Item> = items.filter { it.key !in notified }

    /** «DroidTop 1.11 → 1.12, Telegram 10 → 11, …» — первые [SHOWN], остальное многоточием. */
    fun summary(items: List<Item>): String =
        items.take(SHOWN).joinToString(", ") { "${it.label} ${it.from} → ${it.to}" } + if (items.size > SHOWN) ", …" else ""

    // ---------- с Android ----------

    /** Только в фоне, после успешной плановой отправки. */
    fun afterSync(ctx: Context) {
        val p = Prefs(ctx)
        if (!p.updNotify || !canNotify(ctx)) return
        val apps = Apps.load(ctx, Apps.cached(ctx)).filter { !it.system }
        Updates.check(ctx, apps)
        val items = apps.mapNotNull { a ->
            Updates.available(ctx, a)?.let { rel ->
                val to = Updates.numbers(rel.tag).joinToString(".").ifEmpty { rel.tag.removePrefix("v") }
                Item(a.pkg, a.label, Ui.versionShort(a.versionName), to, rel.tag)
            }
        }.sortedBy { it.label.lowercase() }
        val new = fresh(items, p.updNotified)
        // запоминаем только те, что ещё актуальны: вышедшие обновления потом забываются, а следующий релиз — снова новость
        p.updNotified = items.mapTo(HashSet()) { it.key }
        if (new.isEmpty()) return
        post(ctx, items)
    }

    fun canNotify(ctx: Context) = android.os.Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun post(ctx: Context, items: List<Item>) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.nt_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 0,
            Intent(ctx, MainActivity::class.java).putExtra(EXTRA_UPDATES, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = summary(items)
        nm.notify(NOTIFICATION_ID, Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify_update)
            .setContentTitle(ctx.resources.getQuantityString(R.plurals.nt_title, items.size, items.size))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }
}
