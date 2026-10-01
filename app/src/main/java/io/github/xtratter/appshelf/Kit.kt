package io.github.xtratter.appshelf

import android.content.Context
import io.github.xtratter.uikit.Haptics
import io.github.xtratter.uikit.Help

/** Общие файлы из android-ui-kit (EdgeBlur, Haptics, Help) — настройка под AppShelf: хранение, цвета, анимация. */
object Kit {
    fun init(ctx: Context) {
        Haptics.init(ctx, ctx.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE))
        Haptics.onTouch = { v, e -> if (Ui.EXPRESSIVE) Expressive.morph(v, e) }
        Help.style = { Help.Style(Ui.mix(Ui.surface, Ui.primary, 0.14f), Ui.withAlpha(Ui.primary, 0.4f), Ui.primary, Ui.TEXT) }
    }
}

val Haptics.Level.title get() = when (this) {
    Haptics.Level.OFF -> R.string.hap_off
    Haptics.Level.LIGHT -> R.string.hap_light
    Haptics.Level.MEDIUM -> R.string.hap_medium
    Haptics.Level.STRONG -> R.string.hap_strong
}

val Haptics.Engine.title get() = when (this) {
    Haptics.Engine.AUTO -> R.string.hap_eng_auto
    Haptics.Engine.EFFECTS -> R.string.hap_eng_effects
    Haptics.Engine.PRIMITIVES -> R.string.hap_eng_primitives
    Haptics.Engine.SIMPLE -> R.string.hap_eng_simple
}
