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
    /** Сейчас список копируется в картинку под стеклом панели: карточки рисуем попроще (обычный холст). */
    @JvmStatic var capturing = false

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
uniform float blur;        // frosting radius, px (0 = clear)

// frosted sample: the point and two rings around it, kept inside the glass (premultiplied)
half4 frost(float2 pos) {
    pos = clamp(pos, origin + 0.5, origin + size - 0.5);
    half4 acc = content.eval(pos);
    if (blur < 0.5) return acc;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.785398 + 0.39;
        float2 o = float2(cos(a), sin(a));
        acc += content.eval(clamp(pos + o * blur, origin + 0.5, origin + size - 0.5));
        acc += content.eval(clamp(pos + o * blur * 0.45, origin + 0.5, origin + size - 0.5));
    }
    return acc / 17.0;
}

float sdf(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

float rimAt(float d, float w) {
    return smoothstep(-w - 1.2, -w * 0.35, d) * (1.0 - smoothstep(-0.4, 0.7, d));
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
    half4 base;
    if (bend < 0.3) {
        base = frost(xy);
    } else {
        half4 g = frost(xy + disp);
        base = half4(frost(xy + disp * 1.18).r, g.g, frost(xy + disp * 0.82).b, g.a);
    }
    // the content may be translucent (a glass fill): work with plain colors and keep its opacity
    float ca = float(base.a);
    float3 col = ca > 0.001 ? float3(base.rgb) / ca : float3(0.0);

    // livelier colors under the glass, then the tint
    float lum = dot(col, float3(0.299, 0.587, 0.114));
    col = mix(float3(lum), col, 1.2);
    col = mix(col, tint.rgb, tint.a);
    ca = max(ca, tint.a);

    // light: a thin bright rim (strongest top-left, a reflection bottom-right) and a soft inner glow
    float2 L = normalize(float2(-0.6, -0.8));
    float k = dot(n, L);

    float spec = 0.25 + 0.75 * pow(max(k, 0.0), 1.5) + 0.6 * pow(max(-k, 0.0), 2.0);
    float rimW = 1.4 * dp;
    float ra = clamp(rimAt(d, rimW) * spec * light, 0.0, 1.0);
    col = mix(col, float3(1.0), ra);
    float glow = smoothstep(-9.0 * dp, 0.0, d);
    float ga = glow * glow * 0.09 * light * (0.35 + max(k, 0.0) + 0.5 * max(-k, 0.0));
    col += ga;
    ca = max(ca, max(ra, ga * 2.0));

    float a = (1.0 - smoothstep(-0.75, 0.75, d)) * ca;
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

    fun uniforms(s: RuntimeShader, x: Float, y: Float, w: Float, h: Float, radius: Float, dp: Float, tint: Int, blur: Float = 0f) {
        s.setFloatUniform("blur", blur)
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

    private var fillShader: Shader? = null
    private var fillFor = 0

    /**
     * Стекло без того, что под ним (окна-диалоги, кнопки в них): та же кромка, блик и свечение, что у панели,
     * поверх полупрозрачной заливки [fill]. false — холст не аппаратный (копия под панелью), рисовать по-старому.
     */
    fun drawRim(c: Canvas, r: RectF, radius: Float, fill: Int): Boolean {
        if (!c.isHardwareAccelerated || Liquid.capturing) return false
        if (fillShader == null || fillFor != fill) {
            fillShader = android.graphics.LinearGradient(0f, 0f, 1f, 0f, fill, fill, Shader.TileMode.CLAMP)
            fillFor = fill
        }
        shader.setInputShader("content", fillShader!!)
        Liquid.uniforms(shader, r.left, r.top, r.width(), r.height(), radius, dp, 0)
        paint.shader = shader
        c.drawRect(r.left - 2, r.top - 2, r.right + 2, r.bottom + 2, paint)
        return true
    }

    private var snapShader: BitmapShader? = null
    private var snapFor: android.graphics.Bitmap? = null

    /**
     * Стекло в окне-диалоге: под ним — снимок главного экрана ([Ui.snapshot]), размытый и преломлённый у краёв,
     * поверх — заливка окна и самого элемента. false — снимка нет или холст не аппаратный.
     */
    fun drawOverSnapshot(c: Canvas, host: View, r: RectF, radius: Float, fill: Int): Boolean {
        if (!c.isHardwareAccelerated || Liquid.capturing) return false
        val bmp = Ui.snapshot ?: return false
        if (snapFor !== bmp) { snapShader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }; snapFor = bmp }
        val bs = snapShader ?: return false
        host.getLocationOnScreen(loc)
        m.setScale(Ui.snapshotW / bmp.width.toFloat(), Ui.snapshotH / bmp.height.toFloat())
        m.postTranslate(Ui.snapshotX - loc[0].toFloat(), Ui.snapshotY - loc[1].toFloat())
        bs.setLocalMatrix(m)
        shader.setInputShader("content", bs)
        // элемент внутри окна: его заливка поверх заливки окна, как если бы он лежал на стекле окна
        val tint = if (host.rootView === host) Ui.dialogBlurColor() else Ui.over(fill, Ui.dialogBlurColor())
        Liquid.uniforms(shader, r.left, r.top, r.width(), r.height(), radius, dp, if (host.rootView === host) fill else tint)
        paint.shader = shader
        c.drawRect(r.left - 2, r.top - 2, r.right + 2, r.bottom + 2, paint)
        return true
    }

    /** false — рисовать нечем (фон ещё не готов или стекло не в главном окне). */
    fun draw(c: Canvas, host: View, r: RectF, radius: Float, tint: Int): Boolean {
        if (!c.isHardwareAccelerated || Liquid.capturing) return false
        val bmp = Ui.backdrop ?: return false
        if (host.rootView !== Ui.liquidRoot || Ui.backdropW <= 0) return false
        if (bmpFor !== bmp) { bmpShader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }; bmpFor = bmp }
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
 * Настоящее стекло для плавающей панели: то, что под ней (фон окна и [sources]), копируется в картинку
 * в половину размера — только когда список сдвинулся или поменялся, — и рисуется той же линзой, что и карточки.
 * Системные эффекты (RenderEffect) для этого не годятся: их координаты система считает от перерисовываемой
 * области, и стекло то уплывало, то срезалось.
 */
@TargetApi(33)
class LiquidBackdrop(private val host: View, private val sources: List<View>, radiusDp: Float, private val tint: Int) :
    Drawable(), ViewTreeObserver.OnPreDrawListener {
    private val dp = host.resources.displayMetrics.density
    private val radius = radiusDp * dp
    private val scale = 0.5f
    private val shader = RuntimeShader(Liquid.AGSL)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val m = Matrix()
    private val loc = IntArray(2)
    private val src = IntArray(2)
    /**
     * Три картинки по очереди: новая рисуется в свободную, а на экран уходит только готовая.
     * С одной картинкой системный поток отрисовки иногда брал её в момент перерисовки — шапка моргала чёрным.
     */
    private val bmps = arrayOfNulls<android.graphics.Bitmap>(3)
    private val shaders = arrayOfNulls<BitmapShader>(3)
    private var front = 0
    private val bmp get() = bmps[front]
    private var shown = ""

    init {
        host.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = v.viewTreeObserver.addOnPreDrawListener(this@LiquidBackdrop)
            override fun onViewDetachedFromWindow(v: View) = v.viewTreeObserver.removeOnPreDrawListener(this@LiquidBackdrop)
        })
        if (host.isAttachedToWindow) host.viewTreeObserver.addOnPreDrawListener(this)
    }

    override fun onBoundsChange(b: Rect) {
        if (b.isEmpty) return
        for (i in bmps.indices) {
            val out = android.graphics.Bitmap.createBitmap((b.width() * scale).toInt().coerceAtLeast(1),
                (b.height() * scale).toInt().coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
            out.eraseColor(Ui.base)
            bmps[i] = out
            m.setScale(b.width() / out.width.toFloat(), b.height() / out.height.toFloat())
            m.postTranslate(b.left.toFloat(), b.top.toFloat())
            shaders[i] = BitmapShader(out, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
                setLocalMatrix(m)
            }
        }
        front = 0
        shader.setInputShader("content", shaders[0]!!)
        Liquid.uniforms(shader, b.left.toFloat(), b.top.toFloat(), b.width().toFloat(), b.height().toFloat(),
            radius, dp, tint)
        paint.shader = shader
        shown = ""
    }

    /** Что сейчас под панелью: если ничего не сдвинулось — не копируем заново. */
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
            capture()
            invalidateSelf()
        }
        return true
    }

    /** Нарисовать в картинку то, что под панелью: фон окна и список (в обычном, не аппаратном режиме). */
    private fun capture() {
        val next = (front + 1) % bmps.size
        val out = bmps[next] ?: return
        host.getLocationInWindow(loc)
        val x = (loc[0] + bounds.left).toFloat()
        val y = (loc[1] + bounds.top).toFloat()
        out.eraseColor(Ui.base)
        val c = Canvas(out)
        c.scale(out.width / bounds.width().toFloat(), out.height / bounds.height().toFloat())
        c.translate(-x, -y)
        c.clipRect(x, y, x + bounds.width(), y + bounds.height())
        Liquid.capturing = true
        try {
            host.rootView.background?.draw(c)
            for (v in sources) {
                if (v.visibility != View.VISIBLE) continue
                v.getLocationInWindow(src)
                c.save()
                c.translate(src[0].toFloat(), src[1].toFloat())
                v.draw(c)
                c.restore()
            }
        } finally {
            Liquid.capturing = false
        }
        // лёгкое плавное размытие (картинка в полразмера: 1,5 dp здесь ≈ 3 dp на экране)
        Blur.apply(out, (Ui.barBlurDp * dp * out.width / bounds.width()).toInt())
        // готово — показываем её; прежняя остаётся нетронутой, пока её дорисовывает система
        front = next
        shader.setInputShader("content", shaders[next]!!)
        paint.shader = shader
    }

    override fun draw(c: Canvas) {
        if (bmp == null) return
        if (!c.isHardwareAccelerated) {   // снимок экрана для окон: рисуем просто, без шейдера
            c.drawRoundRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(),
                radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.withAlpha(Ui.surface, 0.9f) })
            return
        }
        if (shown.isEmpty()) { shown = state(); capture() }
        c.drawRect(bounds.left - 1f, bounds.top - 1f, bounds.right + 1f, bounds.bottom + 1f, paint)
    }

    // тот же радиус, что у стекла (не больше половины высоты), иначе тень ложится прямоугольником в уголки
    override fun getOutline(outline: Outline) = outline.setRoundRect(bounds, minOf(radius, bounds.height() / 2f, bounds.width() / 2f))
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
