package io.github.xtratter.appshelf

/** Вернуться в главный поток; если экран за это время закрылся (поворот, выход) — ничего не делать: ни окон, ни тостов на мёртвом экране. */
internal fun MainActivity.ui(block: () -> Unit) = runOnUiThread { if (!isDestroyed) block() }

/** Экран, на котором можно показывать окна: [a], если он жив, иначе открытый сейчас (после поворота это уже новый экземпляр); null — экрана нет. */
internal fun liveHost(a: MainActivity): MainActivity? =
    a.takeIf { !it.isDestroyed } ?: MainActivity.current?.get()?.takeIf { !it.isDestroyed }
