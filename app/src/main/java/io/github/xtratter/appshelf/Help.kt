package io.github.xtratter.appshelf

import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/**
 * ЭКСПЕРИМЕНТ (ветка experiment/long-press-help): интерактивная справка — удержание пальца на кнопке или пункте
 * показывает рядом стеклянную подсказку: что это и что будет при нажатии.
 */
object Help {
    private var shown: PopupWindow? = null

    /** Подсказка по удержанию на [v]: [title] — название, [text] — что делает. */
    fun attach(v: View, title: String, text: String) {
        v.setOnLongClickListener { show(it, title, text); true }
    }

    fun attach(v: View, title: Int, text: Int) = attach(v, v.context.getString(title), v.context.getString(text))

    fun show(anchor: View, title: String, text: String) {
        shown?.dismiss()
        val ctx = anchor.context
        val dp = ctx.resources.displayMetrics.density
        fun px(x: Float) = (x * dp).toInt()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16f), px(12f), px(16f), px(14f))
            background = Ui.pill(ctx, Ui.mix(Ui.surface, Ui.primary, 0.14f), Ui.withAlpha(Ui.primary, 0.4f), 20f)
            addView(TextView(ctx).apply {
                this.text = title; textSize = 15f; typeface = Ui.medium; setTextColor(Ui.primary)
            })
            addView(TextView(ctx).apply {
                this.text = text; textSize = 14f; setTextColor(Ui.TEXT); setLineSpacing(0f, 1.15f)
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
        // над кнопкой, а если сверху нет места — под ней; по горизонтали — центр кнопки, не вылезая за край
        val at = IntArray(2); anchor.getLocationOnScreen(at)
        val screenW = anchor.rootView.width
        val x = (at[0] + anchor.width / 2 - box.measuredWidth / 2).coerceIn(px(16f), screenW - box.measuredWidth - px(16f))
        val above = at[1] - box.measuredHeight - px(10f)
        val y = if (above > px(40f)) above else at[1] + anchor.height + px(10f)
        pw.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
        shown = pw
        Haptics.play(Haptics.Kind.TICK)
        anchor.postDelayed({ if (shown === pw) { pw.dismiss(); shown = null } }, 5000)
    }
}
