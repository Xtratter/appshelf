// Copied from github.com/Xtratter/android-ui-kit (v1.1) — edit there and re-run install.sh
package io.github.xtratter.uikit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * A card / bar / window surface. Expressive ([M3.expressive]): a flat tonal fill — the default [M3.card] becomes
 * [M3.surfaceContainer], any mostly-opaque fill becomes [M3.surfaceContainerHigh]. Otherwise — "glass": the fill,
 * a soft highlight on top and a light edge. [tonal] = false keeps the fill exactly as given (e.g. a user-set alpha).
 */
class M3Surface(ctx: Context, radiusDp: Float, private val fill: Int = M3.card, private val tonal: Boolean = true) : Drawable() {
    private val radius = M3.dp(ctx, radiusDp)
    private val d = ctx.resources.displayMetrics.density
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val hiP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = d }
    private val r = RectF()

    override fun onBoundsChange(b: Rect) {
        r.set(b)
        r.inset(d / 2, d / 2)
        hiP.shader = LinearGradient(0f, r.top, 0f, r.top + minOf(r.height(), 90 * d),
            if (M3.light) 0x66FFFFFF else 0x16FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        val edge = if (M3.light) intArrayOf(0xFFFFFFFF.toInt(), 0x12000000, 0x12000000, 0x1F000000)
        else intArrayOf(0x70FFFFFF, 0x12FFFFFF, 0x12FFFFFF, 0x3DFFFFFF)
        edgeP.shader = LinearGradient(r.left, r.top, r.right, r.bottom, edge, floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(c: Canvas) {
        if (M3.expressive && tonal) {
            fillP.color = when {
                fill == M3.card -> M3.surfaceContainer
                (fill ushr 24) >= 0x80 -> M3.surfaceContainerHigh
                else -> fill
            }
            c.drawRoundRect(r, radius, radius, fillP)
            return
        }
        c.drawRoundRect(r, radius, radius, fillP)
        c.drawRoundRect(r, radius, radius, hiP)
        c.drawRoundRect(r, radius, radius, edgeP)
    }

    override fun getOutline(outline: Outline) = outline.setRoundRect(bounds, minOf(radius, bounds.height() / 2f, bounds.width() / 2f))
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * Window background: [M3.base] with soft blobs of the accent colours ([M3.aurora]) — they show through
 * translucent surfaces. Drawn once into a quarter-size bitmap, then only scaled: almost free per frame.
 * Use `window.setBackgroundDrawable(if (M3.aurora) M3Background() else ColorDrawable(M3.base))`.
 */
class M3Background : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var bmp: Bitmap? = null

    override fun onBoundsChange(b: Rect) {
        if (b.isEmpty) return
        val w = b.width() / 4f
        val h = b.height() / 4f
        val out = Bitmap.createBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(M3.base)
        val (c1, c2, c3) = M3.auroraColors
        val k = M3.auroraStrength
        fun blob(x: Float, y: Float, r: Float, color: Int, a: Float) {
            p.shader = RadialGradient(x, y, r, M3.withAlpha(color, a), M3.withAlpha(color, 0f), Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r, p)
        }
        blob(w * 0.05f, h * 0.08f, w * 0.95f, c1, 0.42f * k)
        blob(w * 1.0f, h * 0.38f, w * 0.85f, c2, 0.30f * k)
        blob(w * 0.1f, h * 0.78f, w * 0.9f, c3, 0.22f * k)
        blob(w * 0.9f, h * 1.02f, w * 0.7f, c1, 0.25f * k)
        bmp = out
    }

    override fun draw(c: Canvas) {
        val b = bmp
        if (b == null) c.drawColor(M3.base) else c.drawBitmap(b, null, bounds, bmpPaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
