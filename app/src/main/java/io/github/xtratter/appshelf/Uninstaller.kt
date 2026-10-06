package io.github.xtratter.appshelf

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Удаление приложений, в том числе несколькими: следующее — когда Android ответил про предыдущее. */
object Uninstaller {
    /** Очередь удаления нескольких приложений: следующее — когда Android ответил про предыдущее. */
    private val queue = ArrayDeque<Pair<String, String>>()

    fun all(ctx: Context, apps: List<Pair<String, String>>) {
        queue.clear()
        queue.addAll(apps)
        next(ctx)
    }

    /** Следующее из очереди (или ничего). [stop] — пользователь отменил: остальные не трогаем. */
    fun next(ctx: Context, stop: Boolean = false) {
        if (stop) { queue.clear(); return }
        val (pkg, label) = queue.removeFirstOrNull() ?: return
        one(ctx, pkg, label)
    }

    /** Удалить приложение: Android сам спросит подтверждение; ответ — в [InstallReceiver]. */
    fun one(ctx: Context, pkg: String, label: String) {
        val intent = Intent(ctx, InstallReceiver::class.java).putExtra(InstallReceiver.EXTRA_REMOVED, label)
        try {
            ctx.packageManager.packageInstaller.uninstall(pkg,
                PendingIntent.getBroadcast(ctx, pkg.hashCode(), intent, installerFlags()).intentSender)
        } catch (e: Exception) {
            Toast.makeText(ctx, ctx.getString(R.string.uninstall_failed, e.message.orEmpty()), Toast.LENGTH_LONG).show()
        }
    }
}
