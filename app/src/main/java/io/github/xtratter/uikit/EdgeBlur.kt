// Copied from github.com/Xtratter/android-ui-kit (v1.1) — edit there and re-run install.sh
package io.github.xtratter.uikit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.FrameLayout
import android.widget.ScrollView

/**
 * Soft edges like in Telegram: scrolling content near the top and bottom edges is progressively blurred (stronger
 * towards the edge) and fades into the background. The container draws the blur itself in the same frame as the
 * content, so nothing lags behind at any scroll speed. Android 12+; below that — only the fade.
 *
 * Usage: `EdgeBlur.wrap(listView, bandDp = 56f, fadeTop = bgColor)` — replaces the view in its parent with a
 * wrapper; `alwaysTop`/`alwaysBottom` keep the bands even when there is nothing to scroll, `dissolve` makes the
 * edges transparent (for dialogs), `topBand`/`bottomBand` (px) can be changed later, e.g. from window insets.
 */
@SuppressLint("ViewConstructor")
class EdgeBlur(ctx: Context, private val fadeTop: Int, private val fadeBottom: Int) : FrameLayout(ctx),
    android.view.ViewTreeObserver.OnPreDrawListener {

    companion object {
        /** Обернуть прокручиваемый [target] в контейнер с мягкими краями высотой [bandDp]. */
        fun wrap(target: View, bandDp: Float, fadeTop: Int, fadeBottom: Int = fadeTop): EdgeBlur? {
            val parent = target.parent as? ViewGroup ?: return null
            val index = parent.indexOfChild(target)
            val lp = target.layoutParams
            parent.removeView(target)
            val box = EdgeBlur(target.context, fadeTop, fadeBottom)
            val px = (bandDp * target.resources.displayMetrics.density).toInt()
            box.topBand = px; box.bottomBand = px
            box.addView(target, LayoutParams(-1, -1))
            parent.addView(box, index, lp)
            return box
        }

        /**
         * Прокручиваемый список или ScrollView окна — видимый и самый большой (у AlertDialog есть свой скрытый
         * ScrollView для текста сообщения — его пропускаем).
         */
        fun findScrollable(v: View): View? {
            var best: View? = null
            fun walk(x: View) {
                if (x.visibility != VISIBLE) return
                if ((x is ScrollView || x is AbsListView) && (best == null || x.height > best!!.height)) best = x
                if (x is ViewGroup) for (i in 0 until x.childCount) walk(x.getChildAt(i))
            }
            walk(v)
            return best?.takeIf { it.height > 0 }
        }
    }

    /** Высота полос (px): сверху — [topBand], из них нижние [topRamp] — плавный переход (0 — вся полоса); снизу так же. */
    var topBand = 0
    var topRamp = 0
    var bottomBand = 0
    var bottomRamp = 0
    /** Полосы всегда (главный список: размытие под шапкой и у края экрана — и до прокрутки), а не только когда есть что прокручивать. */
    var alwaysTop = false
    /**
     * Окна: у краёв содержимое ещё и становится прозрачным — растворяется в стекле окна, без резкой линии там,
     * где кончается область прокрутки (например, над кнопкой «Закрыть»).
     */
    var dissolve = false
    private val outP = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    var alwaysBottom = false

    private val dp = ctx.resources.displayMetrics.density
    private val blurOk = Build.VERSION.SDK_INT >= 31
    /** Ступени размытия (dp), плавно перекрывающие друг друга; две — вдвое меньше работы в каждом кадре прокрутки. */
    private val radii = floatArrayOf(4f, 13f)
    private val content = if (blurOk) RenderNode("edge-content") else null
    private val levels = if (blurOk) radii.map { RenderNode("edge-$it") to RenderNode("edge-$it-b") } else emptyList()
    private val maskP = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val tintP = Paint()

    init {
        setWillNotDraw(false)
        addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = v.viewTreeObserver.addOnPreDrawListener(this@EdgeBlur)
            override fun onViewDetachedFromWindow(v: View) = v.viewTreeObserver.removeOnPreDrawListener(this@EdgeBlur)
        })
        if (blurOk) levels.forEachIndexed { i, (a, b) ->
            val r = radii[i] * dp
            a.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP))
            b.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP))
        }
    }

    /** Какие полосы нужны сейчас: верхняя — если есть что прокручивать вверх, нижняя — вниз (или всегда). */
    private fun want(): Int {
        val t = if (childCount > 0) getChildAt(0) else return 0
        return (if (topBand > 0 && (alwaysTop || t.canScrollVertically(-1))) 1 else 0) or
            (if (bottomBand > 0 && (alwaysBottom || t.canScrollVertically(1))) 2 else 0)
    }
    private var drawn = -1

    /**
     * При прокрутке перерисовывается только список внутри, а не этот контейнер, — без этой проверки решение
     * «показывать ли полосу» застревало в прежнем состоянии (сверху не появлялось, снизу пропадало).
     */
    override fun onPreDraw(): Boolean {
        if (want() != drawn) invalidate()
        return true
    }

    override fun dispatchDraw(c: Canvas) {
        drawn = want()
        val w = width; val h = height
        val target = if (childCount > 0) getChildAt(0) else null
        if (w <= 0 || h <= 0 || target == null) return super.dispatchDraw(c)
        val showTop = drawn and 1 != 0
        val showBottom = drawn and 2 != 0
        val node = content
        if (node == null || !c.isHardwareAccelerated || (!showTop && !showBottom)) {
            super.dispatchDraw(c)
            drawTints(c, w, h, showTop, showBottom)
            return
        }
        // растворение: всё рисуем в слой, а в конце у краёв делаем его прозрачным
        val layer = if (dissolve) c.saveLayer(0f, 0f, w.toFloat(), h.toFloat(), null) else -1
        // содержимое — один раз в слой; рисуем его как есть, а у краёв — размытые копии того же кадра
        node.setPosition(0, 0, w, h)
        val rc = node.beginRecording(w, h)
        super.dispatchDraw(rc)
        node.endRecording()
        c.drawRenderNode(node)
        if (showTop) band(c, node, w, 0, topBand, if (topRamp in 1 until topBand) topRamp else topBand, top = true)
        if (showBottom) band(c, node, w, h - bottomBand, h, if (bottomRamp in 1 until bottomBand) bottomRamp else bottomBand, top = false)
        drawTints(c, w, h, showTop, showBottom)
        if (layer >= 0) {
            dissolveEdges(c, w, h, showTop, showBottom)
            c.restoreToCount(layer)
        }
    }

    /** Края слоя — в прозрачность: последние 70 % полосы плавно уходят от 1 к 0. */
    private fun dissolveEdges(c: Canvas, w: Int, h: Int, showTop: Boolean, showBottom: Boolean) {
        if (showTop) {
            outP.shader = LinearGradient(0f, topBand * 0.7f, 0f, 0f, -1, 0, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), topBand.toFloat(), outP)
        }
        if (showBottom) {
            outP.shader = LinearGradient(0f, h - bottomBand * 0.7f, 0f, h.toFloat(), -1, 0, Shader.TileMode.CLAMP)
            c.drawRect(0f, (h - bottomBand).toFloat(), w.toFloat(), h.toFloat(), outP)
        }
    }

    /** Полоса [y0, y1): ступени размытия; каждая проявляется плавно и перекрывает предыдущую ближе к краю. */
    private fun band(c: Canvas, content: RenderNode, w: Int, y0: Int, y1: Int, ramp: Int, top: Boolean) {
        val n = radii.size
        for ((i, pair) in levels.withIndex()) {
            val node = if (top) pair.first else pair.second
            val margin = (radii[i] * dp * 3).toInt()
            // слой ступени шире полосы — края размытия (где CLAMP тянет пиксели) остаются за её пределами
            val ny0 = (y0 - margin).coerceAtLeast(0)
            val ny1 = (y1 + margin).coerceAtMost(height)
            node.setPosition(0, ny0, w, ny1)
            val nc = node.beginRecording(w, ny1 - ny0)
            nc.translate(0f, -ny0.toFloat())
            nc.drawRenderNode(content)
            node.endRecording()
            // где ступень проявляется: от внутренней границы перехода к краю, с перекрытием соседних
            val inner = if (top) y1.toFloat() else y0.toFloat()
            val start = ramp * (i / (n + 0.5f))
            val end = ramp * ((i + 1.5f) / (n + 0.5f))
            val a0 = if (top) inner - start else inner + start
            val a1 = if (top) inner - end else inner + end
            val save = c.saveLayer(0f, y0.toFloat(), w.toFloat(), y1.toFloat(), null)
            c.drawRenderNode(node)
            maskP.shader = LinearGradient(0f, a0, 0f, a1, 0, -1, Shader.TileMode.CLAMP)
            c.drawRect(0f, y0.toFloat(), w.toFloat(), y1.toFloat(), maskP)
            c.restoreToCount(save)
        }
    }

    /** Лёгкое растворение в цвете фона у самого края. */
    private fun drawTints(c: Canvas, w: Int, h: Int, showTop: Boolean, showBottom: Boolean) {
        if (showTop) {
            tintP.shader = LinearGradient(0f, topBand.toFloat(), 0f, 0f, fadeTop and 0xFFFFFF, fadeTop, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), topBand.toFloat(), tintP)
        }
        if (showBottom) {
            tintP.shader = LinearGradient(0f, (h - bottomBand).toFloat(), 0f, h.toFloat(), fadeBottom and 0xFFFFFF, fadeBottom, Shader.TileMode.CLAMP)
            c.drawRect(0f, (h - bottomBand).toFloat(), w.toFloat(), h.toFloat(), tintP)
        }
    }
}
