package io.github.xtratter.appshelf

/** Тема оформления. [SYSTEM] — стандартная тёмная или светлая, как в настройках Android. */
enum class Theme(val title: Int) {
    STANDARD(R.string.th_standard),
    SYSTEM(R.string.th_system),
    AMOLED(R.string.th_amoled),
    LIGHT(R.string.th_light),
    GRAPHITE(R.string.th_graphite);

    companion object {
        /** Тема по умолчанию. */
        val DEFAULT: Theme get() = SYSTEM   // тёмная или светлая — как в настройках Android
    }
}
