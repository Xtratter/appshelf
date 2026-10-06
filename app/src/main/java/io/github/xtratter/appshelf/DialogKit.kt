package io.github.xtratter.appshelf

import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Общие кирпичики окон настроек: подписи, карточки, поля, кнопки-«таблетки». [busy] — идёт сетевое действие, кнопки не нажимаются. */
internal class DialogKit(val a: MainActivity, private val busy: () -> Boolean = { false }) {
    private val dp = a.resources.displayMetrics.density
    fun px(v: Float) = (v * dp).toInt()

        fun label(text: String, size: Float = 13.5f, color: Int = Ui.TEXT2, top: Float = 0f) = TextView(a).apply {
            this.text = text; textSize = size; setTextColor(color); setLineSpacing(0f, 1.1f)
            setPadding(0, px(top), 0, px(4f))
        }
        fun card() = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = GlassDrawable(a, 18f)
            setPadding(px(14f), px(12f), px(14f), px(12f))
        }
        fun field(hint: Int, value: String, type: Int) = EditText(a).apply {
            setHint(hint); setText(value)
            setHintTextColor(Ui.TEXT3); setTextColor(Ui.TEXT)
            textSize = 15f; maxLines = 1; inputType = type
            background = GlassDrawable(a, 16f, Ui.ink(0x10))
            setPadding(px(14f), px(10f), px(14f), px(10f))
        }
        fun pill(text: String, filled: Boolean = false, onClick: () -> Unit) = TextView(a).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 14.5f
            typeface = Ui.medium
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(11, 15, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            setPadding(px(12f), 0, px(12f), 0)
            setTextColor(if (filled) Ui.ON_ACCENT else Ui.primary)
            background = if (filled) Ui.pill(a, Ui.primary)
            else Ui.pill(a, Ui.withAlpha(Ui.primary, 0.12f), Ui.withAlpha(Ui.primary, 0.3f))
            foreground = Ui.ripple(a, 100f)
            setOnClickListener { if (!busy()) onClick() }
        }
        fun gap(h: Float) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(h) }
}
