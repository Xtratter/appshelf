package io.github.xtratter.appshelf

/**
 * План восстановления: откуда будет ставиться каждое недостающее приложение. Порядок проверки — тот же, что у очереди:
 * резервная копия → своя ссылка или из каталога → магазин, из которого оно было → просто поиск.
 * Без Android-зависимостей — проверяется unit-тестами.
 */
enum class Way {
    /** Резервная копия APK на телефоне или в облаке — ставится без сети. */
    BACKUP,
    /** Ссылка на APK или на GitHub: скачивается и ставится прямо в AppShelf. */
    DIRECT,
    /** Другая сохранённая ссылка: открывается страница (или Obtainium), дальше вручную. */
    PAGE,
    /** Страница приложения в магазине, откуда оно было. */
    STORE,
    /** Источника нет: откроется поиск, ставить придётся вручную. */
    SEARCH,
}

object RestorePlan {
    /** Есть ли у приложения «своя» страница в магазине (иначе откроется общий поиск). */
    fun hasStorePage(a: AppInfo): Boolean = when (a.source) {
        Source.APPSHELF, Source.APK, Source.PREINSTALLED, Source.UNKNOWN -> false
        else -> true
    }

    /** Как будет ставиться [a]; [links] — его ссылки (свои, затем из каталога). */
    fun wayOf(a: AppInfo, hasBackup: Boolean, links: List<Link>): Way {
        if (hasBackup) return Way.BACKUP
        val link = links.firstOrNull()
        if (link != null) {
            return if (Links.kind(link.url) == LinkKind.APK || Updates.repoOf(link.url) != null) Way.DIRECT else Way.PAGE
        }
        return if (hasStorePage(a)) Way.STORE else Way.SEARCH
    }

    /** Приложения по способам, в порядке от самого надёжного; пустые группы пропускаются. */
    fun group(apps: List<AppInfo>, backup: (String) -> Boolean, links: (String) -> List<Link>): Map<Way, List<AppInfo>> {
        val by = apps.groupBy { wayOf(it, backup(it.pkg), links(it.pkg)) }
        return Way.entries.mapNotNull { w -> by[w]?.let { w to it } }.toMap()
    }
}
