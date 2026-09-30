package io.github.xtratter.appshelf

import android.animation.TimeInterpolator
import android.app.Dialog
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp

/**
 * Анимации «жидкого стекла»: стекло ведёт себя как капля — при касании надувается вокруг пальца и отпружинивает,
 * окна вытекают из места нажатия, строка поиска — из кнопки-лупы. Работает, только пока включено «жидкое стекло».
 */
object Motion {
    /** Пружина: быстро к цели, лёгкий перелёт и затухающее покачивание. */
    class Spring(private val decay: Float = 5.5f, private val cycles: Float = 1.25f) : TimeInterpolator {
        override fun getInterpolation(t: Float): Float =
            if (t >= 1f) 1f else (1f - exp(-decay * t) * cos(2 * PI.toFloat() * cycles * t))
    }

    /** Последнее касание экрана (координаты на экране) — отсюда вытекают окна. */
    var lastX = -1f
    var lastY = -1f

    fun touched(e: MotionEvent) {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { lastX = e.rawX; lastY = e.rawY }
    }

    /**
     * Стекло «надувается» под пальцем: вид чуть растёт вокруг точки касания, а отпущенный — отпружинивает.
     * Касание не перехватывается — нажатия и прокрутка работают как обычно.
     */
    fun press(v: View) {
        if (!Ui.liquid) return
        v.setOnTouchListener { view, e ->
            touched(e)   // касания внутри окон тоже запоминаем: следующее окно вытечет отсюда
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.pivotX = e.x
                    view.pivotY = e.y
                    // маленькие кнопки надуваются заметнее, широкие карточки — чуть-чуть
                    val grow = 1f + (Ui.dp(view.context, 10f) / view.width.coerceAtLeast(1)).coerceIn(0.02f, 0.08f)
                    view.animate().scaleX(grow).scaleY(grow).setDuration(140).setInterpolator(DecelerateInterpolator()).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(520).setInterpolator(Spring()).start()
            }
            false
        }
    }

    /** То же для всех нажимаемых стеклянных элементов внутри [root] (кнопки и карточки окна). */
    fun pressAll(root: View) {
        if (!Ui.liquid) return
        if (root.isClickable && (root.background is GlassDrawable || root.foreground != null)) press(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) pressAll(root.getChildAt(i))
    }

    /** Окно вытекает из места последнего касания: растёт из этой точки с пружинкой. */
    fun popIn(d: Dialog) {
        if (!Ui.liquid) return
        val decor = d.window?.decorView ?: return
        decor.alpha = 0f
        decor.post {
            val at = IntArray(2)
            decor.getLocationOnScreen(at)
            decor.pivotX = if (lastX >= 0) (lastX - at[0]).coerceIn(0f, decor.width.toFloat()) else decor.width / 2f
            decor.pivotY = if (lastY >= 0) (lastY - at[1]).coerceIn(0f, decor.height.toFloat()) else decor.height / 2f
            decor.scaleX = 0.55f
            decor.scaleY = 0.45f
            decor.animate().alpha(1f).setDuration(160).start()
            decor.animate().scaleX(1f).scaleY(1f).setDuration(560).setInterpolator(Spring()).start()
        }
    }

    /** Строка поиска вытекает из кнопки-лупы ([fromX] — её центр по горизонтали в координатах панели). */
    fun openSearch(box: View, fromX: Float) {
        box.visibility = View.VISIBLE
        if (!Ui.liquid) return
        box.pivotX = fromX
        box.pivotY = 0f
        box.scaleX = 0.12f
        box.scaleY = 0.6f
        box.alpha = 0f
        box.animate().alpha(1f).setDuration(140).start()
        box.animate().scaleX(1f).scaleY(1f).setDuration(600).setInterpolator(Spring()).start()
    }

    /** И втекает обратно в лупу; [done] — когда спрятана. */
    fun closeSearch(box: View, toX: Float, done: () -> Unit) {
        if (!Ui.liquid) { box.visibility = View.GONE; done(); return }
        box.pivotX = toX
        box.animate().scaleX(0.12f).scaleY(0.6f).alpha(0f).setDuration(220).setInterpolator(DecelerateInterpolator())
            .withEndAction {
                box.visibility = View.GONE
                box.scaleX = 1f; box.scaleY = 1f; box.alpha = 1f
                done()
            }.start()
    }
}
