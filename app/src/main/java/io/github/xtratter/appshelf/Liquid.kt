package io.github.xtratter.appshelf

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewTreeObserver
import android.annotation.TargetApi

/**
 * Тема «Жидкое стекло» (как в iOS 26), Android 13+: шейдер AGSL превращает скруглённый прямоугольник в выпуклую линзу.
 * У кромки то, что под стеклом, изгибается (сэмплируем снаружи, по нормали к краю), цвета чуть расходятся
 * (хроматическая аберрация), по краю — блик от света сверху слева и отражение снизу справа.
 */
object Liquid {
    /** Содержимое под стеклом поменялось без прокрутки (новый список, фильтр, значки) — панель перезапишет его. */
    @JvmStatic var version = 0

    const val AGSL = """
uniform shader content;
uniform float2 origin;     // top-left of the glass in content coordinates
uniform float2 size;
uniform float radius;
uniform float bezel;       // width of the curved rim, px
uniform float strength;    // how far the rim bends the backdrop, px
uniform float4 tint;       // glass tint, a = amount
uniform float light;       // highlight brightness
uniform float dp;

float sdf(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 xy) {
    float2 hs = size * 0.5;
    float2 p = xy - origin - hs;
    float r = min(radius, min(hs.x, hs.y));
    float d = sdf(p, hs, r);
    if (d > 1.0) return half4(0.0);

    // normal to the nearest edge
    float e = 0.75;
    float2 n = float2(sdf(p + float2(e, 0.0), hs, r) - sdf(p - float2(e, 0.0), hs, r),
                      sdf(p + float2(0.0, e), hs, r) - sdf(p - float2(0.0, e), hs, r));
    n = n / max(length(n), 0.0001);

    // the middle stays clear; only a narrow band at the rim bends, like a thick lens:
    // content from further inside is pulled out to the edge and stretched along it
    float t = clamp(1.0 + d / bezel, 0.0, 1.0);
    float bend = pow(t, 3.0) * strength;
    float2 disp = -n * bend;
    float3 col = float3(content.eval(xy + disp * 1.18).r,
                        content.eval(xy + disp).g,
                        content.eval(xy + disp * 0.82).b);

    // livelier colors under the glass, then the tint
    float lum = dot(col, float3(0.299, 0.587, 0.114));
    col = mix(float3(lum), col, 1.2);
    col = mix(col, tint.rgb, tint.a);

    // light: a thin bright rim (strongest top-left, a reflection bottom-right) and a soft inner glow
    float2 L = normalize(float2(-0.6, -0.8));
    float k = dot(n, L);
    float spec = 0.25 + 0.75 * pow(max(k, 0.0), 1.5) + 0.6 * pow(max(-k, 0.0), 2.0);
    float rimW = 1.4 * dp;
    float rim = smoothstep(-rimW - 1.2, -rimW * 0.35, d) * (1.0 - smoothstep(-0.4, 0.7, d));
    col = mix(col, float3(1.0), clamp(rim * spec * light, 0.0, 1.0));
    float glow = smoothstep(-9.0 * dp, 0.0, d);
    col += glow * glow * 0.09 * light * (0.35 + max(k, 0.0) + 0.5 * max(-k, 0.0));

    float a = 1.0 - smoothstep(-0.75, 0.75, d);
    return half4(half3(clamp(col, 0.0, 1.0) * a), half(a));
}
"""

    /** Собирается ли шейдер на этом телефоне (проверяем один раз; не собрался — тема рисует обычное стекло). */
    val works: Boolean by lazy {
        android.os.Build.VERSION.SDK_INT >= 33 && try {
            RuntimeShader(AGSL); true
        } catch (e: Throwable) {
            android.util.Log.w("AppShelf", "liquid glass shader failed", e); false
        }
    }

    fun uniforms(s: RuntimeShader, x: Float, y: Float, w: Float, h: Float, radius: Float, dp: Float, tint: Int) {
        s.setFloatUniform("origin", x, y)
        s.setFloatUniform("size", w, h)
        s.setFloatUniform("radius", radius)
        val bezel = minOf(minOf(w, h) * 0.3f, 16 * dp)
        s.setFloatUniform("bezel", bezel)
        s.setFloatUniform("strength", bezel * 0.9f)
        s.setFloatUniform("dp", dp)
        s.setFloatUniform("tint", ((tint shr 16) and 0xFF) / 255f, ((tint shr 8) and 0xFF) / 255f,
            (tint and 0xFF) / 255f, ((tint ushr 24) and 0xFF) / 255f)
        s.setFloatUniform("light", if (Ui.light) 0.95f else 0.8f)
    }
}

/** Стеклянная карточка поверх фона окна: преломляет размытые пятна фона, которые под ней. */
@TargetApi(33)
class LiquidCard(private val dp: Float) {
    private val shader = RuntimeShader(Liquid.AGSL)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val m = Matrix()
    private val loc = IntArray(2)
    private var bmpShader: BitmapShader? = null
    private var bmpFor: android.graphics.Bitmap? = null

    /** false — рисовать нечем (фон ещё не готов или стекло не в главном окне). */
    fun draw(c: Canvas, host: View, r: RectF, radius: Float, tint: Int): Boolean {
        val bmp = Ui.backdrop ?: return false
        if (host.rootView !== Ui.liquidRoot || Ui.backdropW <= 0) return false
        if (bmpFor !== bmp) { bmpShader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP); bmpFor = bmp }
        val bs = bmpShader ?: return false
        host.getLocationInWindow(loc)
        m.setScale(Ui.backdropW / bmp.width.toFloat(), Ui.backdropH / bmp.height.toFloat())
        m.postTranslate(-loc[0].toFloat(), -loc[1].toFloat())
        bs.setLocalMatrix(m)
        shader.setInputShader("content", bs)
        Liquid.uniforms(shader, r.left, r.top, r.width(), r.height(), radius, dp, tint)
        paint.shader = shader
        c.drawRect(r.left - 2, r.top - 2, r.right + 2, r.bottom + 2, paint)
        return true
    }
}

/**
 * Настоящее стекло для плавающей панели: то, что под ней (фон окна и [sources]), записывается в RenderNode
 * ровно её размера, слегка размывается и пропускается через линзу.
 * Панель рисуется в собственный слой и перерисовывается только целиком: координаты шейдерного эффекта
 * система считает от перерисовываемой области, и частичная перерисовка (например, гаснущая полоса прокрутки)
 * сдвигала бы стекло.
 */
@TargetApi(33)
class LiquidBackdrop(private val host: View, private val sources: List<View>, radiusDp: Float, private val tint: Int) :
    Drawable(), ViewTreeObserver.OnPreDrawListener {
    private val dp = host.resources.displayMetrics.density
    private val radius = radiusDp * dp
    private val node = RenderNode("liquid")
    private val shader = RuntimeShader(Liquid.AGSL)
    private val loc = IntArray(2)
    private val src = IntArray(2)
    private var shown = ""

    init {
        host.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        host.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = v.viewTreeObserver.addOnPreDrawListener(this@LiquidBackdrop)
            override fun onViewDetachedFromWindow(v: View) = v.viewTreeObserver.removeOnPreDrawListener(this@LiquidBackdrop)
        })
        if (host.isAttachedToWindow) host.viewTreeObserver.addOnPreDrawListener(this)
    }

    override fun onBoundsChange(b: Rect) {
        if (b.isEmpty) return
        node.setPosition(0, 0, b.width(), b.height())
        Liquid.uniforms(shader, 0f, 0f, b.width().toFloat(), b.height().toFloat(), radius, dp, tint)
        val blur = 2.5f * dp
        node.setRenderEffect(RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(shader, "content"),
            RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)))
        shown = ""
    }

    /** Что сейчас под панелью: если ничего не сдвинулось — не трогаем (иначе панель перерисовывалась бы бесконечно). */
    private fun state(): String {
        host.getLocationInWindow(loc)
        val sb = StringBuilder().append(loc[0]).append(',').append(loc[1]).append(',').append(Liquid.version)
        for (v in sources) {
            sb.append('|').append(v.visibility)
            if (v is android.widget.AbsListView) {
                sb.append(',').append(v.firstVisiblePosition).append(',').append(v.childCount)
                    .append(',').append(v.getChildAt(0)?.top ?: 0).append(',').append(v.adapter?.count ?: 0)
            } else sb.append(',').append(v.scrollY)
        }
        return sb.toString()
    }

    override fun onPreDraw(): Boolean {
        if (bounds.isEmpty || !host.isShown) return true
        val now = state()
        if (now != shown) {
            shown = now
            record()
            host.invalidate()   // весь слой панели целиком
        }
        return true
    }

    private fun record() {
        host.getLocationInWindow(loc)
        val c = node.beginRecording()
        c.translate((-loc[0] - bounds.left).toFloat(), (-loc[1] - bounds.top).toFloat())
        host.rootView.background?.draw(c)
        for (v in sources) {
            if (v.visibility != View.VISIBLE) continue
            v.getLocationInWindow(src)
            c.save()
            c.translate(src[0].toFloat(), src[1].toFloat())
            v.draw(c)
            c.restore()
        }
        node.endRecording()
    }

    override fun draw(c: Canvas) {
        if (!c.isHardwareAccelerated) return
        if (shown.isEmpty()) { shown = state(); record() }
        c.save()
        c.translate(bounds.left.toFloat(), bounds.top.toFloat())
        c.drawRenderNode(node)
        c.restore()
    }

    override fun getOutline(outline: Outline) = outline.setRoundRect(bounds, radius)
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
