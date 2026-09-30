package io.github.xtratter.appshelf

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.ViewGroup

/**
 * Отклик вибрацией, как Taptic Engine: короткие чёткие щелчки разного характера для разных действий.
 * Сила — в настройке (меню ⋮ → «Вибрация»), «Выключена» — никакой вибрации.
 * Сначала — готовые эффекты производителя (они настроены под мотор: у линейного мотора это чёткий «стук»),
 * иначе — «примитивы» Android 11+ с управляемой силой, иначе — короткий импульс нужной силы.
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

    /** Способ вибрации: авто — примитивы (настраиваемая сила), иначе эффекты производителя, иначе импульс. */
    enum class Engine(val title: Int) {
        AUTO(R.string.hap_eng_auto), EFFECTS(R.string.hap_eng_effects), PRIMITIVES(R.string.hap_eng_primitives),
        SIMPLE(R.string.hap_eng_simple)
    }

    private var engine = Engine.AUTO

    private var vibrator: Vibrator? = null
    private var level = Level.MEDIUM

    fun init(ctx: Context) {
        val app = ctx.applicationContext
        vibrator = if (Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
        level = Prefs(app).haptics()
        engine = runCatching { Engine.valueOf(Prefs(app).hapticsEngine) }.getOrDefault(Engine.AUTO)
    }

    fun engineChoice() = engine

    fun setEngine(ctx: Context, e: Engine) {
        Prefs(ctx).hapticsEngine = e.name
        engine = e
    }

    /** Поддерживает ли телефон способ [e] (для пометок в настройке). */
    fun supports(e: Engine): Boolean {
        val v = vibrator ?: return false
        if (!v.hasVibrator()) return false
        return when (e) {
            Engine.AUTO, Engine.SIMPLE -> true
            Engine.EFFECTS -> predefinedSupported(v)
            Engine.PRIMITIVES -> primitivesSupported(v)
        }
    }

    private fun primitivesSupported(v: Vibrator) = Build.VERSION.SDK_INT >= 30 &&
        v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK, VibrationEffect.Composition.PRIMITIVE_TICK)

    fun setLevel(ctx: Context, l: Level) {
        Prefs(ctx).haptics = l.name
        level = l
    }

    fun level() = level

    /** Есть ли в телефоне вибромотор. */
    fun available() = vibrator?.hasVibrator() == true

    /** Каким способом на деле вибрирует телефон при выбранном [engine] (для «Авто» — лучший из поддерживаемых). */
    fun effective(): Engine {
        val v = vibrator ?: return Engine.SIMPLE
        return when (engine) {
            Engine.AUTO -> if (primitivesSupported(v)) Engine.PRIMITIVES else if (predefinedSupported(v)) Engine.EFFECTS else Engine.SIMPLE
            Engine.PRIMITIVES -> if (primitivesSupported(v)) Engine.PRIMITIVES else Engine.SIMPLE
            Engine.EFFECTS -> if (predefinedSupported(v)) Engine.EFFECTS else Engine.SIMPLE
            Engine.SIMPLE -> Engine.SIMPLE
        }
    }

    /** Можно ли менять силу импульса (амплитуду) — тогда у простого импульса уровень меняет силу. */
    fun amplitude() = vibrator?.hasAmplitudeControl() == true

    /**
     * Готовые эффекты производителя звучат с одной силой, заданной прошивкой, поэтому для них уровень меняет,
     * на что откликается вибрация: лёгкая — только окна, успех и ошибки; средняя — плюс кнопки; сильная — плюс
     * чипы, галочки и ползунок, а открытие окна — двойным щелчком.
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
            // вибрация — не главное: не получилось, и ладно
        }
    }

    /** Чёткие примитивы (Android 11+), если телефон умеет все нужные. */
    private fun composed(v: Vibrator, kind: Kind, s: Float): VibrationEffect? {
        if (!primitivesSupported(v)) return null
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

    private val PREDEFINED = intArrayOf(VibrationEffect.EFFECT_TICK, VibrationEffect.EFFECT_CLICK,
        VibrationEffect.EFFECT_HEAVY_CLICK, VibrationEffect.EFFECT_DOUBLE_CLICK)

    /** Телефон умеет готовые эффекты производителя (настроены под его мотор — чёткий «стук» у линейного мотора). */
    private fun predefinedSupported(v: Vibrator) = Build.VERSION.SDK_INT >= 30 &&
        v.areAllEffectsSupported(*PREDEFINED) == Vibrator.VIBRATION_EFFECT_SUPPORT_YES

    /**
     * Готовые эффекты производителя: чёткий стук, но силу задаёт прошивка (у разных телефонов «тяжёлый» бывает
     * даже слабее обычного) — поэтому уровень силы на них не влияет, только «Выключена».
     */
    private fun predefined(v: Vibrator, kind: Kind): VibrationEffect? {
        if (!predefinedSupported(v)) return null
        return VibrationEffect.createPredefined(when (kind) {
            Kind.TAP -> VibrationEffect.EFFECT_CLICK
            Kind.OPEN -> if (level == Level.STRONG) VibrationEffect.EFFECT_DOUBLE_CLICK else VibrationEffect.EFFECT_CLICK
            Kind.TICK, Kind.CLOSE -> VibrationEffect.EFFECT_TICK
            Kind.SUCCESS -> VibrationEffect.EFFECT_DOUBLE_CLICK
            Kind.ERROR -> VibrationEffect.EFFECT_DOUBLE_CLICK
        })
    }


    /** Короткий импульс нужной силы (где нет ни примитивов, ни готовых эффектов). */
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
    fun onClick(v: View, kind: Kind = Kind.TAP) {
        // один обработчик касаний на вид: щелчок и упругое нажатие стекла (Motion.touch)
        Motion.touch(v, kind, Motion.elasticOf(v))
    }

    /** Щелчки для всех нажимаемых элементов внутри [root] (окна): кнопки — щелчок, галочки и переключатели — тик. */
    fun attachAll(root: View) {
        if (root.isClickable && root !is ViewGroup || (root is ViewGroup && root.isClickable && root.hasOnClickListeners()))
            onClick(root, if (root is android.widget.CompoundButton) Kind.TICK else Kind.TAP)
        if (root is ViewGroup) for (i in 0 until root.childCount) attachAll(root.getChildAt(i))
    }
}
