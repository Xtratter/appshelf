package io.github.xtratter.appshelf

import android.app.AlertDialog
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import io.github.xtratter.uikit.M3
import io.github.xtratter.uikit.M3Background
import io.github.xtratter.uikit.M3Dialog
import io.github.xtratter.uikit.M3Surface

/**
 * Цвета, шрифты и оформление AppShelf. Сам Material 3 Expressive (схема цветов, поверхности, окна) — в общем
 * наборе android-ui-kit ([M3]); здесь — темы AppShelf поверх него и короткие имена для остального кода.
 */
object Ui {
    /** Material 3 Expressive: тональные поверхности, крупные формы. */
    const val EXPRESSIVE = true
    /** Высота кнопок: крупнее в Expressive — по исследованию Google по ним быстрее попадают. */
    val buttonDp get() = if (EXPRESSIVE) 52f else 44f

    val primary get() = M3.primary
    val secondary get() = M3.secondary
    val tertiary get() = M3.tertiary
    val base get() = M3.base
    val light get() = M3.light
    val amoled get() = M3.amoled
    val aurora get() = M3.aurora
    val auroraColors get() = M3.auroraColors
    val auroraStrength get() = M3.auroraStrength
    val translucent get() = M3.translucent
    val primaryContainer get() = M3.primaryContainer
    val onPrimaryContainer get() = M3.onPrimaryContainer
    val secondaryContainer get() = M3.secondaryContainer
    val onSecondaryContainer get() = M3.onSecondaryContainer
    val surfaceContainer get() = M3.surfaceContainer
    val surfaceContainerHigh get() = M3.surfaceContainerHigh
    val TEXT get() = M3.TEXT
    val TEXT2 get() = M3.TEXT2
    val TEXT3 get() = M3.TEXT3
    val ON_ACCENT get() = M3.ON_ACCENT
    val WARN get() = M3.WARN
    val HOT get() = M3.HOT
    val OK get() = M3.OK
    val TRACK get() = M3.TRACK
    val card get() = M3.card
    val dialogBlur get() = M3.dialogBlur
    val dialogSolid get() = M3.dialogSolid
    val surface get() = M3.surface
    val hintFill get() = M3.hintFill
    val hintText get() = M3.hintText
    fun ink(alpha: Int) = M3.ink(alpha)

    val medium: Typeface get() = M3.medium
    val bold: Typeface get() = M3.bold
    val heavy: Typeface get() = M3.heavy
    val regular: Typeface get() = M3.regular

    /** Какая тема AppShelf применена сейчас (null — ещё никакая). */
    var theme: Theme? = null; private set

    private fun mode(t: Theme) = when (t) {
        Theme.SYSTEM -> M3.Mode.SYSTEM
        Theme.LIGHT -> M3.Mode.LIGHT
        Theme.STANDARD -> M3.Mode.DARK
        Theme.GRAPHITE -> M3.Mode.GRAPHITE
        Theme.AMOLED -> M3.Mode.AMOLED
    }

    fun init(ctx: Context) {
        if (theme == null) apply(ctx, Prefs(ctx).theme())
    }

    /** Тема [t] с учётом системного тёмного режима (для [Theme.SYSTEM]). */
    fun resolve(ctx: Context, t: Theme): Theme = when (M3.resolve(ctx, mode(t))) {
        M3.Mode.LIGHT -> Theme.LIGHT
        M3.Mode.GRAPHITE -> Theme.GRAPHITE
        M3.Mode.AMOLED -> Theme.AMOLED
        else -> Theme.STANDARD
    }

    fun isCurrent(ctx: Context, t: Theme) = theme == t && M3.isCurrent(ctx, mode(t), Prefs(ctx).translucent)

    fun apply(ctx: Context, t: Theme) {
        theme = t
        M3.apply(ctx, mode(t), Prefs(ctx).translucent)
    }

    /** Версия для строки списка: «1.12.1», длинные хвосты («-beta (123)», хеши) обрезаются до 14 знаков. */
    fun versionShort(v: String): String {
        val t = v.trim().removePrefix("v")
        return if (t.length <= 14) t else t.take(13) + "…"
    }

    fun withAlpha(color: Int, a: Float) = M3.withAlpha(color, a)
    fun mix(a: Int, b: Int, t: Float) = M3.mix(a, b, t)
    fun dp(ctx: Context, v: Float) = M3.dp(ctx, v)
    fun sp(ctx: Context, v: Float) = M3.sp(ctx, v)
    fun textPaint(ctx: Context, sizeSp: Float, face: Typeface = regular, color: Int = TEXT) = M3.textPaint(ctx, sizeSp, face, color)
    fun ellipsize(p: Paint, s: String, width: Float) = M3.ellipsize(p, s, width)
    fun ripple(ctx: Context, radiusDp: Float, insetH: Float = 0f, insetV: Float = 0f) = M3.ripple(ctx, radiusDp, insetH, insetV)
    fun pill(ctx: Context, fill: Int, stroke: Int = 0, radiusDp: Float = 100f) = M3.pill(ctx, fill, stroke, radiusDp)

    val openDialogs get() = M3Dialog.count
    fun forgetDialogs() = M3Dialog.forget()
    var onDialogsClosed: (() -> Unit)?
        get() = M3Dialog.onAllClosed
        set(v) { M3Dialog.onAllClosed = v }

    /** Оформить окно: тональный фон, размытие экрана позади (Android 12+), щелчки, мягкие края. */
    fun glassDialog(d: AlertDialog) = M3Dialog.style(d)
}

/** Поверхность карточек, панелей и окон — из набора ([M3Surface]). */
typealias GlassDrawable = M3Surface
/** Фон окна с мягкими цветными пятнами — из набора ([M3Background]). */
typealias AuroraDrawable = M3Background
