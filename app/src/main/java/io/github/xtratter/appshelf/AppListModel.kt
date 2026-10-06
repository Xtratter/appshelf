package io.github.xtratter.appshelf

/**
 * Кэши для перерисовки списка: render() вызывается на каждый символ поиска и каждое нажатие на фильтр,
 * поэтому сортировка, строки для поиска и проверки «есть обновление / нет ссылки» не должны считаться заново.
 */
internal class ListModel {
    private var base: List<AppInfo>? = null
    private var sorted: List<AppInfo> = emptyList()
    private val haystack = HashMap<String, String>()

    /**
     * [apps] по алфавиту. Сортировка запоминается для того же списка (тот же объект); отфильтрованный
     * потом список остаётся отсортированным, поэтому фильтры сортировать заново не нужно.
     */
    fun sorted(apps: List<AppInfo>): List<AppInfo> {
        if (apps !== base) {
            sorted = ListFile.sorted(apps)
            base = apps
            haystack.clear()
        }
        return sorted
    }

    /** Подходит ли [a] под запрос [q] (уже в нижнем регистре): название, пакет или заметка ([note] — лениво). */
    fun matches(a: AppInfo, q: String, note: () -> String): Boolean {
        val h = haystack.getOrPut(a.pkg) { a.label.lowercase() + "\u0000" + a.pkg.lowercase() }
        return q in h || q in note().lowercase()
    }
}

/** Результаты проверок для одной перерисовки: каждое приложение проверяется не больше одного раза. */
internal class RenderMemo(
    private val update: (AppInfo) -> Updates.Release?,
    private val noLink: (AppInfo) -> Boolean,
) {
    private val upd = HashMap<String, Updates.Release?>()
    private val link = HashMap<String, Boolean>()

    fun update(a: AppInfo): Updates.Release? {
        if (!upd.containsKey(a.pkg)) upd[a.pkg] = update.invoke(a)
        return upd[a.pkg]
    }

    fun needsLink(a: AppInfo): Boolean = a.source == Source.APK && link.getOrPut(a.pkg) { noLink(a) }
}
