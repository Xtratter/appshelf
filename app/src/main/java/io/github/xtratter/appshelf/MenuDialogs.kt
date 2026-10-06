package io.github.xtratter.appshelf

import io.github.xtratter.uikit.Expressive
import io.github.xtratter.uikit.EdgeBlur
import io.github.xtratter.uikit.Haptics
import io.github.xtratter.uikit.Help
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

// ---------- меню ----------

/** Меню ⋮ — стеклянное окно под кнопкой (системное всплывающее меню стеклом не сделать). */
internal fun MainActivity.showMenu(anchor: View) {
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8f), dp(10f), dp(8f), dp(10f))
    }
    val dialog = AlertDialog.Builder(this).setView(box).create()
    fun item(title: Int, help: Int, action: () -> Unit) = box.addView(TextView(this).apply {
        setText(title)
        Help.attach(this, title, help)
        textSize = 16f
        setTextColor(Ui.TEXT)
        gravity = Gravity.CENTER_VERTICAL
        minHeight = dp(52f)
        setPadding(dp(18f), 0, dp(18f), 0)
        background = Ui.ripple(this@showMenu, 16f)
        setOnClickListener { dialog.dismiss(); action() }
    }, LinearLayout.LayoutParams(-1, -2))
    /** Пункт-переключатель: галочка в цветах темы, меню не закрывается — сразу видно, что изменилось. */
    fun toggle(title: Int, value: Boolean, change: (Boolean) -> Unit) = box.addView(LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(52f)
        setPadding(dp(18f), 0, dp(10f), 0)
        background = Ui.ripple(this@showMenu, 16f)
        addView(TextView(this@showMenu).apply { setText(title); textSize = 16f; setTextColor(Ui.TEXT) },
            LinearLayout.LayoutParams(0, -2, 1f))
        val box2 = android.widget.CheckBox(this@showMenu).apply {
            isChecked = value
            isClickable = false
            isFocusable = false
            buttonTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Ui.primary, Ui.TEXT3))
        }
        addView(box2)
        setOnClickListener {
            box2.isChecked = !box2.isChecked
            Haptics.play(Haptics.Kind.TICK)
            change(box2.isChecked)
        }
    }, LinearLayout.LayoutParams(-1, -2))
    item(R.string.hi_title, R.string.h_history) { HistoryDialog.show(this) }
    item(R.string.catalog_title, R.string.h_catalog) { CatalogDialog.show(this) }
    item(R.string.theme, R.string.h_theme) { themeDialog() }
    item(R.string.haptics, R.string.h_haptics) { hapticsDialog() }
    toggle(R.string.show_system, prefs.showSystem) { prefs.showSystem = it; render() }
    Help.attach(box.getChildAt(box.childCount - 1), R.string.show_system, R.string.h_system)
    item(R.string.about, R.string.h_about) { about() }
    dialog.show()
    Ui.glassDialog(dialog)
    dialog.window?.apply {
        val at = IntArray(2)
        anchor.getLocationOnScreen(at)
        setGravity(Gravity.TOP or Gravity.END)
        attributes = attributes.apply {
            width = dp(270f)
            x = dp(10f)
            y = at[1] + anchor.height - dp(4f)
        }
        setDimAmount(0.15f)
    }
}

/** Тема: выбор из списка, сразу применяется. */
/**
 * Применить тему на месте: экран перекрашивается заново, окно «Тема» остаётся открытым и тоже перекрашивается —
 * без пересоздания экрана, поэтому без вспышки.
 */
internal fun MainActivity.applyThemeInPlace(change: () -> Unit) {
    change()
    Ui.apply(this, prefs.theme())
    setTheme(if (Ui.light) R.style.AppTheme_Light else R.style.AppTheme)
    buildUi()
    window.decorView.requestApplyInsets()   // новые виды получают отступы под строку состояния и навигацию
}

private fun MainActivity.themeDialog() {
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20f), dp(18f), dp(20f), 0)
    }
    val dialog = AlertDialog.Builder(this)
        .setView(ScrollView(this).apply { addView(box) })
        .setNegativeButton(R.string.close, null)
        .create()
    fillThemeBox(dialog, box)
    dialog.show()
    Ui.glassDialog(dialog)
}

/** Содержимое окна «Тема» в текущих цветах; после смены темы — перекрасить само окно и собрать заново. */
private fun MainActivity.fillThemeBox(dialog: AlertDialog, box: LinearLayout) {
    box.removeAllViews()
    val themes = Theme.entries
    val tint = android.content.res.ColorStateList.valueOf(Ui.primary)
    fun recolor() {
        dialog.window?.setBackgroundDrawable(GlassDrawable(this, 28f, Ui.dialogBlur))
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)
            .forEach { dialog.getButton(it)?.setTextColor(Ui.primary) }
        fillThemeBox(dialog, box)
    }
    box.addView(TextView(this).apply {
        setText(R.string.theme); textSize = 20f; typeface = Ui.medium; setTextColor(Ui.TEXT)
        setPadding(dp(4f), 0, 0, dp(8f))
    })
    val group = android.widget.RadioGroup(this)
    for (t in themes) group.addView(android.widget.RadioButton(this).apply {
        id = View.generateViewId()
        setText(t.title); textSize = 16f; setTextColor(Ui.TEXT); buttonTintList = tint
        minHeight = dp(48f)
        isChecked = t == prefs.theme()
        setOnClickListener {
            if (t == prefs.theme()) return@setOnClickListener
            Haptics.play(Haptics.Kind.TICK)
            applyThemeInPlace { prefs.theme = t.name }
            recolor()
        }
    })
    box.addView(group)
    // прозрачность всего интерфейса — отдельно от цветов темы
    box.addView(View(this).apply { setBackgroundColor(Ui.ink(0x22)) },
        LinearLayout.LayoutParams(-1, dp(1f)).apply { topMargin = dp(8f); bottomMargin = dp(4f) })
    box.addView(LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(52f)
        background = Ui.ripple(this@fillThemeBox, 16f)
        addView(TextView(this@fillThemeBox).apply {
            setText(R.string.translucency); textSize = 16f; setTextColor(Ui.TEXT)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val sw = android.widget.Switch(this@fillThemeBox).apply {
            isChecked = prefs.translucent
            isClickable = false
            thumbTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Ui.primary, Ui.TEXT3))
            trackTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Ui.withAlpha(Ui.primary, 0.5f), Ui.ink(0x33)))
        }
        addView(sw)
        setOnClickListener {
            sw.isChecked = !sw.isChecked
            Haptics.play(Haptics.Kind.TICK)
            applyThemeInPlace { prefs.translucent = sw.isChecked }
            recolor()
        }
    }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6f) })
}

/** Вибрация: сила отклика или «Выключена»; при выборе сразу проигрывается пример. */
private fun MainActivity.hapticsDialog() {
    val levels = Haptics.Level.entries
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20f), dp(6f), dp(20f), 0)
    }
    val tint = android.content.res.ColorStateList.valueOf(Ui.primary)
    val group = android.widget.RadioGroup(this)
    for (l in levels) group.addView(android.widget.RadioButton(this).apply {
        id = View.generateViewId()
        setText(l.title); textSize = 16f; setTextColor(Ui.TEXT); buttonTintList = tint
        minHeight = dp(48f)
        isChecked = l == Haptics.level()
        isEnabled = Haptics.available() || l == Haptics.Level.OFF
        setOnClickListener {
            Haptics.setLevel(l)
            // пример: щелчок и «открытие окна»
            Haptics.play(Haptics.Kind.TAP)
            main.postDelayed({ Haptics.play(Haptics.Kind.OPEN) }, 220)
        }
    })
    box.addView(group)
    // как уровень работает на этом телефоне: у эффектов производителя сила одна — уровень меняет, на что откликаться
    val how = TextView(this)
    fun explain() {
        how.text = if (!Haptics.available()) getString(R.string.hap_none) else getString(when (Haptics.effective()) {
            Haptics.Engine.EFFECTS -> R.string.hap_how_effects
            Haptics.Engine.PRIMITIVES -> R.string.hap_how_primitives
            else -> if (Haptics.amplitude()) R.string.hap_how_simple else R.string.hap_how_simple_fixed
        })
    }
    explain()
    // способ вибрации — с пометкой, что поддерживает этот телефон; при выборе — пример
    if (Haptics.available()) {
        box.addView(TextView(this).apply {
            setText(R.string.hap_engine); textSize = 14f; setTextColor(Ui.primary); typeface = Ui.medium
            setPadding(0, dp(12f), 0, dp(2f))
        })
        val engines = android.widget.RadioGroup(this)
        for (e in Haptics.Engine.entries) engines.addView(android.widget.RadioButton(this).apply {
            id = View.generateViewId()
            val ok = Haptics.supports(e)
            text = getString(e.title) + if (ok) "" else " — " + getString(R.string.hap_unsupported)
            textSize = 15f; setTextColor(if (ok) Ui.TEXT else Ui.TEXT3); buttonTintList = tint
            minHeight = dp(44f)
            isEnabled = ok
            isChecked = e == Haptics.engineChoice()
            setOnClickListener {
                Haptics.setEngine(e)
                explain()
                Haptics.play(Haptics.Kind.TAP)
                main.postDelayed({ Haptics.play(Haptics.Kind.OPEN) }, 220)
            }
        })
        box.addView(engines)
    }
    box.addView(how.apply {
        textSize = 13f; setTextColor(Ui.TEXT3); setLineSpacing(0f, 1.1f)
        setPadding(0, dp(6f), 0, dp(4f))
    })
    AlertDialog.Builder(this).setTitle(R.string.haptics).setView(box)
        .setPositiveButton(R.string.done, null)
        .show().also { Ui.glassDialog(it) }
}

private fun MainActivity.about() {
    val version = packageManager.getPackageInfo(packageName, 0).versionName
    val tv = TextView(this).apply {
        text = android.text.Html.fromHtml(getString(R.string.about_text, version), android.text.Html.FROM_HTML_MODE_LEGACY)
        movementMethod = LinkMovementMethod.getInstance()
        setLinkTextColor(Ui.primary)
        setTextColor(Ui.TEXT2)
        setPadding(dp(24f), dp(8f), dp(24f), 0)
        textSize = 15f
    }
    AlertDialog.Builder(this).setTitle(R.string.app_name).setView(tv)
        .setPositiveButton(android.R.string.ok, null).show().also { Ui.glassDialog(it) }
}
