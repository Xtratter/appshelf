package io.github.xtratter.appshelf

/** Тема оформления. [SYSTEM] — стандартная тёмная или светлая, как в настройках Android. */
enum class Theme(val title: Int) {
    STANDARD(R.string.th_standard),
    SYSTEM(R.string.th_system),
    AMOLED(R.string.th_amoled),
    LIGHT(R.string.th_light),
    GRAPHITE(R.string.th_graphite),
    /** Экспериментальная: «жидкое стекло» как в iOS 26 (линза и блики — на Android 13+). */
    LIQUID(R.string.th_liquid),
}
