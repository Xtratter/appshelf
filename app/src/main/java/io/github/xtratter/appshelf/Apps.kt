package io.github.xtratter.appshelf

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/** Установленные приложения из PackageManager. */
object Apps {
    /** Все приложения; системные без обновлений из магазина отмечены [AppInfo.system]. */
    fun load(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val list = if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.getInstalledPackages(0)
        return list.mapNotNull { info(pm, it) }
    }

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) ctx.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun info(pm: PackageManager, p: PackageInfo): AppInfo? {
        val ai = p.applicationInfo ?: return null
        val system = ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        var installer = ""
        var initiator = ""
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val s = pm.getInstallSourceInfo(p.packageName)
                installer = s.installingPackageName.orEmpty()
                initiator = s.initiatingPackageName.orEmpty()
            } else {
                @Suppress("DEPRECATION")
                installer = pm.getInstallerPackageName(p.packageName).orEmpty()
            }
        } catch (e: Exception) {
            // пакет удалили во время сканирования — источник просто неизвестен
        }
        // «инициатор» совпадает с установщиком, когда ставили прямо из магазина — не дублируем
        if (initiator == installer || initiator in Source.PACKAGE_INSTALLERS) initiator = ""
        return AppInfo(
            label = ai.loadLabel(pm).toString().trim().ifEmpty { p.packageName },
            pkg = p.packageName,
            versionName = p.versionName.orEmpty(),
            versionCode = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong(),
            installer = installer,
            initiator = initiator,
            firstInstall = p.firstInstallTime,
            lastUpdate = p.lastUpdateTime,
            system = system,
        )
    }

    /** Название приложения по пакету (для «через Telegram»); если не установлено — сам пакет. */
    fun labelOf(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    /** Модель телефона и версия Android — подпись к сохранённому списку. */
    fun device(): String {
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL else "$maker ${Build.MODEL}"
        return "$model · Android ${Build.VERSION.RELEASE}"
    }
}
