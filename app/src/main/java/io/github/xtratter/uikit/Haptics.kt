// Copied from github.com/Xtratter/android-ui-kit (v1.1) — edit there and re-run install.sh
package io.github.xtratter.uikit

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

/**
 * Haptic feedback like a Taptic Engine: short crisp clicks of different character for different actions.
 * Strength levels (OFF…STRONG) and the method are stored in the given SharedPreferences.
 * Methods: Android 11+ primitives (adjustable strength), the vendor's predefined effects (tuned for the motor),
 * or a short one-shot pulse; AUTO picks the best one the phone supports.
 *
 * Usage: `Haptics.init(context, prefs)` once (e.g. in onCreate), then `Haptics.play(Haptics.Kind.TAP)`,
 * `Haptics.onClick(button)` or `Haptics.attachAll(dialogRoot)`.
 */
object Haptics {
    /** Strength levels; OFF — no vibration at all. */
    enum class Level(val scale: Float) { OFF(0f), LIGHT(0.4f), MEDIUM(0.7f), STRONG(1f) }

    enum class Kind { TAP, TICK, OPEN, CLOSE, SUCCESS, ERROR }

    /** AUTO — primitives if supported, else predefined effects, else a pulse. */
    enum class Engine { AUTO, EFFECTS, PRIMITIVES, SIMPLE }

    /** Preference keys (strings with enum names). */
    var levelKey = "haptics"
    var engineKey = "haptics_engine"
    var defaultLevel = Level.MEDIUM

    /** Called on every touch of views set up by [onClick] — e.g. for a press animation. */
    var onTouch: ((View, MotionEvent) -> Unit)? = null

    private var prefs: SharedPreferences? = null
    private var vibrator: Vibrator? = null
    private var level = Level.MEDIUM
    private var engine = Engine.AUTO

    fun init(ctx: Context, prefs: SharedPreferences) {
        val app = ctx.applicationContext
        this.prefs = prefs
        vibrator = if (Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
        level = runCatching { Level.valueOf(prefs.getString(levelKey, null)!!) }.getOrDefault(defaultLevel)
        engine = runCatching { Engine.valueOf(prefs.getString(engineKey, null)!!) }.getOrDefault(Engine.AUTO)
    }

    fun level() = level
    fun setLevel(l: Level) { level = l; prefs?.edit()?.putString(levelKey, l.name)?.apply() }
    fun engineChoice() = engine
    fun setEngine(e: Engine) { engine = e; prefs?.edit()?.putString(engineKey, e.name)?.apply() }

    /** The phone has a vibration motor. */
    fun available() = vibrator?.hasVibrator() == true

    /** Amplitude control — the level then changes the strength of a simple pulse. */
    fun amplitude() = vibrator?.hasAmplitudeControl() == true

    /** Whether the phone supports method [e]. */
    fun supports(e: Engine): Boolean {
        val v = vibrator ?: return false
        if (!v.hasVibrator()) return false
        return when (e) {
            Engine.AUTO, Engine.SIMPLE -> true
            Engine.EFFECTS -> predefinedSupported(v)
            Engine.PRIMITIVES -> primitivesSupported(v)
        }
    }

    /** The method actually used for the chosen [engine] (for AUTO — the best supported one). */
    fun effective(): Engine {
        val v = vibrator ?: return Engine.SIMPLE
        return when (engine) {
            Engine.AUTO -> if (primitivesSupported(v)) Engine.PRIMITIVES else if (predefinedSupported(v)) Engine.EFFECTS else Engine.SIMPLE
            Engine.PRIMITIVES -> if (primitivesSupported(v)) Engine.PRIMITIVES else Engine.SIMPLE
            Engine.EFFECTS -> if (predefinedSupported(v)) Engine.EFFECTS else Engine.SIMPLE
            Engine.SIMPLE -> Engine.SIMPLE
        }
    }

    private fun primitivesSupported(v: Vibrator) = Build.VERSION.SDK_INT >= 30 &&
        v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK, VibrationEffect.Composition.PRIMITIVE_TICK)

    /**
     * Predefined effects have one firmware-defined strength, so for them the level changes what vibrates:
     * light — only windows, success and errors; medium — plus buttons; strong — plus ticks, double click on open.
     */
    private fun effectsAllow(kind: Kind) = when (level) {
        Level.OFF -> false
        Level.LIGHT -> kind == Kind.OPEN || kind == Kind.CLOSE || kind == Kind.SUCCESS || kind == Kind.ERROR
        Level.MEDIUM -> kind != Kind.TICK
        Level.STRONG -> true
    }

    fun play(kind: Kind) {
        val v = vibrator ?: return
        val s = level.scale
        if (s <= 0f || !v.hasVibrator()) return
        try {
            v.vibrate(when (effective()) {
                Engine.PRIMITIVES -> composed(v, kind, s) ?: simple(v, kind, s)
                Engine.EFFECTS -> if (!effectsAllow(kind)) return else predefined(v, kind) ?: simple(v, kind, s)
                else -> simple(v, kind, s)
            })
        } catch (e: Exception) {
            // vibration is not essential
        }
    }

    private fun composed(v: Vibrator, kind: Kind, s: Float): VibrationEffect? {
        if (!primitivesSupported(v)) return null
        val C = VibrationEffect.Composition.PRIMITIVE_CLICK
        val T = VibrationEffect.Composition.PRIMITIVE_TICK
        val rise = if (Build.VERSION.SDK_INT >= 31) VibrationEffect.Composition.PRIMITIVE_QUICK_RISE else C
        val fall = if (Build.VERSION.SDK_INT >= 31) VibrationEffect.Composition.PRIMITIVE_QUICK_FALL else T
        val thud = if (Build.VERSION.SDK_INT >= 31) VibrationEffect.Composition.PRIMITIVE_THUD else C
        val parts: List<Triple<Int, Float, Int>> = when (kind) {   // primitive, scale, delay before it (ms)
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

    private val PREDEFINED = intArrayOf(VibrationEffect.EFFECT_TICK, VibrationEffect.EFFECT_CLICK,
        VibrationEffect.EFFECT_HEAVY_CLICK, VibrationEffect.EFFECT_DOUBLE_CLICK)

    private fun predefinedSupported(v: Vibrator) = Build.VERSION.SDK_INT >= 30 &&
        v.areAllEffectsSupported(*PREDEFINED) == Vibrator.VIBRATION_EFFECT_SUPPORT_YES

    private fun predefined(v: Vibrator, kind: Kind): VibrationEffect? {
        if (!predefinedSupported(v)) return null
        return VibrationEffect.createPredefined(when (kind) {
            Kind.TAP -> VibrationEffect.EFFECT_CLICK
            Kind.OPEN -> if (level == Level.STRONG) VibrationEffect.EFFECT_DOUBLE_CLICK else VibrationEffect.EFFECT_CLICK
            Kind.TICK, Kind.CLOSE -> VibrationEffect.EFFECT_TICK
            Kind.SUCCESS, Kind.ERROR -> VibrationEffect.EFFECT_DOUBLE_CLICK
        })
    }

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
     * A click when [v] is tapped — at the moment the finger lifts inside the view; no click if a scroll started.
     * The touch is not consumed.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    fun onClick(v: View, kind: Kind = Kind.TAP) {
        v.setOnTouchListener { view, e ->
            onTouch?.invoke(view, e)
            if (e.actionMasked == MotionEvent.ACTION_UP && view.isPressed &&
                e.x >= 0 && e.y >= 0 && e.x <= view.width && e.y <= view.height) play(kind)
            false
        }
    }

    /** Clicks for every clickable view inside [root]: buttons — TAP, checkboxes and switches — TICK. */
    fun attachAll(root: View) {
        if (root.isClickable && root !is ViewGroup || (root is ViewGroup && root.isClickable && root.hasOnClickListeners()))
            onClick(root, if (root is android.widget.CompoundButton) Kind.TICK else Kind.TAP)
        if (root is ViewGroup) for (i in 0 until root.childCount) attachAll(root.getChildAt(i))
    }
}
