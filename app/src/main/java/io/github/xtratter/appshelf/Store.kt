package io.github.xtratter.appshelf

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/** Открыть приложение там, откуда его можно поставить заново, или его системные сведения. */
object Store {
    /** Страница приложения в его магазине; если магазина нет — любой, кто умеет market://, иначе поиск в браузере. */
    fun open(ctx: Context, a: AppInfo) {
        val pkg = a.pkg
        val tries = ArrayList<Intent>()
        fun view(uri: String, app: String? = null) = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply { app?.let { setPackage(it) } }
        when (a.source) {
            Source.FDROID -> {
                for (app in Source.FDROID.stores) tries += view("https://f-droid.org/packages/$pkg/", app)
                tries += view("https://f-droid.org/packages/$pkg/")
            }
            Source.RUSTORE -> tries += view("https://www.rustore.ru/catalog/app/$pkg")
            Source.PLAY, Source.AURORA -> {
                tries += view("market://details?id=$pkg", a.installer)
                tries += view("https://play.google.com/store/apps/details?id=$pkg")
            }
            else -> {
                if (a.installer.isNotEmpty()) tries += view("market://details?id=$pkg", a.installer)
            }
        }
        tries += view("market://details?id=$pkg")
        tries += view("https://www.google.com/search?q=" + Uri.encode("$pkg ${a.label} apk"))
        for (i in tries) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                // пробуем следующий способ
            } catch (e: SecurityException) {
            }
        }
        Toast.makeText(ctx, R.string.no_store, Toast.LENGTH_SHORT).show()
    }

    /** Системная карточка «О приложении». */
    fun details(ctx: Context, pkg: String) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, R.string.no_store, Toast.LENGTH_SHORT).show()
        }
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
