// Copied from github.com/Xtratter/android-ui-kit (v1.1) — edit there and re-run install.sh
package io.github.xtratter.uikit

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue

/**
 * Light Material 3 Expressive for plain Views, no libraries: a colour scheme (Material You accents from the
 * wallpaper on Android 12+), tonal containers and surfaces, optional transparency, text colours and helpers.
 *
 * Usage: `M3.apply(context, M3.Mode.SYSTEM, translucent = true)` before building views (and again on a theme
 * change), then read colours: `M3.primary`, `M3.surfaceContainer`, `M3.TEXT`… Drawables: [M3Surface], [M3Background];
 * dialogs: [M3Dialog]; press animation and grouped rows: [Expressive]; ready-made widgets: [M3Widgets].
 */
object M3 {
    /** Themes, in the order a theme picker shows them. SYSTEM — dark or light like Android's dark mode. */
    enum class Mode { SYSTEM, LIGHT, DARK, GRAPHITE, AMOLED }

    /** An app's own dark theme on top of DARK (e.g. a classic-terminal one): accents and backgrounds. */
    class Custom(val primary: Int, val secondary: Int, val tertiary: Int, val base: Int, val surface: Int,
                 val card: Int = 0x0FFFFFFF, val dialogBlur: Int = (base and 0xFFFFFF) or (0xE6 shl 24),
                 val dialogSolid: Int = (base and 0xFFFFFF) or (0xFA shl 24))

    /** true — flat tonal surfaces (Expressive); false — the older "glass" look with a highlight and an edge. */
    var expressive = true

    var primary = 0xFFA8C7FA.toInt(); private set
    var secondary = 0xFFBFC6DC.toInt(); private set
    var tertiary = 0xFFD7BAFF.toInt(); private set
    /** Window background. */
    var base = 0xFF0D0F14.toInt(); private set
    var light = false; private set
    /** AMOLED: accent-filled buttons become black with an outline (see [pill]). */
    var amoled = false; private set
    /** Soft colour blobs on the background ([M3Background]) or a flat background. */
    var aurora = true; private set
    var auroraColors = intArrayOf(primary, tertiary, secondary); private set
    var auroraStrength = 1f; private set
    var translucent = true; private set

    var primaryContainer = 0; private set
    var onPrimaryContainer = 0; private set
    var secondaryContainer = 0; private set
    var onSecondaryContainer = 0; private set
    var surfaceContainer = 0; private set
    var surfaceContainerHigh = 0; private set

    var TEXT = 0xFFF2F2F6.toInt(); private set
    var TEXT2 = 0xB3F2F2F6.toInt(); private set
    var TEXT3 = 0x73F2F2F6; private set
    var ON_ACCENT = 0xFF10131A.toInt(); private set
    var WARN = 0xFFFFC857.toInt(); private set
    var HOT = 0xFFFF6B6B.toInt(); private set
    var OK = 0xFF7EE08A.toInt(); private set
    var TRACK = 0x1AFFFFFF; private set
    /** Fill of the default surface ([M3Surface] without a colour). */
    var card = 0x14FFFFFF; private set
    /** Dialog fill: over a blurred screen and without blur. */
    var dialogBlur = 0x9E16181E.toInt(); private set
    var dialogSolid = 0xF016181E.toInt(); private set
    /** Opaque surface for bars, popups, overlays. */
    var surface = 0xFF16181E.toInt(); private set
    var hintFill = 0x33FFC857; private set
    var hintText = 0xFFFFE6A8.toInt(); private set
    private var inkColor = 0xFFFFFFFF.toInt()

    /** How opaque translucent surfaces are (1 — opaque). */
    var surfaceAlpha = 0.8f

    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val bold: Typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 700, false) else Typeface.DEFAULT_BOLD
    val heavy: Typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 800, false) else Typeface.DEFAULT_BOLD
    val regular: Typeface = Typeface.DEFAULT

    /** The applied mode (null — none yet). */
    var mode: Mode? = null; private set
    private var night = true
    private var custom: Custom? = null

    /** Translucent "ink": white in dark themes, black in light ones (lines, tracks, edges). */
    fun ink(alpha: Int) = (inkColor and 0xFFFFFF) or (alpha shl 24)

    fun systemNight(ctx: Context) =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_NO

    /** [m] with Android's dark mode resolved (SYSTEM → LIGHT or DARK). */
    fun resolve(ctx: Context, m: Mode) = if (m != Mode.SYSTEM) m else if (systemNight(ctx)) Mode.DARK else Mode.LIGHT

    /** Is exactly this already applied (for SYSTEM — also the current dark mode)? */
    fun isCurrent(ctx: Context, m: Mode, translucent: Boolean, custom: Custom? = null) =
        mode == m && this.translucent == translucent && this.custom === custom && (m != Mode.SYSTEM || systemNight(ctx) == night)

    fun apply(ctx: Context, m: Mode, translucent: Boolean = true, custom: Custom? = null) {
        mode = m; night = systemNight(ctx); this.translucent = translucent; this.custom = custom
        val r = if (custom != null) Mode.DARK else resolve(ctx, m)
        val you = Build.VERSION.SDK_INT >= 31
        fun c(id: Int) = ctx.getColor(id)
        light = r == Mode.LIGHT
        amoled = r == Mode.AMOLED
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
            tonal()
            return
        }
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
        when {
            custom != null -> {
                primary = custom.primary; secondary = custom.secondary; tertiary = custom.tertiary
                base = custom.base; card = custom.card; surface = custom.surface
                dialogBlur = custom.dialogBlur; dialogSolid = custom.dialogSolid
            }
            r == Mode.AMOLED -> {
                ON_ACCENT = TEXT   // buttons are black — light text on them
                base = 0xFF000000.toInt()
                card = 0x0DFFFFFF
                dialogBlur = 0xE6000000.toInt(); dialogSolid = 0xFA050505.toInt()
                surface = 0xFF000000.toInt()
            }
            r == Mode.GRAPHITE -> {
                primary = 0xFFB0BEC5.toInt(); secondary = 0xFF90A4AE.toInt(); tertiary = 0xFFCFD8DC.toInt()
                base = 0xFF1B1C1F.toInt()
                card = 0x12FFFFFF
                dialogBlur = 0xB0242529.toInt(); dialogSolid = 0xF5242529.toInt()
                surface = 0xFF242529.toInt()
            }
            else -> {   // DARK
                base = if (you) mix(c(android.R.color.system_neutral1_900), 0xFF000000.toInt(), 0.35f) else 0xFF0D0F14.toInt()
                aurora = true
                card = 0x14FFFFFF
                dialogBlur = 0x9E16181E.toInt(); dialogSolid = 0xF016181E.toInt()
                surface = 0xFF16181E.toInt()
            }
        }
        auroraColors = intArrayOf(primary, tertiary, secondary)
        tonal()
    }

    /** Tonal M3 colours: accent containers and surfaces; with [translucent] — slightly see-through. */
    private fun tonal() {
        val white = 0xFFFFFFFF.toInt(); val black = 0xFF000000.toInt()
        if (light) {
            primaryContainer = mix(white, primary, 0.22f); onPrimaryContainer = mix(primary, black, 0.55f)
            secondaryContainer = mix(white, secondary, 0.22f); onSecondaryContainer = mix(secondary, black, 0.55f)
            surfaceContainer = mix(base, white, 0.55f); surfaceContainerHigh = mix(base, white, 0.85f)
        } else {
            primaryContainer = mix(base, primary, 0.36f); onPrimaryContainer = mix(primary, white, 0.7f)
            secondaryContainer = mix(base, secondary, 0.3f); onSecondaryContainer = mix(secondary, white, 0.7f)
            surfaceContainer = mix(base, white, 0.07f); surfaceContainerHigh = mix(base, white, 0.12f)
        }
        if (translucent) {
            surfaceContainer = withAlpha(surfaceContainer, surfaceAlpha)
            surfaceContainerHigh = withAlpha(surfaceContainerHigh, surfaceAlpha + 0.08f)
            primaryContainer = withAlpha(primaryContainer, surfaceAlpha + 0.04f)
            secondaryContainer = withAlpha(secondaryContainer, surfaceAlpha + 0.08f)
        }
    }

    // ---------- helpers ----------

    fun withAlpha(color: Int, a: Float) = (color and 0xFFFFFF) or ((a * 255).toInt().coerceIn(0, 255) shl 24)

    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - t) + ((b shr s) and 0xFF) * t).toInt() shl s
        return ch(24) or ch(16) or ch(8) or ch(0)
    }

    fun dp(ctx: Context, v: Float) = v * ctx.resources.displayMetrics.density
    fun sp(ctx: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, ctx.resources.displayMetrics)

    fun textPaint(ctx: Context, sizeSp: Float, face: Typeface = regular, color: Int = TEXT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sp(ctx, sizeSp)
            typeface = face
            this.color = color
            fontFeatureSettings = "tnum"   // equal-width digits — numbers do not jump
        }

    /** Cut [s] with an ellipsis to fit [width]. */
    fun ellipsize(p: Paint, s: String, width: Float): String {
        if (p.measureText(s) <= width) return s
        val n = p.breakText(s, true, (width - p.measureText("…")).coerceAtLeast(0f), null)
        return s.take(n) + "…"
    }

    /** Press ripple with rounded corners. */
    fun ripple(ctx: Context, radiusDp: Float, insetH: Float = 0f, insetV: Float = 0f): Drawable {
        val mask = GradientDrawable().apply { cornerRadius = dp(ctx, radiusDp); setColor(-1) }
        return RippleDrawable(ColorStateList.valueOf(ink(0x33)), null,
            InsetDrawable(mask, insetH.toInt(), insetV.toInt(), insetH.toInt(), insetV.toInt()))
    }

    /** Pill / rounded rectangle; in AMOLED a [primary]-filled one becomes black with an outline. */
    fun pill(ctx: Context, fill: Int, stroke: Int = 0, radiusDp: Float = 100f): Drawable {
        val black = amoled && fill == primary
        val f = if (black) 0xFF000000.toInt() else fill
        val s = if (black) ink(0x73) else stroke
        return GradientDrawable().apply {
            cornerRadius = dp(ctx, radiusDp)
            setColor(f)
            if (s != 0) setStroke(dp(ctx, 1f).toInt().coerceAtLeast(1), s)
        }
    }
}
