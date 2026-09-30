package io.github.xtratter.appshelf

/** Тема оформления. [SYSTEM] — стандартная тёмная или светлая, как в настройках Android. */
enum class Theme(val title: Int) {
    /** «Жидкое стекло», как в iOS 26 (линза и блики — на Android 13+); тема по умолчанию. */
    LIQUID(R.string.th_liquid),
    STANDARD(R.string.th_standard),
    SYSTEM(R.string.th_system),
    AMOLED(R.string.th_amoled),
    LIGHT(R.string.th_light),
    GRAPHITE(R.string.th_graphite);

    companion object {
        /** По умолчанию — «жидкое стекло»; где его шейдеры недоступны (до Android 13) — стандартная. */
        val DEFAULT: Theme get() = if (android.os.Build.VERSION.SDK_INT >= 33) LIQUID else STANDARD
    }
}

/** Шрифт интерфейса: [style] — наложение на тему (android:fontFamily), чтобы шрифт получили все надписи. */
enum class AppFont(val title: Int, val style: Int) {
    /** Cascadia Mono (Microsoft, OFL) — моноширинный, похож на Consolas; по умолчанию. */
    CASCADIA(R.string.font_cascadia, R.style.Font_Cascadia),
    SYSTEM(R.string.font_system, R.style.Font_System),
    MONO(R.string.font_mono, R.style.Font_Mono),
}
