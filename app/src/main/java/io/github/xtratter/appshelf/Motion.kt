package io.github.xtratter.appshelf

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp

/**
 * «Жидкое стекло» перетекает: нажатая кнопка вытекает стеклянной каплей и превращается в окно,
 * а закрытое окно стекает обратно в кнопку. Капля рисуется поверх главного экрана тем же стеклом,
 * что и окна (со снимком экрана под ним), поэтому переход между каплей и окном незаметен.
 * Работает, только пока включено «жидкое стекло» (Android 13+).
 */
object Motion {
    /** Пружина: быстро к цели, лёгкий перелёт и затухающее покачивание. */
    class Spring(private val decay: Float = 6.5f, private val cycles: Float = 0.85f) : TimeInterpolator {
        override fun getInterpolation(t: Float): Float =
            if (t >= 1f) 1f else (1f - exp(-decay * t) * cos(2 * PI.toFloat() * cycles * t))
    }

    /** Откуда вытечет следующее окно (кнопка или строка главного экрана); забирается [Ui.glassDialog]. */
    private var source: View? = null
    private var sourceAt = 0L

    fun from(v: View) { source = v; sourceAt = android.os.SystemClock.uptimeMillis() }

    /** Источник, только если нажали только что (иначе окно откроется само по себе, а не из старой кнопки). */
    fun takeSource(): View? = source.takeIf { android.os.SystemClock.uptimeMillis() - sourceAt < 600 }.also { source = null }

    private val enabled get() = Ui.liquid && Build.VERSION.SDK_INT >= 33

    private fun overlayRoot(): ViewGroup? = Ui.liquidRoot as? ViewGroup

    /**
     * Снимок содержимого [v] без его фона (стеклом будет сама капля), в полразмера — хватает для анимации.
     * null — вид ещё не разложен.
     */
    private fun capture(v: View): android.graphics.Bitmap? {
        if (v.width <= 0 || v.height <= 0) return null
        val bmp = android.graphics.Bitmap.createBitmap((v.width / 2).coerceAtLeast(1), (v.height / 2).coerceAtLeast(1),
            android.graphics.Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(bmp.width / v.width.toFloat(), bmp.height / v.height.toFloat())
        Liquid.capturing = true
        try {
            if (v is ViewGroup) for (i in 0 until v.childCount) {
                val ch = v.getChildAt(i)
                if (ch.visibility != View.VISIBLE) continue
                c.save()
                c.translate(ch.left.toFloat(), ch.top.toFloat())
                ch.draw(c)
                c.restore()
            } else v.draw(c)
        } catch (e: Exception) {
            return null
        } finally {
            Liquid.capturing = false
        }
        return bmp
    }

    /** Прямоугольник вида в координатах главного окна. */
    private fun rectOf(v: View): RectF {
        val at = IntArray(2)
        v.getLocationInWindow(at)
        return RectF(at[0].toFloat(), at[1].toFloat(), (at[0] + v.width).toFloat(), (at[1] + v.height).toFloat())
    }

    /**
     * Капля из [from] в [to] (радиусы скруглений — [fromR], [toR]); [atReveal] — когда капля почти на месте
     * (пора показывать то, во что она превращается), [done] — в конце, капля уже убрана.
     */
    @TargetApi(33)
    private fun flow(from: RectF, fromR: Float, to: RectF, toR: Float, ms: Long, content: android.graphics.Bitmap? = null,
                     appear: Boolean = true, atReveal: () -> Unit, done: () -> Unit) {
        val root = overlayRoot() ?: run { atReveal(); done(); return }
        val blob = Blob(root.context, from, fromR, to, toR, content, appear)
        root.addView(blob, ViewGroup.LayoutParams(-1, -1))
        var revealed = false
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms
            interpolator = LinearInterpolator()   // сглаживание — у каждого края своё (см. Blob)
            addUpdateListener {
                blob.t = it.animatedValue as Float
                blob.invalidate()
                if (!revealed && blob.t >= 0.8f) { revealed = true; atReveal() }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    if (!revealed) atReveal()
                    // капля уходит чуть позже — пока проявляется то, во что она превратилась
                    blob.postDelayed({ root.removeView(blob); done() }, 120)
                }
            })
            start()
        }
    }

    /**
     * Капля стекла: четыре края идут к цели каждый по своей пружине — ведущие (по направлению движения) раньше,
     * отстающие позже, — поэтому форма по пути вытягивается, как жидкость.
     */
    @TargetApi(33)
    @SuppressLint("ViewConstructor")
    private class Blob(ctx: Context, val from: RectF, val fromR: Float, val to: RectF, val toR: Float,
                       val content: android.graphics.Bitmap?, val appear: Boolean) : View(ctx) {
        private val contentPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val clip = android.graphics.Path()
        var t = 0f
        private val glass = LiquidCard(ctx.resources.displayMetrics.density)
        private val cur = RectF()
        private val spring = Spring()
        private val dx = to.centerX() - from.centerX()
        private val dy = to.centerY() - from.centerY()

        private fun edge(a: Float, b: Float, lead: Boolean): Float {
            val delay = if (lead) 0f else 0.16f
            val p = ((t - delay) / (1f - delay)).coerceIn(0f, 1f)
            return a + (b - a) * spring.getInterpolation(p)
        }

        override fun onDraw(c: Canvas) {
            cur.set(edge(from.left, to.left, dx < 0), edge(from.top, to.top, dy < 0),
                edge(from.right, to.right, dx > 0), edge(from.bottom, to.bottom, dy > 0))
            if (cur.width() < 2 || cur.height() < 2) return
            val r = (fromR + (toR - fromR) * t.coerceIn(0f, 1f)).coerceAtMost(minOf(cur.width(), cur.height()) / 2f)
            glass.drawOverSnapshot(c, this, cur, r, 0)
            // содержимое окна течёт вместе с каплей: растягивается по её форме, проявляется (или растворяется)
            val bmp = content ?: return
            val k = if (appear) ((t - 0.3f) / 0.6f) else (1f - t / 0.55f)
            val a = k.coerceIn(0f, 1f)
            if (a <= 0f) return
            contentPaint.alpha = (a * 255).toInt()
            clip.reset()
            clip.addRoundRect(cur, r, r, android.graphics.Path.Direction.CW)
            c.save()
            c.clipPath(clip)
            c.drawBitmap(bmp, null, cur, contentPaint)
            c.restore()
        }
    }

    /**
     * Окно вытекает из кнопки [src]: пока капля растёт, окно невидимо, без затемнения и размытия позади
     * (иначе система размыла бы и каплю); потом окно проявляется, а при закрытии стекает обратно в кнопку.
     */
    fun morphIn(d: Dialog, src: View) {
        if (!enabled || src.rootView !== Ui.liquidRoot) return
        val w = d.window ?: return
        val decor = w.decorView
        val dp = src.resources.displayMetrics.density
        decor.alpha = 0f
        w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        val from = rectOf(src)
        val fromR = minOf(src.height / 2f, 28 * dp)
        // место окна и его затемнение известны перед первой отрисовкой (окно могли ещё передвинуть — как меню ⋮)
        decor.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                decor.viewTreeObserver.removeOnPreDrawListener(this)
                val dim = w.attributes.dimAmount
                w.setDimAmount(0f)
                grow(w, decor, src, from, fromR, dim, dp)
                return true
            }
        })
    }

    private fun grow(w: android.view.Window, decor: View, src: View, from: RectF, fromR: Float, dim: Float, dp: Float) {
        val at = IntArray(2); val base = IntArray(2)
        decor.getLocationOnScreen(at)
        Ui.liquidRoot?.getLocationOnScreen(base)
        val to = RectF((at[0] - base[0]).toFloat(), (at[1] - base[1]).toFloat(),
            (at[0] - base[0] + decor.width).toFloat(), (at[1] - base[1] + decor.height).toFloat())
        val toR = 28 * dp
        Haptics.play(Haptics.Kind.OPEN)
        src.animate().alpha(0f).setDuration(90).start()
        flow(from, fromR, to, toR, 380, content = capture(decor), appear = true, atReveal = {
            decor.animate().alpha(1f).setDuration(110).start()
        }) {
            // фон позади окна затемняется и размывается плавно, когда окно уже на месте
            if (!decor.isAttachedToWindow) return@flow
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 180
                addUpdateListener { a ->
                    if (!decor.isAttachedToWindow) return@addUpdateListener
                    val k = a.animatedValue as Float
                    w.setDimAmount(dim * k)
                    w.attributes = w.attributes.apply { blurBehindRadius = (10 * dp * k).toInt() }
                }
                start()
            }
        }
        // закрыли — окно стекает обратно в кнопку (со своим содержимым, каким оно было в момент закрытия)
        decor.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                val shot = capture(decor)
                Haptics.play(Haptics.Kind.CLOSE)
                v.removeOnAttachStateChangeListener(this)
                val back = if (src.isAttachedToWindow) rectOf(src) else from
                flow(to, toR, back, fromR, 300, content = shot, appear = false, atReveal = {
                    src.animate().alpha(1f).setDuration(100).start()
                }) {}
            }
        })
    }

    /** Строка поиска вытекает из кнопки-лупы: [box] становится видимой, когда капля на месте. */
    fun openSearch(box: View, lens: View, done: () -> Unit) {
        if (!enabled) { box.visibility = View.VISIBLE; done(); return }
        Ui.takeSnapshot()
        box.alpha = 0f
        box.visibility = View.VISIBLE
        box.post {
            val dp = box.resources.displayMetrics.density
            val from = rectOf(lens)
            val to = rectOf(box)
            flow(from, from.height() / 2f, to, 26 * dp, 360, content = capture(box), appear = true,
                atReveal = { box.animate().alpha(1f).setDuration(100).start() }) {}
            done()
        }
    }

    /** И стекает обратно в лупу; [done] — когда строка спрятана. */
    fun closeSearch(box: View, lens: View, done: () -> Unit) {
        if (!enabled) { box.visibility = View.GONE; done(); return }
        val dp = box.resources.displayMetrics.density
        Ui.takeSnapshot()
        val from = rectOf(box)
        val to = rectOf(lens)
        val shot = capture(box)
        box.visibility = View.GONE
        box.alpha = 1f
        done()
        flow(from, 26 * dp, to, to.height() / 2f, 280, content = shot, appear = false, atReveal = {}) {}
    }
}
