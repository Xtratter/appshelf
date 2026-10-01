package io.github.xtratter.appshelf

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
import android.view.ViewTreeObserver
import android.widget.AbsListView
import android.widget.FrameLayout
import android.widget.ScrollView

/**
 * Прогрессивное размытие у края прокручиваемого списка: полоса, в которой то, что под ней, нарисовано ещё раз —
 * размытым тем сильнее, чем ближе к краю (несколько ступеней), и плавно уходит в цвет фона.
 * На Android 12+; ниже — только плавное растворение в фоне.
 */
@SuppressLint("ViewConstructor")
class EdgeBlur(ctx: Context, private val target: View, private val top: Boolean, private val fadeTo: Int) :
    View(ctx), ViewTreeObserver.OnPreDrawListener {

    companion object {
        /** Содержимое списка поменялось без прокрутки (новые строки, загрузились значки) — перерисовать полосы. */
        @JvmStatic var version = 0

        /**
         * Полосы сверху и снизу у прокручиваемого [target] внутри окна: [target] переносится в FrameLayout
         * вместе с двумя полосами высотой [heightDp]. Полоса видна, только когда в её сторону есть что прокручивать.
         */
        fun wrap(target: View, heightDp: Float, fadeTo: Int) {
            val parent = target.parent as? ViewGroup ?: return
            val index = parent.indexOfChild(target)
            val lp = target.layoutParams
            parent.removeView(target)
            val box = FrameLayout(target.context)
            box.addView(target, FrameLayout.LayoutParams(-1, -1))
            val h = (heightDp * target.resources.displayMetrics.density).toInt()
            box.addView(EdgeBlur(target.context, target, true, fadeTo), FrameLayout.LayoutParams(-1, h, android.view.Gravity.TOP))
            box.addView(EdgeBlur(target.context, target, false, fadeTo), FrameLayout.LayoutParams(-1, h, android.view.Gravity.BOTTOM))
            parent.addView(box, index, lp)
        }

        /** Первый прокручиваемый список или ScrollView в дереве [v]. */
        fun findScrollable(v: View): View? {
            if (v is ScrollView || v is AbsListView) return v
            if (v is ViewGroup) for (i in 0 until v.childCount) findScrollable(v.getChildAt(i))?.let { return it }
            return null
        }
    }

    private val dp = ctx.resources.displayMetrics.density
    private val blurOk = Build.VERSION.SDK_INT >= 31
    /** Ступени размытия (dp): у внутренней границы полосы — слабое, у края — сильное. */
    private val radii = floatArrayOf(2f, 6f, 14f)
    private val base = if (blurOk) RenderNode("edge") else null
    private val levels = if (blurOk) radii.map { r ->
        RenderNode("edge-$r").apply {
            setRenderEffect(RenderEffect.createBlurEffect(r * dp, r * dp, Shader.TileMode.CLAMP))
        }
    } else emptyList()
    private val maskP = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val tintP = Paint()
    private val masks = ArrayList<Shader>()
    private val me = IntArray(2)
    private val them = IntArray(2)
    private var shown = ""

    init {
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = v.viewTreeObserver.addOnPreDrawListener(this@EdgeBlur)
            override fun onViewDetachedFromWindow(v: View) = v.viewTreeObserver.removeOnPreDrawListener(this@EdgeBlur)
        })
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        base?.setPosition(0, 0, w, h)
        levels.forEach { it.setPosition(0, 0, w, h) }
        // маски ступеней: каждая следующая (сильнее размытая) начинается ближе к краю
        masks.clear()
        val n = radii.size
        for (i in 0 until n) {
            val from = h * i / (n + 1f)
            val to = h * (i + 1) / (n + 1f)
            masks += if (top) LinearGradient(0f, h - from, 0f, h - to, 0, -1, Shader.TileMode.CLAMP)
            else LinearGradient(0f, from, 0f, to, 0, -1, Shader.TileMode.CLAMP)
        }
        // и в самом конце — растворение в цвете фона ([fadeTo] — цвет у самого края, вместе с прозрачностью)
        val clear = fadeTo and 0xFFFFFF
        tintP.shader = if (top) LinearGradient(0f, h.toFloat(), 0f, 0f, clear, fadeTo, Shader.TileMode.CLAMP)
        else LinearGradient(0f, 0f, 0f, h.toFloat(), clear, fadeTo, Shader.TileMode.CLAMP)
        shown = ""
    }

    /** Есть ли что прокручивать в сторону этой полосы. */
    private fun active() = target.canScrollVertically(if (top) -1 else 1)

    /** Что сейчас под полосой: если ничего не сдвинулось — не перерисовываем. */
    private fun state(): String {
        val sb = StringBuilder().append(version).append(',').append(target.scrollY).append(',').append(active())
        if (target is AbsListView) sb.append(',').append(target.firstVisiblePosition).append(',')
            .append(target.getChildAt(0)?.top ?: 0).append(',').append(target.childCount)
        return sb.toString()
    }

    override fun onPreDraw(): Boolean {
        if (width <= 0) return true
        val now = state()
        if (now != shown) {
            shown = now
            visibility = if (active()) VISIBLE else INVISIBLE
            invalidate()
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        if (width <= 0 || height <= 0) return
        val w = width.toFloat(); val h = height.toFloat()
        if (base != null && c.isHardwareAccelerated) {
            // то, что под полосой, — в отдельный слой (с учётом прокрутки самого списка)
            getLocationInWindow(me); target.getLocationInWindow(them)
            val rc = base.beginRecording()
            rc.translate((them[0] - me[0] - target.scrollX).toFloat(), (them[1] - me[1] - target.scrollY).toFloat())
            target.draw(rc)
            base.endRecording()
            for ((i, node) in levels.withIndex()) {
                if (!node.hasDisplayList()) { val lc = node.beginRecording(); lc.drawRenderNode(base); node.endRecording() }
                val save = c.saveLayer(0f, 0f, w, h, null)
                c.drawRenderNode(node)
                maskP.shader = masks.getOrNull(i)
                c.drawRect(0f, 0f, w, h, maskP)
                c.restoreToCount(save)
            }
        }
        c.drawRect(0f, 0f, w, h, tintP)
    }
}
