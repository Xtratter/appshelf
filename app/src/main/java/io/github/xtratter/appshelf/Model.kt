package io.github.xtratter.appshelf

/**
 * Одно приложение в списке.
 * [installer] — пакет, который установил приложение (Google Play, F-Droid…), [initiator] — приложение,
 * из которого запустили установку APK (браузер, Telegram, файловый менеджер); оба могут быть пустыми.
 * Даты — миллисекунды с 1970 года (0 — неизвестно).
 */
data class AppInfo(
    val label: String,
    val pkg: String,
    val versionName: String = "",
    val versionCode: Long = 0,
    val installer: String = "",
    val initiator: String = "",
    val firstInstall: Long = 0,
    val lastUpdate: Long = 0,
    val system: Boolean = false,
) {
    val source: Source get() = Source.of(installer, initiator, system)
}

/** Откуда приложение: магазин, APK-файл, предустановлено или неизвестно. */
enum class Source(val title: Int, val color: Int, val stores: List<String> = emptyList()) {
    PLAY(R.string.src_play, 0xFF34A853.toInt(), listOf("com.android.vending")),
    FDROID(R.string.src_fdroid, 0xFF1976D2.toInt(), listOf(
        "org.fdroid.fdroid", "org.fdroid.basic", "com.looker.droidify", "com.machiav3lli.fdroid",
        "in.sunilpaulmathew.izzyondroid")),
    AURORA(R.string.src_aurora, 0xFF7E57C2.toInt(), listOf("com.aurora.store")),
    OBTAINIUM(R.string.src_obtainium, 0xFF00897B.toInt(), listOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid")),
    RUSTORE(R.string.src_rustore, 0xFF0077FF.toInt(), listOf("ru.vk.store")),
    GETAPPS(R.string.src_getapps, 0xFFFF6900.toInt(), listOf("com.xiaomi.market", "com.xiaomi.mipicks")),
    GALAXY(R.string.src_galaxy, 0xFF1428A0.toInt(), listOf("com.sec.android.app.samsungapps")),
    APPGALLERY(R.string.src_appgallery, 0xFFCF0A2C.toInt(), listOf("com.huawei.appmarket")),
    AMAZON(R.string.src_amazon, 0xFFFF9900.toInt(), listOf("com.amazon.venezia")),
    /** Поставлено самим AppShelf (из резервной копии, по ссылке, из релиза GitHub) — он же может и обновлять. */
    APPSHELF(R.string.src_appshelf, 0xFF5C6BC0.toInt(), listOf("io.github.xtratter.appshelf",
        "io.github.xtratter.appshelf.help", "io.github.xtratter.appshelf.mdc")),
    APK(R.string.src_apk, 0xFFF9A825.toInt()),
    PREINSTALLED(R.string.src_preinstalled, 0xFF78909C.toInt()),
    OTHER(R.string.src_other, 0xFF8D6E63.toInt()),
    UNKNOWN(R.string.src_unknown, 0xFF9E9E9E.toInt());

    companion object {
        /** Системные установщики APK: приложение поставили из файла. */
        val PACKAGE_INSTALLERS = setOf(
            "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.miui.packageinstaller", "com.samsung.android.packageinstaller",
            "com.android.shell",   // adb install
        )

        fun of(installer: String, initiator: String, system: Boolean): Source {
            entries.firstOrNull { installer in it.stores }?.let { return it }
            return when {
                installer in PACKAGE_INSTALLERS -> APK
                installer.isNotEmpty() -> OTHER
                system -> PREINSTALLED
                initiator.isNotEmpty() -> APK
                else -> UNKNOWN
            }
        }
    }
}
