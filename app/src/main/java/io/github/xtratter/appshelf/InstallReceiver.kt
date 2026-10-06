package io.github.xtratter.appshelf

import io.github.xtratter.uikit.Haptics
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/** Ответ системы об установке: попросить подтверждение, сообщить результат. */
class InstallReceiver : BroadcastReceiver() {
    companion object {
        /** Это ответ на удаление; значение — название приложения. */
        const val EXTRA_REMOVED = "io.github.xtratter.appshelf.REMOVED"
        /** Это шаг «Обновить все»: после ответа — следующее обновление. */
        const val EXTRA_UPDATE = "io.github.xtratter.appshelf.UPDATE"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val removed = intent.getStringExtra(EXTRA_REMOVED)
        Kit.init(ctx)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (intent.getBooleanExtra(EXTRA_UPDATE, false) && status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            if (status != PackageInstaller.STATUS_SUCCESS && status != PackageInstaller.STATUS_FAILURE_ABORTED)
                Toast.makeText(ctx, ctx.getString(R.string.inst_failed, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()),
                    Toast.LENGTH_LONG).show()
            UpdateAll.result(status == PackageInstaller.STATUS_SUCCESS)
            return
        }
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Haptics.play(Haptics.Kind.SUCCESS)
                Toast.makeText(ctx, if (removed != null) ctx.getString(R.string.uninstall_done, removed)
                    else ctx.getString(R.string.inst_done), Toast.LENGTH_SHORT).show()
                MainActivity.current?.get()?.let { m -> m.runOnUiThread { m.onInstalled() } }
                if (removed != null) Uninstaller.next(ctx)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> if (removed != null) Uninstaller.next(ctx, stop = true)   // отменили — остальные не удаляем
            else -> Haptics.play(Haptics.Kind.ERROR).let { _ -> Toast.makeText(ctx, ctx.getString(if (removed != null) R.string.uninstall_failed else R.string.inst_failed,
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()), Toast.LENGTH_LONG).show()
                if (removed != null) Uninstaller.next(ctx) }
        }
    }
}