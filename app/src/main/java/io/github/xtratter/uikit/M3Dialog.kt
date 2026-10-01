// Copied from github.com/Xtratter/android-ui-kit (v1.1) — edit there and re-run install.sh
package io.github.xtratter.uikit

import android.app.AlertDialog
import android.os.Build
import android.view.View
import android.view.WindowManager

/**
 * Dialogs in the M3 style: tonal (or glass) window, the screen behind blurred on Android 12+, accent buttons,
 * haptic clicks on everything clickable, soft dissolving edges of the scrolling content.
 * Call `M3Dialog.style(dialog)` right after `dialog.show()`.
 */
object M3Dialog {
    private val open = HashSet<View>()
    /** How many styled dialogs are open (e.g. skip expensive redraws of the screen under a blurred dialog). */
    val count get() = open.size
    /** A new screen: forget dialogs of the old one. */
    fun forget() = open.clear()
    /** Called when the last styled dialog closes. */
    var onAllClosed: (() -> Unit)? = null
    /** Height of the dissolving edges of the scrolling content, dp (0 — none). */
    var edgeDp = 56f

    fun blurAvailable(d: AlertDialog) = Build.VERSION.SDK_INT >= 31 &&
        d.context.getSystemService(WindowManager::class.java).isCrossWindowBlurEnabled

    /** Re-tint an already styled dialog after a theme change (window fill and button colours). */
    fun recolor(d: AlertDialog) {
        val w = d.window ?: return
        w.setBackgroundDrawable(M3Surface(d.context, 28f, if (blurAvailable(d)) M3.dialogBlur else M3.dialogSolid))
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)
            .forEach { d.getButton(it)?.setTextColor(M3.primary) }
    }

    fun style(d: AlertDialog) {
        val w = d.window ?: return
        open += w.decorView
        w.decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                if (open.remove(v) && open.isEmpty()) onAllClosed?.invoke()
            }
        })
        val blur = blurAvailable(d)
        recolor(d)
        if (blur && Build.VERSION.SDK_INT >= 31) {
            // blur the whole screen behind; no separate blur under the window — the system blurs a rectangle,
            // and a "frame" showed inside the rounded window
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            w.attributes = w.attributes.apply { blurBehindRadius = M3.dp(d.context, 10f).toInt() }
        }
        w.setDimAmount(0.35f)
        // translucent window: the system shadow and panel backgrounds showed through as a light "frame"
        w.setElevation(0f)
        clearPanels(w.decorView)
        Haptics.attachAll(w.decorView)
        if (edgeDp > 0) w.decorView.post {
            val scroller = EdgeBlur.findScrollable(w.decorView) ?: return@post
            EdgeBlur.wrap(scroller, edgeDp, 0, 0)?.dissolve = true
        }
    }

    /** Remove backgrounds of the dialog's own panels and of the frames around the content (not ours). */
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
}
