package io.github.xtratter.appshelf

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * ЭКСПЕРИМЕНТ: Material 3 Expressive (design.google/library/expressive-material-design-google-research) —
 * форма как выразительное средство: кнопки при нажатии пружинисто «сплющиваются» из капсулы в скруглённый
 * прямоугольник, строки списка собираются в группы с крупными внешними и мелкими внутренними углами.
 */
object Expressive {
    /** Нажатие: скругление кнопки уменьшается (капсула → скруглённый прямоугольник), отпустили — пружинит назад. */
    fun morph(v: View, e: MotionEvent) {
        val bg = v.background as? GradientDrawable ?: return
        val full = v.height / 2f
        if (full <= 0f) return
        val pressed = full * 0.35f
        val tag = v.getTag(R.id.title) as? ValueAnimator
        fun to(target: Float, ms: Long, overshoot: Boolean) {
            tag?.cancel()
            val from = bg.cornerRadius.coerceAtMost(full)
            ValueAnimator.ofFloat(from, target).apply {
                duration = ms
                interpolator = if (overshoot) OvershootInterpolator(2.2f) else DecelerateInterpolator()
                addUpdateListener { bg.cornerRadius = it.animatedValue as Float }
                v.setTag(R.id.title, this)
                start()
            }
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> to(pressed, 120, false)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> to(full, 420, true)
        }
    }

    /** Карточка строки в группе: [top]/[bottom] — первая/последняя в группе (крупные углы), внутри — мелкие. */
    fun groupPath(path: Path, r: RectF, top: Boolean, bottom: Boolean, big: Float, small: Float) {
        val t = if (top) big else small
        val b = if (bottom) big else small
        path.reset()
        path.addRoundRect(r, floatArrayOf(t, t, t, t, b, b, b, b), Path.Direction.CW)
    }

    /** Кнопка-пилюля в тональном контейнере. */
    fun pill(ctx: Context, color: Int, radiusDp: Float) = GradientDrawable().apply {
        cornerRadius = Ui.dp(ctx, radiusDp)
        setColor(color)
    }
}
