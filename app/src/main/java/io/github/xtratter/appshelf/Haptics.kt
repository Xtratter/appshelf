package io.github.xtratter.appshelf

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

/**
 * Отклик вибрацией, как Taptic Engine: короткие чёткие щелчки разного характера для разных действий.
 * Сила — в настройке (меню ⋮ → «Вибрация»), «Выключена» — никакой вибрации.
 * На Android 11+ — «примитивы» с управляемой силой (если телефон их умеет), иначе — короткий импульс нужной силы.
 */
object Haptics {
    /** Уровни силы: 0 — выключено. */
    enum class Level(val title: Int, val scale: Float) {
        OFF(R.string.hap_off, 0f),
        LIGHT(R.string.hap_light, 0.4f),
        MEDIUM(R.string.hap_medium, 0.7f),
        STRONG(R.string.hap_strong, 1f),
    }

    enum class Kind { TAP, TICK, OPEN, CLOSE, SUCCESS, ERROR }

    private var vibrator: Vibrator? = null
    private var level = Level.MEDIUM

    fun init(ctx: Context) {
        val app = ctx.applicationContext
        vibrator = if (Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
        level = Prefs(app).haptics()
    }

    fun setLevel(ctx: Context, l: Level) {
        Prefs(ctx).haptics = l.name
        level = l
    }

    fun level() = level

    /** Есть ли в телефоне вибромотор. */
    fun available() = vibrator?.hasVibrator() == true

    fun play(kind: Kind) {
        val v = vibrator ?: return
        val s = level.scale
        if (s <= 0f || !v.hasVibrator()) return
        try {
            v.vibrate(composed(v, kind, s) ?: simple(v, kind, s))
        } catch (e: Exception) {
            // вибрация — не главное: не получилось, и ладно
        }
    }

    /** Чёткие примитивы (Android 11+), если телефон умеет все нужные. */
    private fun composed(v: Vibrator, kind: Kind, s: Float): VibrationEffect? {
        if (Build.VERSION.SDK_INT < 30) return null
        val C = VibrationEffect.Composition.PRIMITIVE_CLICK
        val T = VibrationEffect.Composition.PRIMITIVE_TICK
        val rise = if (Build.VERSION.SDK_INT >= 31) VibrationEffect.Composition.PRIMITIVE_QUICK_RISE else C
        val fall = if (Build.VERSION.SDK_INT >= 31) VibrationEffect.Composition.PRIMITIVE_QUICK_FALL else T
        val thud = if (Build.VERSION.SDK_INT >= 31) VibrationEffect.Composition.PRIMITIVE_THUD else C
        val parts: List<Triple<Int, Float, Int>> = when (kind) {   // примитив, сила, пауза перед ним (мс)
            Kind.TAP -> listOf(Triple(C, s, 0))
            Kind.TICK -> listOf(Triple(T, s, 0))
            Kind.OPEN -> listOf(Triple(rise, s * 0.8f, 0), Triple(C, s * 0.6f, 20))
            Kind.CLOSE -> listOf(Triple(fall, s * 0.8f, 0))
            Kind.SUCCESS -> listOf(Triple(C, s * 0.7f, 0), Triple(C, s, 90))
            Kind.ERROR -> listOf(Triple(thud, s, 0), Triple(thud, s * 0.7f, 110))
        }
        if (!v.areAllPrimitivesSupported(*parts.map { it.first }.distinct().toIntArray())) return null
        val c = VibrationEffect.startComposition()
        for ((p, scale, delay) in parts) c.addPrimitive(p, scale.coerceIn(0f, 1f), delay)
        return c.compose()
    }

    /** Короткий импульс нужной силы (где нет примитивов). */
    private fun simple(v: Vibrator, kind: Kind, s: Float): VibrationEffect {
        val amp = if (v.hasAmplitudeControl()) (s * 255).toInt().coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
        return when (kind) {
            Kind.TAP -> VibrationEffect.createOneShot(12, amp)
            Kind.TICK -> VibrationEffect.createOneShot(6, (amp * 0.6f).toInt().coerceAtLeast(1))
            Kind.OPEN -> VibrationEffect.createOneShot(18, amp)
            Kind.CLOSE -> VibrationEffect.createOneShot(10, (amp * 0.7f).toInt().coerceAtLeast(1))
            Kind.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 12, 80, 14), intArrayOf(0, amp, 0, amp), -1)
            Kind.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 30, 90, 30), intArrayOf(0, amp, 0, amp), -1)
        }
    }

    /**
     * Щелчок при нажатии на [v] — в момент, когда палец отпускает кнопку (как само нажатие);
     * если начали прокручивать, щелчка нет. Касание не перехватывается.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun onClick(v: View, kind: Kind = Kind.TAP) {
        v.setOnTouchListener { view, e ->
            if (e.actionMasked == MotionEvent.ACTION_UP && view.isPressed &&
                e.x >= 0 && e.y >= 0 && e.x <= view.width && e.y <= view.height) play(kind)
            false
        }
    }

    /** Щелчки для всех нажимаемых элементов внутри [root] (окна): кнопки — щелчок, галочки и переключатели — тик. */
    fun attachAll(root: View) {
        if (root.isClickable && root !is ViewGroup || (root is ViewGroup && root.isClickable && root.hasOnClickListeners()))
            onClick(root, if (root is android.widget.CompoundButton) Kind.TICK else Kind.TAP)
        if (root is ViewGroup) for (i in 0 until root.childCount) attachAll(root.getChildAt(i))
    }
}
