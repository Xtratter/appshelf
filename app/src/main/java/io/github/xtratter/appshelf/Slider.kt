package io.github.xtratter.appshelf

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Ползунок в стиле Material 3 Expressive: толстая дорожка (активная часть — цвет акцента, остальная — тональная),
 * вертикальная ручка-полоска с зазором вокруг, точка в конце дорожки; при нажатии ручка сужается.
 * Каждые [step] — тик вибрации; [onChange] — пока тянут, [onCommit] — когда отпустили.
 */
@SuppressLint("ViewConstructor")
class Slider(ctx: Context, private val max: Int, value: Int, private val step: Int = 5,
             private val onChange: (Int) -> Unit = {}, private val onCommit: (Int) -> Unit) : View(ctx) {
    var value = value.coerceIn(0, max); private set
    private val dp = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var pressed = false
    private var lastTick = this.value / step

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (44 * dp).toInt())

    override fun onDraw(c: Canvas) {
        val pad = 4 * dp
        val left = pad; val right = width - pad
        val cy = height / 2f
        val track = 16 * dp
        val handleW = (if (pressed) 2f else 4f) * dp
        val gap = 6 * dp
        val x = left + (right - left) * value / max.toFloat()
        // активная часть — до ручки, со скруглением слева
        if (x - gap - handleW / 2 > left) {
            p.color = Ui.primary
            r.set(left, cy - track / 2, x - gap - handleW / 2, cy + track / 2)
            c.drawRoundRect(r, track / 2, track / 2, p)
            c.drawRect(r.right - track / 2, r.top, r.right, r.bottom, p)   // у ручки — прямой край
            p.color = Ui.primary
            c.drawRoundRect(RectF(r.right - 4 * dp, r.top, r.right, r.bottom), 2 * dp, 2 * dp, p)
        }
        // неактивная часть — после ручки
        if (x + gap + handleW / 2 < right) {
            p.color = Ui.secondaryContainer.let { if ((it ushr 24) < 0x80) Ui.withAlpha(it, 0.9f) else it }
            r.set(x + gap + handleW / 2, cy - track / 2, right, cy + track / 2)
            c.drawRoundRect(r, track / 2, track / 2, p)
            // точка в конце дорожки
            p.color = Ui.primary
            c.drawCircle(right - track / 2, cy, 2 * dp, p)
        }
        // ручка — вертикальная полоска
        p.color = Ui.primary
        r.set(x - handleW / 2, cy - 22 * dp, x + handleW / 2, cy + 22 * dp)
        c.drawRoundRect(r, handleW / 2, handleW / 2, p)
    }

    private fun setFromX(x: Float) {
        val pad = 4 * dp
        val v = (((x - pad) / (width - 2 * pad)) * max).toInt().coerceIn(0, max)
        if (v == value) return
        value = v
        if (v / step != lastTick) { lastTick = v / step; Haptics.play(Haptics.Kind.TICK) }
        onChange(v)
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent?.requestDisallowInterceptTouchEvent(true); pressed = true; setFromX(e.x); invalidate() }
            MotionEvent.ACTION_MOVE -> setFromX(e.x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { pressed = false; invalidate(); onCommit(value) }
        }
        return true
    }

    companion object {
        /** Строка настройки: название и значение сверху, ползунок под ними. */
        fun row(ctx: Context, title: String, max: Int, value: Int, format: (Int) -> String, onCommit: (Int) -> Unit): View {
            val dp = ctx.resources.displayMetrics.density
            val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(0, (8 * dp).toInt(), 0, 0) }
            val head = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
            head.addView(TextView(ctx).apply { text = title; textSize = 16f; setTextColor(Ui.TEXT) }, LinearLayout.LayoutParams(0, -2, 1f))
            val v = TextView(ctx).apply { text = format(value); textSize = 15f; typeface = Ui.medium; setTextColor(Ui.primary) }
            head.addView(v)
            box.addView(head)
            box.addView(Slider(ctx, max, value, onChange = { v.text = format(it) }, onCommit = onCommit))
            return box
        }
    }
}
