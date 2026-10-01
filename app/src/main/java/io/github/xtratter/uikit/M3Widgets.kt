// Copied from github.com/Xtratter/android-ui-kit (v1.1) — edit there and re-run install.sh
package io.github.xtratter.uikit

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/** Ready-made M3 Expressive widgets on plain Views (colours from [M3], clicks with [Haptics], squash with [Expressive]). */
object M3Widgets {
    enum class ButtonKind { FILLED, TONAL, OUTLINED, DANGER }

    /** A pill button; put it into a LayoutParams of height `M3.dp(ctx, 52f)` (Expressive buttons are taller). */
    fun button(ctx: Context, text: CharSequence, kind: ButtonKind = ButtonKind.FILLED, onClick: () -> Unit) = TextView(ctx).apply {
        this.text = text
        textSize = 15f; typeface = M3.medium; gravity = Gravity.CENTER
        val dp = ctx.resources.displayMetrics.density
        setPadding((20 * dp).toInt(), 0, (20 * dp).toInt(), 0)
        when (kind) {
            ButtonKind.FILLED -> { setTextColor(M3.ON_ACCENT); background = M3.pill(ctx, M3.primary) }
            ButtonKind.TONAL -> { setTextColor(M3.onSecondaryContainer); background = Expressive.pill(ctx, M3.secondaryContainer, 100f) }
            ButtonKind.OUTLINED -> { setTextColor(M3.primary); background = M3.pill(ctx, 0, M3.withAlpha(M3.primary, 0.5f)) }
            ButtonKind.DANGER -> { setTextColor(M3.HOT); background = M3.pill(ctx, M3.withAlpha(M3.HOT, 0.12f), M3.withAlpha(M3.HOT, 0.35f)) }
        }
        foreground = M3.ripple(ctx, 100f)
        setOnClickListener { onClick() }
        Haptics.onClick(this)
    }

    /** A filter chip: selected — a secondary-tone pill with a check; others — rounded rectangles; [dot] — colour mark. */
    fun chip(ctx: Context, label: CharSequence, selected: Boolean, dot: Int? = null, onClick: () -> Unit) = TextView(ctx).apply {
        val dp = ctx.resources.displayMetrics.density
        text = if (selected) "✓  $label" else label
        textSize = 13.5f; typeface = M3.medium; gravity = Gravity.CENTER
        setPadding(((if (dot != null) 12 else 16) * dp).toInt(), 0, (16 * dp).toInt(), 0)
        setTextColor(if (selected) M3.onSecondaryContainer else M3.TEXT)
        background = if (selected) Expressive.pill(ctx, M3.secondaryContainer, 100f)
        else M3.pill(ctx, M3.surfaceContainer, M3.ink(0x26), 12f)
        foreground = M3.ripple(ctx, 100f)
        if (dot != null && !selected) {
            val d = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(dot); setSize((8 * dp).toInt(), (8 * dp).toInt()) }
            setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null)
            compoundDrawablePadding = (8 * dp).toInt()
        }
        setOnClickListener { onClick() }
        Haptics.onClick(this, Haptics.Kind.TICK)
    }

    /** Accent tint for a checkbox / radio / switch. */
    fun tint(b: CompoundButton) {
        b.buttonTintList = ColorStateList.valueOf(M3.primary)
        if (b is Switch) {
            val st = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            b.thumbTintList = ColorStateList(st, intArrayOf(M3.primary, M3.TEXT3))
            b.trackTintList = ColorStateList(st, intArrayOf(M3.withAlpha(M3.primary, 0.5f), M3.ink(0x33)))
            b.buttonTintList = null
        }
    }

    /** A settings row: title on the left, a switch on the right; the whole row toggles. */
    fun switchRow(ctx: Context, title: CharSequence, checked: Boolean, onChange: (Boolean) -> Unit) = LinearLayout(ctx).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = M3.dp(ctx, 52f).toInt()
        background = M3.ripple(ctx, 16f)
        addView(TextView(ctx).apply { text = title; textSize = 16f; setTextColor(M3.TEXT) }, LinearLayout.LayoutParams(0, -2, 1f))
        val sw = Switch(ctx).apply { isChecked = checked; isClickable = false; tint(this) }
        addView(sw)
        setOnClickListener { sw.isChecked = !sw.isChecked; Haptics.play(Haptics.Kind.TICK); onChange(sw.isChecked) }
    }
}
