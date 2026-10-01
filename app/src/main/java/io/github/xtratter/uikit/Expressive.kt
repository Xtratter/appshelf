// Copied from github.com/Xtratter/android-ui-kit (v1.1) — edit there and re-run install.sh
package io.github.xtratter.uikit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import java.util.WeakHashMap

/**
 * Material 3 Expressive shapes: buttons springily "squash" from a pill into a rounded rectangle when pressed;
 * list rows join into groups with big outer and small inner corners.
 * Press animation for every `Haptics.onClick` view: `Haptics.onTouch = { v, e -> Expressive.morph(v, e) }`.
 */
object Expressive {
    private val running = WeakHashMap<View, ValueAnimator>()

    /** Pressed: corner radius shrinks (pill → rounded rectangle); released — springs back. Needs a GradientDrawable background. */
    fun morph(v: View, e: MotionEvent) {
        val bg = v.background as? GradientDrawable ?: return
        val full = v.height / 2f
        if (full <= 0f) return
        fun to(target: Float, ms: Long, overshoot: Boolean) {
            running[v]?.cancel()
            val from = bg.cornerRadius.coerceAtMost(full)
            running[v] = ValueAnimator.ofFloat(from, target).apply {
                duration = ms
                interpolator = if (overshoot) OvershootInterpolator(2.2f) else DecelerateInterpolator()
                addUpdateListener { bg.cornerRadius = it.animatedValue as Float }
                start()
            }
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> to(full * 0.35f, 120, false)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> to(full, 420, true)
        }
    }

    /** A row in a group: [top]/[bottom] — first/last in the group (big corners), inside — small ones. */
    fun groupPath(path: Path, r: RectF, top: Boolean, bottom: Boolean, big: Float, small: Float) {
        val t = if (top) big else small
        val b = if (bottom) big else small
        path.reset()
        path.addRoundRect(r, floatArrayOf(t, t, t, t, b, b, b, b), Path.Direction.CW)
    }

    /** A pill in a tonal container colour. */
    fun pill(ctx: Context, color: Int, radiusDp: Float) = GradientDrawable().apply {
        cornerRadius = M3.dp(ctx, radiusDp)
        setColor(color)
    }
}
