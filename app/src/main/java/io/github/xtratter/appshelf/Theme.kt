package io.github.xtratter.appshelf

/**
 * Тема оформления, в порядке списка в окне «Тема». [SYSTEM] — тёмная или светлая, как в настройках Android;
 * [STANDARD] — тёмная (имя в настройках прежнее, чтобы выбранная тема сохранилась).
 */
enum class Theme(val title: Int) {
    SYSTEM(R.string.th_system),
    LIGHT(R.string.th_light),
    STANDARD(R.string.th_standard),
    GRAPHITE(R.string.th_graphite),
    AMOLED(R.string.th_amoled);

    companion object {
        /** Тема по умолчанию. */
        val DEFAULT: Theme get() = SYSTEM   // тёмная или светлая — как в настройках Android
    }
}
