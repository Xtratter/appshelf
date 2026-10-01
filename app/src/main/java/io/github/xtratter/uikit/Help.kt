// Copied from github.com/Xtratter/android-ui-kit (v1.1) — edit there and re-run install.sh
package io.github.xtratter.uikit

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/**
 * Long-press help: holding a finger on a button shows a bubble next to it with the button's name and what it does.
 * Shown above the view (below if there is no room), hides on a tap elsewhere or after [timeoutMs].
 *
 * Usage: `Help.attach(button, R.string.title, R.string.text)`; colours via [style] (read every time a bubble opens,
 * so a lambda that returns the current theme colours keeps bubbles in sync with theme changes).
 */
object Help {
    class Style(
        val background: Int = 0xF0202228.toInt(),
        val border: Int = 0x66A8C7FA,
        val title: Int = 0xFFA8C7FA.toInt(),
        val text: Int = 0xFFE6E6EA.toInt(),
        val cornerDp: Float = 20f,
    )

    /** Default: colours of the current [M3] theme. */
    var style: () -> Style = { Style(M3.mix(M3.surface, M3.primary, 0.14f), M3.withAlpha(M3.primary, 0.4f), M3.primary, M3.TEXT) }
    var timeoutMs = 5000L
    /** Called when a bubble appears — e.g. `{ Haptics.play(Haptics.Kind.TICK) }`. */
    var onShow: (() -> Unit)? = { Haptics.play(Haptics.Kind.TICK) }

    private var shown: PopupWindow? = null

    /** Help on long-press of [v]; replaces any other long-click listener of the view. */
    fun attach(v: View, title: String, text: String) {
        v.setOnLongClickListener { show(it, title, text); true }
    }

    fun attach(v: View, title: Int, text: Int) = attach(v, v.context.getString(title), v.context.getString(text))

    fun dismiss() { shown?.dismiss(); shown = null }

    fun show(anchor: View, title: String, text: String) {
        dismiss()
        val ctx = anchor.context
        val st = style()
        val dp = ctx.resources.displayMetrics.density
        fun px(x: Float) = (x * dp).toInt()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16f), px(12f), px(16f), px(14f))
            background = GradientDrawable().apply {
                cornerRadius = st.cornerDp * dp; setColor(st.background)
                if (Color.alpha(st.border) > 0) setStroke(px(1f).coerceAtLeast(1), st.border)
            }
            addView(TextView(ctx).apply {
                this.text = title; textSize = 15f; setTextColor(st.title)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })
            addView(TextView(ctx).apply {
                this.text = text; textSize = 14f; setTextColor(st.text); setLineSpacing(0f, 1.15f)
                setPadding(0, px(4f), 0, 0)
            })
        }
        val maxW = (anchor.rootView.width - px(32f)).coerceAtMost(px(360f))
        box.measure(View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED)
        val pw = PopupWindow(box, box.measuredWidth, ViewGroup.LayoutParams.WRAP_CONTENT, false).apply {
            setBackgroundDrawable(ColorDrawable(0))
            isOutsideTouchable = true
            elevation = 10 * dp
            animationStyle = android.R.style.Animation_Dialog
        }
        // above the view, or below it if there is no room; centred on it horizontally, inside the screen
        val at = IntArray(2); anchor.getLocationOnScreen(at)
        val screenW = anchor.rootView.width
        val x = (at[0] + anchor.width / 2 - box.measuredWidth / 2).coerceIn(px(16f), (screenW - box.measuredWidth - px(16f)).coerceAtLeast(px(16f)))
        val above = at[1] - box.measuredHeight - px(10f)
        val y = if (above > px(40f)) above else at[1] + anchor.height + px(10f)
        pw.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
        shown = pw
        onShow?.invoke()
        anchor.postDelayed({ if (shown === pw) dismiss() }, timeoutMs)
    }
}
