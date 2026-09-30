package io.github.xtratter.appshelf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View

/** Что показать в строке: всё уже переведено в текст, чтобы при прокрутке ничего не считать. */
class Row(
    val app: AppInfo,
    val source: String,
    val date: String,
    /** Режим восстановления: null — обычный список, true/false — установлено ли сейчас. */
    val installed: Boolean? = null,
    /** Не входит в сохраняемый список (пользователь исключил). */
    val excluded: Boolean = false,
    /** Режим восстановления: откуда поставить по ссылке («GitHub»); null — ссылок нет, из магазина. */
    val link: String? = null,
)

/**
 * Строка списка — стеклянная карточка: значок, название и дата, пакет, плашка источника.
 * Рисуется целиком в onDraw — без вложенных View, поэтому длинный список прокручивается плавно.
 */
class AppItemView(ctx: Context) : View(ctx) {
    private fun dp(v: Float) = Ui.dp(context, v)
    private val padH = dp(12f)
    private val card = RectF()
    private val glass = GlassDrawable(ctx, 22f)
    private val titleP = Ui.textPaint(ctx, 16f, Ui.medium, Ui.TEXT)
    private val pkgP = Ui.textPaint(ctx, 12.5f, Ui.regular, Ui.TEXT2)
    private val dateP = Ui.textPaint(ctx, 12f, Ui.regular, Ui.TEXT3).apply { textAlign = Paint.Align.RIGHT }
    private val pillP = Ui.textPaint(ctx, 11.5f, Ui.medium)
    private val letterP = Ui.textPaint(ctx, 18f, Ui.medium, Ui.TEXT).apply { textAlign = Paint.Align.CENTER }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpP = Paint(Paint.FILTER_BITMAP_FLAG)
    private val iconSize = dp(44f).toInt()
    private val dst = Rect()
    private val r = RectF()

    var row: Row? = null
        set(v) { field = v; invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(84f).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        card.set(padH, dp(4f), w - padH, h - dp(4f))
        glass.setBounds(card.left.toInt(), card.top.toInt(), card.right.toInt(), card.bottom.toInt())
    }

    override fun onDraw(c: Canvas) {
        val rw = row ?: return
        val a = rw.app
        glass.draw(c)

        // значок или буква на цветном кружке (для приложений, которых на телефоне нет)
        val left = card.left + dp(14f)
        val top = card.centerY() - iconSize / 2f
        val icon = if (rw.installed == false) null else Icons.get(context, a.pkg, iconSize) { invalidate() }
        if (icon != null) {
            dst.set(left.toInt(), top.toInt(), left.toInt() + iconSize, top.toInt() + iconSize)
            c.drawBitmap(icon, null, dst, bmpP)
        } else {
            fill.color = Ui.withAlpha(a.source.color, if (rw.installed == false) 0.35f else 0.55f)
            c.drawCircle(left + iconSize / 2f, card.centerY(), iconSize / 2f, fill)
            letterP.color = Ui.TEXT
            c.drawText(a.label.trim().take(1).uppercase(), left + iconSize / 2f,
                card.centerY() - (letterP.ascent() + letterP.descent()) / 2, letterP)
        }

        val x = left + iconSize + dp(14f)
        val right = card.right - dp(16f)

        // справа сверху: дата установки или (при восстановлении) статус
        val status: String
        when (rw.installed) {
            null -> { status = rw.date; dateP.color = Ui.TEXT3 }
            true -> { status = context.getString(R.string.st_installed); dateP.color = Ui.OK }
            false -> { status = rw.link?.let { "$it ›" } ?: context.getString(R.string.st_missing); dateP.color = Ui.primary }
        }
        val y1 = card.top + dp(26f)
        c.drawText(status, right, y1, dateP)
        val statusW = if (status.isEmpty()) 0f else dateP.measureText(status) + dp(10f)

        titleP.color = if (rw.installed == false) Ui.TEXT2 else Ui.TEXT
        c.drawText(Ui.ellipsize(titleP, a.label, right - x - statusW), x, y1, titleP)
        c.drawText(Ui.ellipsize(pkgP, a.pkg, right - x), x, y1 + dp(19f), pkgP)

        // плашка источника: цветная точка и название
        val pillTop = y1 + dp(28f)
        val ph = dp(20f)
        val text = Ui.ellipsize(pillP, rw.source, right - x - dp(28f))
        val pw = pillP.measureText(text) + dp(26f)
        r.set(x, pillTop, x + pw, pillTop + ph)
        fill.color = Ui.withAlpha(a.source.color, if (Ui.light) 0.14f else 0.20f)
        c.drawRoundRect(r, ph / 2, ph / 2, fill)
        fill.color = a.source.color
        c.drawCircle(x + dp(10f), r.centerY(), dp(3.5f), fill)
        pillP.color = Ui.TEXT
        c.drawText(text, x + dp(18f), r.centerY() - (pillP.ascent() + pillP.descent()) / 2, pillP)
    }
}
