package io.github.xtratter.appshelf

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/** Установленные приложения из PackageManager. */
object Apps {
    /** Меньше стольких изменившихся приложений опрашиваем прямо в этом потоке — пул потоков дороже. */
    private const val INLINE = 8

    /**
     * Запись из [known], если приложение с тех пор не менялось (тот же [lastUpdate] и [versionCode]) — тогда название
     * и источник не спрашиваем у системы заново; null — читать заново.
     */
    fun reusable(known: Map<String, AppInfo>, pkg: String, lastUpdate: Long, versionCode: Long): AppInfo? =
        known[pkg]?.takeIf { it.lastUpdate == lastUpdate && it.versionCode == versionCode }

    /**
     * Все приложения; системные без обновлений из магазина отмечены [AppInfo.system].
     * [previous] — список с прошлого раза: у неизменившихся приложений берём из него название и источник, а не читаем
     * их заново (это самое долгое в загрузке); у остальных и при смене языка или прошивки — читаем.
     */
    fun load(ctx: Context, previous: List<AppInfo>? = null): List<AppInfo> {
        val t0 = android.os.SystemClock.elapsedRealtime()
        val pm = ctx.packageManager
        val list = if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.getInstalledPackages(0)
        val known = if (previous != null && stampFile(ctx).readTextOrNull() == stamp()) previous.associateBy { it.pkg } else emptyMap()
        val out = arrayOfNulls<AppInfo>(list.size)
        val todo = ArrayList<Int>()
        for ((i, p) in list.withIndex()) {
            val same = reusable(known, p.packageName, p.lastUpdateTime, versionCodeOf(p))
            if (same != null) out[i] = same else todo += i
        }
        // название и источник — отдельный запрос к системе на каждое приложение; много — опрашиваем в несколько потоков
        if (todo.size <= INLINE) for (i in todo) out[i] = info(pm, list[i])
        else {
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            val pool = java.util.concurrent.Executors.newFixedThreadPool(threads)
            try {
                val futures = todo.map { i -> pool.submit<AppInfo?> { info(pm, list[i]) } }
                for ((k, i) in todo.withIndex()) out[i] = futures[k].get()
            } finally {
                pool.shutdown()
            }
        }
        android.util.Log.d("AppShelf", "load: ${list.size} apps, ${todo.size} read, ${android.os.SystemClock.elapsedRealtime() - t0} ms")
        return out.filterNotNull()
    }

    private fun versionCodeOf(p: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong()

    /** Что должно совпасть, чтобы сохранённые названия и источники ещё годились: язык и сборка прошивки. */
    private fun stamp() = java.util.Locale.getDefault().toLanguageTag() + "|" + Build.FINGERPRINT

    private fun stampFile(ctx: Context) = java.io.File(ctx.cacheDir, "apps.stamp")

    private fun java.io.File.readTextOrNull(): String? = runCatching { readText() }.getOrNull()

    private fun cacheFile(ctx: Context) = java.io.File(ctx.cacheDir, "apps.json")

    /** Список с прошлого запуска — чтобы показать его сразу, пока свежий собирается в фоне. */
    fun cached(ctx: Context): List<AppInfo>? = runCatching {
        val a = org.json.JSONArray(cacheFile(ctx).readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONArray(i)
            AppInfo(o.getString(0), o.getString(1), o.getString(2), o.getLong(3), o.getString(4), o.getString(5),
                o.getLong(6), o.getLong(7), o.getBoolean(8))
        }
    }.getOrNull()

    fun saveCache(ctx: Context, apps: List<AppInfo>) {
        val a = org.json.JSONArray()
        for (x in apps) a.put(org.json.JSONArray().put(x.label).put(x.pkg).put(x.versionName).put(x.versionCode)
            .put(x.installer).put(x.initiator).put(x.firstInstall).put(x.lastUpdate).put(x.system))
        runCatching { cacheFile(ctx).writeText(a.toString()); stampFile(ctx).writeText(stamp()) }
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
            versionCode = versionCodeOf(p),
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

    /** Сохраняемый список: без системных, если они скрыты, и без исключённых пользователем. */
    fun snapshot(ctx: Context, all: List<AppInfo>, prefs: Prefs): Snapshot {
        val excl = prefs.excluded
        val apps = all.filter { (prefs.showSystem || !it.system) && it.pkg !in excl }
        val mine = LinkStore.mine(ctx)
        val links = apps.mapNotNull { a -> mine[a.pkg]?.takeIf { it.links.isNotEmpty() || it.note.isNotBlank() }?.let { a.pkg to it } }.toMap()
        return Snapshot(System.currentTimeMillis(), device(ctx), ListFile.sorted(apps), links)
    }

    private fun model(): String {
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        return if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL else "$maker ${Build.MODEL}"
    }

    /** Имя устройства из настроек или торговое название («POCO F3») — оно понятнее кода модели («M2012K11AG»). */
    private fun deviceName(ctx: Context): String {
        val name = try {
            android.provider.Settings.Global.getString(ctx.contentResolver, android.provider.Settings.Global.DEVICE_NAME)
        } catch (e: Exception) {
            null
        }?.trim().orEmpty()
        if (name.isNotEmpty() && !name.equals(Build.MODEL, true) && !name.equals(model(), true)) return name
        // иначе — торговое название, которое производитель прописал в прошивке («POCO F3»)
        return listOf("ro.product.marketname", "ro.product.vendor.marketname", "ro.config.marketing_name")
            .map { prop(it).trim() }.firstOrNull { it.isNotEmpty() && !it.equals(Build.MODEL, true) }.orEmpty()
    }

    /** Системное свойство Android; пустая строка, если его нет или прочитать не дали. */
    private fun prop(key: String): String = try {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, key) as String
    } catch (e: Throwable) {
        ""
    }

    /** Модель телефона и версия Android — подпись к сохранённому списку. */
    fun device(ctx: Context): String {
        val name = deviceName(ctx)
        val phone = if (name.isEmpty()) model() else "$name (${Build.MODEL})"
        return "$phone · Android ${Build.VERSION.RELEASE}"
    }

    /** Короткое имя телефона для имени файла: «POCO F3», без символов, которые не любят файловые системы. */
    fun shortName(ctx: Context): String =
        deviceName(ctx).ifEmpty { model() }.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").trim().ifEmpty { "phone" }
}
