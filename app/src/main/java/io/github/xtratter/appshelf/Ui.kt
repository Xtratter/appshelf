package io.github.xtratter.appshelf

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.WindowManager

/** Цвета, шрифты и «стеклянные» элементы оформления. */
object Ui {
    // Цвета текущей темы. Material You: на Android 12+ акценты берём из обоев.
    var primary = 0xFFA8C7FA.toInt(); private set
    var secondary = 0xFFBFC6DC.toInt(); private set
    var tertiary = 0xFFD7BAFF.toInt(); private set
    /** Фон окна. */
    var base = 0xFF0D0F14.toInt(); private set
    /** Светлая тема: тёмный текст на светлом. */
    var light = false; private set
    /** Размытые цветные пятна на фоне (иначе — ровный фон). */
    var aurora = true; private set
    /** Цвета пятен фона и их яркость. */
    var auroraColors = intArrayOf(primary, tertiary, secondary); private set
    var auroraStrength = 1f; private set
    /** Тема «Жидкое стекло» и она поддерживается (Android 13+: шейдеры AGSL). */
    var liquid = false; private set
    /** AMOLED: кнопки не цветные, а чёрные с окантовкой. */
    var amoled = false; private set
    /** Фон окна для стеклянных карточек: картинка пятен (в 1/4 размера) и размер окна; корень главного окна. */
    var backdrop: android.graphics.Bitmap? = null
    var backdropW = 0f
    var backdropH = 0f
    var liquidRoot: View? = null
    /** Прозрачность «жидкого стекла»: 0 — матовое, 1 — прозрачное. */
    var clarity = 0.5f; private set
    private fun lerp(a: Float, b: Float) = a + (b - a) * clarity
    /** Насколько плотна заливка верхней панели, и насколько размыто то, что под ней и за окнами (dp). */
    val barTintAlpha get() = lerp(0.5f, 0.1f)
    val barBlurDp get() = lerp(4f, 0.3f)
    val dialogBlurDp get() = lerp(12f, 1.5f)

    /** Поменять прозрачность стекла на ходу (из настройки): заливка окон пересчитывается сразу. */
    fun setClarity(v: Float) {
        clarity = v.coerceIn(0f, 1f)
        if (liquid) dialogBlur = withAlpha(dialogSolid, lerp(0.62f, 0.12f))
    }

    /** «Жидкое стекло» поверх темы: ровный фон (его картинку преломляют карточки) и заливка окон по прозрачности. */
    private fun liquidOverTheme() {
        if (!liquid) return
        aurora = true
        dialogBlur = withAlpha(dialogSolid, lerp(0.62f, 0.12f))
    }
    /** Снимок главного экрана для стеклянных окон (в 1/4 размера) и где он на экране. */
    var snapshot: android.graphics.Bitmap? = null
    var snapshotX = 0f
    var snapshotY = 0f
    var snapshotW = 0f
    var snapshotH = 0f

    /** Заливка стеклянного окна. */
    fun dialogBlurColor() = dialogBlur

    /** Цвет [top] поверх [bottom] (оба могут быть полупрозрачными). */
    fun over(top: Int, bottom: Int): Int {
        val ta = (top ushr 24) / 255f
        val ba = (bottom ushr 24) / 255f
        val a = ta + ba * (1 - ta)
        if (a <= 0f) return 0
        fun ch(s: Int) = ((((top shr s) and 0xFF) * ta + ((bottom shr s) and 0xFF) * ba * (1 - ta)) / a).toInt().coerceIn(0, 255)
        return ((a * 255).toInt() shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private var snapshotRaw: android.graphics.Bitmap? = null

    /** Копия снимка, плавно размытая под текущую прозрачность (без зерна, как системное размытие). */
    private fun blurred(ctx: Context, raw: android.graphics.Bitmap): android.graphics.Bitmap {
        val out = raw.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        Blur.apply(out, (dp(ctx, dialogBlurDp) * raw.width / snapshotW.coerceAtLeast(1f)).toInt().coerceAtLeast(1))
        return out
    }

    /** Размыть снимок заново под текущую прозрачность; в фоновом потоке. null — снимка нет. */
    fun reblurredSnapshot(ctx: Context): android.graphics.Bitmap? = snapshotRaw?.let { blurred(ctx, it) }

    /** Снять главный экран (для окон «жидкого стекла»): под окном будет видно, как за ним преломляется список. */
    fun takeSnapshot() {
        val root = liquidRoot ?: return
        if (!liquid || root.width <= 0 || root.height <= 0) return
        val q = 2
        // всегда новая картинка: прежнюю, возможно, ещё рисует окно на экране
        val bmp = android.graphics.Bitmap.createBitmap(root.width / q, root.height / q, android.graphics.Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(1f / q, 1f / q)
        Liquid.capturing = true
        try { root.draw(c) } catch (e: Exception) { return } finally { Liquid.capturing = false }
        // неразмытый снимок храним: при смене прозрачности размытие пересчитывается из него, без перерисовки экрана
        snapshotRaw = bmp
        snapshotW = root.width.toFloat(); snapshotH = root.height.toFloat()
        val blurred = blurred(root.context, bmp)
        val at = IntArray(2)
        root.getLocationOnScreen(at)
        snapshot = blurred
        snapshotX = at[0].toFloat(); snapshotY = at[1].toFloat()
        snapshotW = root.width.toFloat(); snapshotH = root.height.toFloat()
    }

    var TEXT = 0xFFF2F2F6.toInt(); private set
    var TEXT2 = 0xB3F2F2F6.toInt(); private set
    var TEXT3 = 0x73F2F2F6; private set
    var ON_ACCENT = 0xFF10131A.toInt(); private set
    var WARN = 0xFFFFC857.toInt(); private set
    var HOT = 0xFFFF6B6B.toInt(); private set
    var OK = 0xFF7EE08A.toInt(); private set
    var TRACK = 0x1AFFFFFF; private set
    /** Заливка стеклянных карточек. */
    var card = 0x14FFFFFF; private set
    /** Заливка диалогов: с размытием под ними и без. */
    var dialogBlur = 0x9E16181E.toInt(); private set
    var dialogSolid = 0xF016181E.toInt(); private set
    /** Непрозрачный фон плашек и панели. */
    var surface = 0xFF16181E.toInt(); private set
    /** Предупреждение: фон и текст плашки-подсказки. */
    var hintFill = 0x33FFC857; private set
    var hintText = 0xFFFFE6A8.toInt(); private set
    private var inkColor = 0xFFFFFFFF.toInt()

    /** Полупрозрачный «чернильный» цвет: белый в тёмных темах, чёрный в светлой (для линий, дорожек, кромок). */
    fun ink(alpha: Int) = (inkColor and 0xFFFFFF) or (alpha shl 24)

    // шрифты интерфейса — по выбранному шрифту (applyFont)
    var medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); private set
    var bold: Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 700, false) else Typeface.DEFAULT_BOLD
        private set
    var regular: Typeface = Typeface.DEFAULT; private set

    /** Выбрать шрифт: обычное, среднее и жирное начертания для надписей, которые рисуются вручную. */
    fun applyFont(ctx: Context, f: AppFont) {
        fun weights(base: Typeface) {
            if (Build.VERSION.SDK_INT >= 28) {
                regular = Typeface.create(base, 400, false)
                medium = Typeface.create(base, 500, false)
                bold = Typeface.create(base, 700, false)
            } else {
                regular = base; medium = base; bold = Typeface.create(base, Typeface.BOLD)
            }
        }
        when (f) {
            AppFont.CASCADIA -> weights(ctx.resources.getFont(R.font.cascadia))
            AppFont.MONO -> weights(Typeface.MONOSPACE)
            AppFont.SYSTEM -> {
                regular = Typeface.DEFAULT
                medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                bold = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 700, false) else Typeface.DEFAULT_BOLD
            }
        }
    }

    /** Какая тема применена сейчас (null — ещё никакая). */
    var theme: Theme? = null; private set
    private var night = true

    fun init(ctx: Context) {
        if (theme == null) apply(ctx, Prefs(ctx).theme())
    }

    /** Тема [t] с учётом системного тёмного режима (для [Theme.SYSTEM]). */
    fun resolve(ctx: Context, t: Theme): Theme {
        if (t != Theme.SYSTEM) return t
        val mode = ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return if (mode == android.content.res.Configuration.UI_MODE_NIGHT_NO) Theme.LIGHT else Theme.STANDARD
    }

    /** Применена ли уже тема [t] (с учётом системного режима). */
    fun isCurrent(ctx: Context, t: Theme) = theme == t && (t != Theme.SYSTEM || nightNow(ctx) == night) &&
        liquid == (Prefs(ctx).liquidGlass && Liquid.works)

    private fun nightNow(ctx: Context) = resolve(ctx, Theme.SYSTEM) != Theme.LIGHT

    fun apply(ctx: Context, t: Theme) {
        theme = t
        night = nightNow(ctx)
        val r = resolve(ctx, t)
        val you = Build.VERSION.SDK_INT >= 31
        fun c(id: Int) = ctx.getColor(id)
        light = r == Theme.LIGHT
        amoled = r == Theme.AMOLED
        clarity = Prefs(ctx).glassClarity / 100f
        liquid = Prefs(ctx).liquidGlass && Liquid.works
        if (light) {
            primary = if (you) c(android.R.color.system_accent1_600) else 0xFF3B5BA9.toInt()
            secondary = if (you) c(android.R.color.system_accent2_600) else 0xFF565E71.toInt()
            tertiary = if (you) c(android.R.color.system_accent3_600) else 0xFF7A4E9E.toInt()
            base = if (you) mix(c(android.R.color.system_neutral1_50), c(android.R.color.system_accent1_50), 0.4f) else 0xFFF1F3F9.toInt()
            aurora = true
            auroraColors = if (you) intArrayOf(c(android.R.color.system_accent1_200), c(android.R.color.system_accent3_200),
                c(android.R.color.system_accent2_200)) else intArrayOf(0xFFA8C7FA.toInt(), 0xFFD7BAFF.toInt(), 0xFFBFC6DC.toInt())
            auroraStrength = 0.9f
            inkColor = 0xFF000000.toInt()
            TEXT = 0xFF1A1C22.toInt(); TEXT2 = 0xB31A1C22.toInt(); TEXT3 = 0x7A1A1C22
            ON_ACCENT = 0xFFFFFFFF.toInt()
            WARN = 0xFFB26A00.toInt(); HOT = 0xFFD32F2F.toInt(); OK = 0xFF2E7D32.toInt()
            TRACK = 0x14000000
            card = 0xA6FFFFFF.toInt()
            dialogBlur = 0xC8F7F8FC.toInt(); dialogSolid = 0xFAF7F8FC.toInt()
            surface = 0xFFF7F8FC.toInt()
            hintFill = 0x33FFB300; hintText = 0xFF6D4C00.toInt()
            liquidOverTheme()
            return
        }
        // тёмные темы
        inkColor = 0xFFFFFFFF.toInt()
        TEXT = 0xFFF2F2F6.toInt(); TEXT2 = 0xB3F2F2F6.toInt(); TEXT3 = 0x73F2F2F6
        ON_ACCENT = 0xFF10131A.toInt()
        WARN = 0xFFFFC857.toInt(); HOT = 0xFFFF6B6B.toInt(); OK = 0xFF7EE08A.toInt()
        TRACK = 0x1AFFFFFF
        hintFill = 0x33FFC857; hintText = 0xFFFFE6A8.toInt()
        primary = if (you) c(android.R.color.system_accent1_200) else 0xFFA8C7FA.toInt()
        secondary = if (you) c(android.R.color.system_accent2_200) else 0xFFBFC6DC.toInt()
        tertiary = if (you) c(android.R.color.system_accent3_200) else 0xFFD7BAFF.toInt()
        aurora = false
        auroraStrength = 1f
        when (r) {
            Theme.AMOLED -> {
                ON_ACCENT = TEXT   // кнопки чёрные — текст на них светлый
                base = 0xFF000000.toInt()
                card = 0x0DFFFFFF
                dialogBlur = 0xE6000000.toInt(); dialogSolid = 0xFA050505.toInt()
                surface = 0xFF000000.toInt()
            }
            Theme.GRAPHITE -> {
                primary = 0xFFB0BEC5.toInt(); secondary = 0xFF90A4AE.toInt(); tertiary = 0xFFCFD8DC.toInt()
                base = 0xFF1B1C1F.toInt()
                card = 0x12FFFFFF
                dialogBlur = 0xB0242529.toInt(); dialogSolid = 0xF5242529.toInt()
                surface = 0xFF242529.toInt()
            }
            else -> {   // стандартная
                base = if (you) mix(c(android.R.color.system_neutral1_900), 0xFF000000.toInt(), 0.35f) else 0xFF0D0F14.toInt()
                aurora = true
                card = 0x14FFFFFF
                dialogBlur = 0x9E16181E.toInt(); dialogSolid = 0xF016181E.toInt()
                surface = 0xFF16181E.toInt()
            }
        }
        auroraColors = intArrayOf(primary, tertiary, secondary)
        liquidOverTheme()
    }

    fun withAlpha(color: Int, a: Float) = (color and 0xFFFFFF) or ((a * 255).toInt().coerceIn(0, 255) shl 24)

    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - t) + ((b shr s) and 0xFF) * t).toInt() shl s
        return ch(24) or ch(16) or ch(8) or ch(0)
    }

    fun dp(ctx: Context, v: Float) = v * ctx.resources.displayMetrics.density
    fun sp(ctx: Context, v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, ctx.resources.displayMetrics)

    fun textPaint(ctx: Context, sizeSp: Float, face: Typeface = regular, color: Int = TEXT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sp(ctx, sizeSp)
            typeface = face
            this.color = color
            fontFeatureSettings = "tnum"   // цифры одинаковой ширины — числа не «прыгают»
        }

    /** Обрезать строку с многоточием под ширину. */
    fun ellipsize(p: Paint, s: String, width: Float): String {
        if (p.measureText(s) <= width) return s
        val n = p.breakText(s, true, (width - p.measureText("…")).coerceAtLeast(0f), null)
        return s.take(n) + "…"
    }

    /** Эффект нажатия со скруглением. */
    fun ripple(ctx: Context, radiusDp: Float, insetH: Float = 0f, insetV: Float = 0f): Drawable {
        val mask = GradientDrawable().apply { cornerRadius = dp(ctx, radiusDp); setColor(-1) }
        return RippleDrawable(ColorStateList.valueOf(ink(0x33)), null,
            InsetDrawable(mask, insetH.toInt(), insetV.toInt(), insetH.toInt(), insetV.toInt()))
    }

    /** Кнопка-«пилюля»; в теме «Жидкое стекло» — стеклянная (цветная заливка становится цветным стеклом). */
    fun pill(ctx: Context, fill: Int, stroke: Int = 0, radiusDp: Float = 100f): Drawable {
        // AMOLED: залитая акцентом кнопка — чёрная с окантовкой (текст на ней — светлый, см. ON_ACCENT)
        val black = amoled && fill == primary
        val f = if (black) 0xFF000000.toInt() else fill
        val s = if (black) ink(0x73) else stroke
        return if (liquid) GlassDrawable(ctx, radiusDp, f) else GradientDrawable().apply {
            cornerRadius = dp(ctx, radiusDp)
            setColor(f)
            if (s != 0) setStroke(dp(ctx, 1f).toInt().coerceAtLeast(1), s)
        }
    }

    private val dialogs = HashSet<View>()
    /** Сколько «стеклянных» диалогов сейчас открыто. */
    val openDialogs get() = dialogs.size
    /** Новый экран: диалоги старого (например, до смены темы) больше не считаем. */
    fun forgetDialogs() = dialogs.clear()
    /** Вызывается, когда закрылся последний диалог. */
    var onDialogsClosed: (() -> Unit)? = null

    /** Убрать фон у служебных панелей диалога и у рамок между окном и содержимым (наше содержимое не трогаем). */
    private fun clearPanels(decor: View) {
        val res = decor.resources
        for (name in listOf("parentPanel", "topPanel", "title_template", "contentPanel", "scrollView",
            "customPanel", "custom", "buttonPanel")) {
            val id = res.getIdentifier(name, "id", "android")
            if (id != 0) decor.findViewById<View>(id)?.background = null
        }
        var v: View? = decor.findViewById<View>(android.R.id.content)
        while (v != null && v !== decor) {
            v.background = null
            v = v.parent as? View
        }
    }

    /** Оформить диалог стеклом; на Android 12+ ещё и размыть то, что под ним. */
    fun glassDialog(d: AlertDialog) {
        val w = d.window ?: return
        // первое окно поверх главного экрана — снимаем экран, чтобы стекло окна его преломляло
        if (liquid && dialogs.isEmpty()) takeSnapshot()
        // под размытым диалогом главный экран не обновляем: каждое его изменение заставляет
        // систему заново размывать весь экран, а под стеклом всё равно ничего не разобрать
        dialogs += w.decorView
        w.decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                if (dialogs.remove(v) && dialogs.isEmpty()) onDialogsClosed?.invoke()
            }
        })
        val ctx = d.context
        val blur = Build.VERSION.SDK_INT >= 31 &&
            ctx.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled
        w.setBackgroundDrawable(GlassDrawable(ctx, 28f, if (blur) dialogBlur else dialogSolid))
        if (blur && Build.VERSION.SDK_INT >= 31) {
            // размываем весь экран позади; отдельное размытие под самим окном не включаем —
            // система размывает прямоугольник, и внутри скруглённого стекла была видна «рамка»
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            w.attributes = w.attributes.apply { blurBehindRadius = dp(ctx, 10f).toInt() }
        }
        w.setDimAmount(0.35f)
        // стекло полупрозрачное: системная тень окна просвечивала у краёв, а внутренние панели диалога
        // могли иметь свою подложку — вместе это выглядело как светлая «рамка» внутри окна
        w.setElevation(0f)
        clearPanels(w.decorView)
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)
            .forEach { d.getButton(it)?.setTextColor(primary) }
    }
}

/**
 * «Жидкое стекло»: полупрозрачная заливка, мягкий блик сверху и светлая кромка,
 * которая ярче в верхнем левом углу — как свет на гранях стекла.
 */
class GlassDrawable(ctx: Context, radiusDp: Float, private val fill: Int = Ui.card) : Drawable() {
    private val radius = Ui.dp(ctx, radiusDp)
    private val d = ctx.resources.displayMetrics.density
    /** Вид, на котором рисуется стекло, если это не его фон (строка списка рисует стекло сама). */
    var host: View? = null
    private val liquid = if (Ui.liquid && Build.VERSION.SDK_INT >= 33) LiquidCard(d) else null
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val hiP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = d }
    private val r = RectF()

    override fun onBoundsChange(b: Rect) {
        r.set(b)
        r.inset(d / 2, d / 2)
        // в светлой теме блик и светлая кромка белые, а тень кромки — чуть тёмная
        hiP.shader = LinearGradient(0f, r.top, 0f, r.top + minOf(r.height(), 90 * d),
            if (Ui.light) 0x66FFFFFF else 0x16FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        val edge = if (Ui.light) intArrayOf(0xFFFFFFFF.toInt(), 0x12000000, 0x12000000, 0x1F000000)
        else intArrayOf(0x70FFFFFF, 0x12FFFFFF, 0x12FFFFFF, 0x3DFFFFFF)
        edgeP.shader = LinearGradient(r.left, r.top, r.right, r.bottom,
            edge, floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(c: Canvas) {
        // «жидкое стекло»: линза над фоном окна; не вышло (диалог, фон не готов) — обычное стекло
        val v = callback as? View ?: host
        if (liquid != null && v != null && liquid.draw(c, v, r, radius, fill)) return
        if (liquid != null && v != null && liquid.drawOverSnapshot(c, v, r, radius, fill)) return
        if (liquid != null && liquid.drawRim(c, r, radius, fill)) return
        c.drawRoundRect(r, radius, radius, fillP)
        c.drawRoundRect(r, radius, radius, hiP)
        c.drawRoundRect(r, radius, radius, edgeP)
    }

    override fun getOutline(outline: Outline) = outline.setRoundRect(bounds, minOf(radius, bounds.height() / 2f, bounds.width() / 2f))
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Фон окна: тёмная основа и размытые цветные пятна из палитры обоев — их «преломляет» стекло. */
class AuroraDrawable : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobs = ArrayList<Triple<Float, Float, Float>>()
    private val shaders = ArrayList<Shader>()
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var bmp: android.graphics.Bitmap? = null

    override fun onBoundsChange(b: Rect) {
        if (b.isEmpty) return
        // пятна мягкие, поэтому рисуем их один раз в картинку в 1/4 размера и потом только растягиваем:
        // так фон почти ничего не стоит при каждом кадре
        val w = b.width() / 4f
        val h = b.height() / 4f
        blobs.clear(); shaders.clear()
        fun blob(x: Float, y: Float, r: Float, color: Int, a: Float) {
            blobs += Triple(x, y, r)
            shaders += RadialGradient(x, y, r, Ui.withAlpha(color, a), Ui.withAlpha(color, 0f), Shader.TileMode.CLAMP)
        }
        val (c1, c2, c3) = Ui.auroraColors
        val k = Ui.auroraStrength
        blob(w * 0.05f, h * 0.08f, w * 0.95f, c1, 0.42f * k)
        blob(w * 1.0f, h * 0.38f, w * 0.85f, c2, 0.30f * k)
        blob(w * 0.1f, h * 0.78f, w * 0.9f, c3, 0.22f * k)
        blob(w * 0.9f, h * 1.02f, w * 0.7f, c1, 0.25f * k)
        // «жидкое стекло»: фон ровный, без пятен — иначе при прокрутке пятна «гуляют» внутри стеклянных карточек
        if (Ui.liquid) { blobs.clear(); shaders.clear() }
        val out = android.graphics.Bitmap.createBitmap(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1),
            android.graphics.Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Ui.base)
        for (i in blobs.indices) {
            p.shader = shaders[i]
            c.drawCircle(blobs[i].first, blobs[i].second, blobs[i].third, p)
        }
        bmp = out
        Ui.backdrop = out
        Ui.backdropW = b.width().toFloat()
        Ui.backdropH = b.height().toFloat()
    }

    override fun draw(c: Canvas) {
        val b = bmp
        if (b == null) c.drawColor(Ui.base) else c.drawBitmap(b, null, bounds, bmpPaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
