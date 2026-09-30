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
    const val AGSL = """
uniform shader content;
uniform float2 origin;     // top-left of the glass in content coordinates
uniform float2 size;
uniform float radius;
uniform float bezel;       // width of the curved rim, px
uniform float strength;    // how far the rim bends the backdrop, px
uniform float4 tint;       // glass tint, a = amount
uniform float light;       // highlight brightness

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

    // 0 = middle of the glass, 1 = the very edge; the lens bends most at the edge
    float t = clamp(1.0 + d / bezel, 0.0, 1.0);
    float2 disp = n * pow(t, 2.5) * strength;

    float3 col = float3(content.eval(xy + disp * 1.14).r,
                        content.eval(xy + disp).g,
                        content.eval(xy + disp * 0.86).b);

    // slightly livelier colors under the glass
    float lum = dot(col, float3(0.299, 0.587, 0.114));
    col = mix(float3(lum), col, 1.18);
    col = mix(col, tint.rgb, tint.a);

    // highlights: a thin bright rim and a soft glow along the curve
    float2 L = normalize(float2(-0.55, -0.85));
    float rim = smoothstep(-3.2, -0.6, d) * (1.0 - smoothstep(-0.6, 0.9, d));
    float spec = pow(max(dot(n, L), 0.0), 2.0) * 0.95 + pow(max(dot(n, -L), 0.0), 3.0) * 0.5 + 0.12;
    col += rim * spec * light;
    col += t * t * 0.10 * light * (0.35 + max(dot(n, L), 0.0));

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
        val bezel = minOf(minOf(w, h) * 0.45f, 22 * dp)
        s.setFloatUniform("bezel", bezel)
        s.setFloatUniform("strength", bezel * 0.75f)
        s.setFloatUniform("tint", ((tint shr 16) and 0xFF) / 255f, ((tint shr 8) and 0xFF) / 255f,
            (tint and 0xFF) / 255f, ((tint ushr 24) and 0xFF) / 255f)
        s.setFloatUniform("light", if (Ui.light) 0.55f else 0.42f)
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
 * Настоящее стекло для плавающей панели: перед каждым кадром то, что под ней (фон окна и [sources]),
 * записывается в RenderNode с запасом по краям, слегка размывается и пропускается через линзу.
 */
@TargetApi(33)
class LiquidBackdrop(private val host: View, private val sources: List<View>, radiusDp: Float, private val tint: Int) :
    Drawable(), ViewTreeObserver.OnPreDrawListener {
    private val dp = host.resources.displayMetrics.density
    private val radius = radiusDp * dp
    private val margin = (28 * dp).toInt()
    private val node = RenderNode("liquid")
    private val shader = RuntimeShader(Liquid.AGSL)
    private val loc = IntArray(2)
    private val src = IntArray(2)

    init {
        host.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = v.viewTreeObserver.addOnPreDrawListener(this@LiquidBackdrop)
            override fun onViewDetachedFromWindow(v: View) = v.viewTreeObserver.removeOnPreDrawListener(this@LiquidBackdrop)
        })
        if (host.isAttachedToWindow) host.viewTreeObserver.addOnPreDrawListener(this)
    }

    override fun onBoundsChange(b: Rect) {
        if (b.isEmpty) return
        node.setPosition(0, 0, b.width() + 2 * margin, b.height() + 2 * margin)
        Liquid.uniforms(shader, margin.toFloat(), margin.toFloat(), b.width().toFloat(), b.height().toFloat(), radius, dp, tint)
        val blur = 5 * dp
        node.setRenderEffect(RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(shader, "content"),
            RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)))
        record()
    }

    /** Кадр вот-вот нарисуется (что-то сдвинулось) — переписываем то, что под панелью. */
    override fun onPreDraw(): Boolean {
        if (!bounds.isEmpty) record()
        return true
    }

    private fun record() {
        host.getLocationInWindow(loc)
        val c = node.beginRecording()
        c.translate((margin - loc[0] - bounds.left).toFloat(), (margin - loc[1] - bounds.top).toFloat())
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
        c.save()
        c.translate((bounds.left - margin).toFloat(), (bounds.top - margin).toFloat())
        c.drawRenderNode(node)
        c.restore()
    }

    override fun getOutline(outline: Outline) = outline.setRoundRect(bounds, radius)
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
