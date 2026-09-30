package io.github.xtratter.appshelf

/** Тема оформления. [SYSTEM] — стандартная тёмная или светлая, как в настройках Android. */
enum class Theme(val title: Int) {
    STANDARD(R.string.th_standard),
    SYSTEM(R.string.th_system),
    AMOLED(R.string.th_amoled),
    LIGHT(R.string.th_light),
    GRAPHITE(R.string.th_graphite);

    companion object {
        /** Тема по умолчанию; «жидкое стекло» — отдельная галочка поверх любой темы ([Prefs.liquidGlass]). */
        val DEFAULT: Theme get() = STANDARD
    }
}

/** Шрифт интерфейса: [style] — наложение на тему (android:fontFamily), чтобы шрифт получили все надписи. */
enum class AppFont(val title: Int, val style: Int) {
    /** Cascadia Mono (Microsoft, OFL) — моноширинный, похож на Consolas; по умолчанию. */
    CASCADIA(R.string.font_cascadia, R.style.Font_Cascadia),
    SYSTEM(R.string.font_system, R.style.Font_System),
    MONO(R.string.font_mono, R.style.Font_Mono),
}
