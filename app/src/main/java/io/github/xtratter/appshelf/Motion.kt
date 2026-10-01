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
import android.view.MotionEvent
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

    // ---------- блик от наклона телефона ----------

    /**
     * Свет «висит» в комнате: по датчику гравитации блик на кромках стекла переезжает, когда наклоняешь телефон.
     * Датчик работает, только пока приложение на экране и включено «жидкое стекло».
     */
    object Tilt : android.hardware.SensorEventListener {
        private var sm: android.hardware.SensorManager? = null
        private var gx = 0f
        private var gy = 9.8f
        private var lastDraw = 0L

        fun start(ctx: Context) {
            if (!enabled) return
            val m = ctx.getSystemService(android.hardware.SensorManager::class.java) ?: return
            val s = m.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY)
                ?: m.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) ?: return
            sm = m
            m.registerListener(this, s, android.hardware.SensorManager.SENSOR_DELAY_UI)
        }

        fun stop() {
            sm?.unregisterListener(this)
            sm = null
        }

        override fun onAccuracyChanged(s: android.hardware.Sensor?, accuracy: Int) {}

        override fun onSensorChanged(e: android.hardware.SensorEvent) {
            // сглаживаем дрожание руки
            gx += (e.values[0] - gx) * 0.15f
            gy += (e.values[1] - gy) * 0.15f
            // «вверх» в координатах экрана (ось y экрана направлена вниз); свет — сверху, чуть слева
            val tilt = (kotlin.math.sqrt(gx * gx + gy * gy) / 9.81f).coerceIn(0f, 1f)
            var lx = -0.6f * (1 - tilt) + (gx / 9.81f - 0.35f) * tilt
            var ly = -0.8f * (1 - tilt) + (-gy / 9.81f) * tilt
            val len = kotlin.math.sqrt(lx * lx + ly * ly).coerceAtLeast(0.01f)
            lx /= len; ly /= len
            if (kotlin.math.abs(lx - Liquid.lightX) + kotlin.math.abs(ly - Liquid.lightY) < 0.02f) return
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastDraw < 33) return   // не чаще ~30 раз в секунду
            lastDraw = now
            Liquid.lightX = lx; Liquid.lightY = ly
            Ui.liquidRoot?.let { redrawGlass(it) }
            Ui.openDialogViews().forEach { redrawGlass(it) }
        }

        /** Перерисовать всё стекло в дереве [v]: фоны-стёкла, панель и строки списка. */
        private fun redrawGlass(v: View) {
            if (v.background is GlassDrawable || v.background is LiquidBackdrop || v is AppItemView || v.rootView === v) v.invalidate()
            if (v is ViewGroup) for (i in 0 until v.childCount) redrawGlass(v.getChildAt(i))
        }
    }

    // ---------- упругое нажатие ----------

    private fun glassOf(v: View): GlassDrawable? = v.background as? GlassDrawable ?: (v as? AppItemView)?.glass

    /** Резиновый ход: чем дальше тянешь, тем туже, не больше [max]. */
    private fun rubber(d: Float, max: Float) = kotlin.math.sign(d) * max * (1f - exp(-kotlin.math.abs(d) / (max * 2.5f)))

    /**
     * Касание стеклянного элемента: [haptic] — щелчок вибрацией при нажатии (когда палец отпускает кнопку);
     * [elastic] — стекло под пальцем продавливается (чуть меньше, линза сжимает фон сильнее) и упруго тянется
     * за пальцем, а отпущенное — отпружинивает. Касание не перехватывается: нажатия и прокрутка работают как обычно.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun touch(v: View, haptic: Haptics.Kind?, elastic: Boolean) {
        var downX = 0f
        var downY = 0f
        var pressAnim: ValueAnimator? = null
        fun pressTo(view: View, target: Float, ms: Long) {
            val g = glassOf(view) ?: return
            pressAnim?.cancel()
            pressAnim = ValueAnimator.ofFloat(g.press, target).apply {
                duration = ms
                addUpdateListener { g.press = it.animatedValue as Float; view.invalidate() }
                start()
            }
        }
        fun release(view: View) {
            view.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(480).setInterpolator(Spring(5f, 1.1f)).start()
            pressTo(view, 0f, 260)
        }
        v.setOnTouchListener { view, e ->
            val dp = view.resources.displayMetrics.density
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    if (elastic) {
                        val s = 1f - (6 * dp / view.width.coerceAtLeast(1)).coerceIn(0.012f, 0.045f)
                        view.animate().scaleX(s).scaleY(s).setDuration(120).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                        pressTo(view, 1f, 140)
                    }
                }
                // тянется за пальцем только на главном экране; в окнах (карточка приложения и др.) — лишнее
                MotionEvent.ACTION_MOVE -> if (elastic && view.rootView === Ui.liquidRoot) {
                    val max = 7 * dp
                    view.translationX = rubber(e.rawX - downX, max)
                    view.translationY = rubber(e.rawY - downY, max)
                }
                MotionEvent.ACTION_UP -> {
                    if (haptic != null && view.isPressed && e.x >= 0 && e.y >= 0 && e.x <= view.width && e.y <= view.height)
                        Haptics.play(haptic)
                    if (elastic) release(view)
                }
                MotionEvent.ACTION_CANCEL -> if (elastic) release(view)
            }
            false
        }
    }

    /** Упругое нажатие для стеклянного элемента (если включено «жидкое стекло» и у него стеклянный фон). */
    fun elasticOf(v: View) = enabled && glassOf(v) != null

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

    /** Видимое стекло вида в координатах главного окна: у строки списка — сама карточка, без полей вокруг. */
    private fun glassRect(v: View): RectF {
        val r = rectOf(v)
        if (v is AppItemView && !v.card.isEmpty) r.set(r.left + v.card.left, r.top + v.card.top, r.left + v.card.right, r.top + v.card.bottom)
        return r
    }

    /** Скругление видимого стекла вида: у строки списка — как у карточки, у кнопок — половина высоты. */
    private fun glassRadius(v: View, r: RectF): Float {
        val dp = v.resources.displayMetrics.density
        return if (v is AppItemView) 22 * dp else minOf(r.height() / 2f, 28 * dp)
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
                     appear: Boolean = true, revealAt: Float = 0.8f, atReveal: () -> Unit, done: () -> Unit) {
        val root = overlayRoot() ?: run { atReveal(); done(); return }
        val blob = Blob(root.context, from, fromR, to, toR, content, appear)
        root.addView(blob, ViewGroup.LayoutParams(-1, -1))
        var revealed = false
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms
            interpolator = LinearInterpolator()   // сглаживание — у каждого края своё (см. Blob)
            addUpdateListener {
                blob.t = it.animatedValue as Float
                // закрытие: в конце капля растворяется поверх уже проявившейся кнопки, а не закрывает её собой
                if (!appear) blob.alpha = ((1f - blob.t) / 0.3f).coerceIn(0f, 1f)
                blob.invalidate()
                if (!revealed && blob.t >= revealAt) { revealed = true; atReveal() }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    if (!revealed) atReveal()
                    // при открытии капля уходит чуть позже — пока проявляется окно, в которое она превратилась
                    if (appear) blob.postDelayed({ root.removeView(blob); done() }, 120)
                    else { root.removeView(blob); done() }
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

        private val anchor = RectF()
        private val dp = ctx.resources.displayMetrics.density

        override fun onDraw(c: Canvas) {
            cur.set(edge(from.left, to.left, dx < 0), edge(from.top, to.top, dy < 0),
                edge(from.right, to.right, dx > 0), edge(from.bottom, to.bottom, dy > 0))
            if (cur.width() < 2 || cur.height() < 2) return
            val r = (fromR + (toR - fromR) * t.coerceIn(0f, 1f)).coerceAtMost(minOf(cur.width(), cur.height()) / 2f)
            // перемычка, как у ртути: при открытии капля отрывается от кнопки (остаток на месте кнопки тает,
            // перемычка истончается и рвётся), при закрытии — наоборот, нарастает у кнопки и сливается с ней
            val home = if (appear) from else to
            val homeR = if (appear) fromR else toR
            // p — ход перемычки: при открытии в первые 60 % пути, при закрытии — в последние 60 %
            val p = (if (appear) t / 0.6f else (t - 0.4f) / 0.6f).coerceIn(0f, 1f)
            // остаток на месте кнопки: при открытии тает, при закрытии нарастает до размера кнопки
            val size = if (appear) 1f - p else p
            // толщина перемычки — ноль в начале и в конце: капля стартует ровно из кнопки и возвращается ровно в неё
            val neck = kotlin.math.sin(PI.toFloat() * p)
            if (size > 0.04f && neck > 0.02f) {
                anchor.set(home.centerX() - home.width() * size / 2, home.centerY() - home.height() * size / 2,
                    home.centerX() + home.width() * size / 2, home.centerY() + home.height() * size / 2)
                glass.drawOverSnapshot(c, this, cur, r, 0, merged = anchor, mergedR = homeR * size, merge = 22 * dp * neck)
            } else glass.drawOverSnapshot(c, this, cur, r, 0)
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
    /** Только что закрытое «перетекающее» окно: где оно было — новое окно может вытечь прямо из него. */
    private class Closing(val rect: RectF, val radius: Float, val at: Long)
    private var closing: Closing? = null

    /**
     * Окно открыли сразу из другого (тот только что закрылся): новое вытекает из места старого — окно «перетекает»
     * в окно, размытие фона не выключается. true — эстафета принята.
     */
    fun handoff(d: Dialog): Boolean {
        val c = closing ?: return false
        if (!enabled || android.os.SystemClock.uptimeMillis() - c.at > 400) return false
        closing = null
        val w = d.window ?: return false
        w.setWindowAnimations(0)
        val decor = w.decorView
        decor.alpha = 0f
        decor.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                decor.viewTreeObserver.removeOnPreDrawListener(this)
                val at = IntArray(2); val base = IntArray(2)
                decor.getLocationOnScreen(at)
                Ui.liquidRoot?.getLocationOnScreen(base)
                val x = (at[0] - base[0]).toFloat(); val y = (at[1] - base[1]).toFloat()
                if (decor.width <= 0 || decor.height <= 0) { decor.alpha = 1f; return true }
                // окно растягивается из прямоугольника прежнего окна в свой, с пружинкой
                decor.pivotX = 0f; decor.pivotY = 0f
                decor.translationX = c.rect.left - x
                decor.translationY = c.rect.top - y
                decor.scaleX = c.rect.width() / decor.width
                decor.scaleY = c.rect.height() / decor.height
                decor.animate().alpha(1f).setDuration(120).start()
                decor.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setDuration(420).setInterpolator(Spring()).start()
                Haptics.play(Haptics.Kind.TICK)
                return true
            }
        })
        return true
    }

    fun morphIn(d: Dialog, src: View) {
        if (!enabled || src.rootView !== Ui.liquidRoot) return
        val w = d.window ?: return
        val decor = w.decorView
        val dp = src.resources.displayMetrics.density
        decor.alpha = 0f
        // без системной анимации окна: иначе при закрытии оно гасло само, а поверх уже бежала капля (двоилось)
        w.setWindowAnimations(0)
        w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        val from = glassRect(src)
        val fromR = glassRadius(src, from)
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
                v.removeOnAttachStateChangeListener(this)
                val shot = capture(decor)
                // из окна сразу открыли другое (например, «Тема» из меню) — окно перетекает в него, а не в кнопку
                closing = Closing(to, toR, android.os.SystemClock.uptimeMillis())
                val mine = closing
                v.post {
                    if (closing !== mine) { src.alpha = 1f; return@post }   // эстафету приняло новое окно
                    closing = null
                    Haptics.play(Haptics.Kind.CLOSE)
                    val back = if (src.isAttachedToWindow) glassRect(src) else from
                    flow(to, toR, back, fromR, 300, content = shot, appear = false, revealAt = 0.55f, atReveal = {
                        src.animate().alpha(1f).setDuration(120).start()
                    }) {}
                }
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
