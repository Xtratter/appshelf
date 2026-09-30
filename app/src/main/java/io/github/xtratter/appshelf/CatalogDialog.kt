package io.github.xtratter.appshelf

import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Каталог ссылок: адрес sources.json (по умолчанию — репозиторий на GitHub), состояние и «Обновить». */
object CatalogDialog {
    fun show(a: MainActivity) {
        val p = Prefs(a)
        val dp = a.resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()
        val fmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
        var busy = false

        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(22f), px(22f), px(22f), px(8f))
        }
        fun label(text: String, size: Float = 13.5f, color: Int = Ui.TEXT2, top: Float = 0f) = TextView(a).apply {
            this.text = text; textSize = size; setTextColor(color); setLineSpacing(0f, 1.1f)
            setPadding(0, px(top), 0, px(4f))
        }
        box.addView(TextView(a).apply {
            setText(R.string.catalog_title); textSize = 20f; typeface = Ui.medium; setTextColor(Ui.TEXT)
        })
        box.addView(label(a.getString(R.string.catalog_explain), top = 4f))
        val url = EditText(a).apply {
            setText(p.catalogUrl); setHint(R.string.catalog_off_hint)
            setHintTextColor(Ui.TEXT3); setTextColor(Ui.TEXT)
            textSize = 14f; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            background = GlassDrawable(a, 16f, Ui.ink(0x10))
            setPadding(px(14f), px(10f), px(14f), px(10f))
        }
        box.addView(url, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(10f) })
        val status = label("", 13.5f, Ui.TEXT2, 10f)
        fun showStatus() {
            status.setTextColor(Ui.TEXT2)
            val n = LinkStore.catalog(a).size
            status.text = when {
                p.catalogUrl.isBlank() -> a.getString(R.string.catalog_off)
                p.catalogFetched <= 0 -> a.getString(R.string.catalog_never)
                else -> a.getString(R.string.catalog_state, a.resources.getQuantityString(R.plurals.catalog_apps, n, n),
                    fmt.format(Date(p.catalogFetched)))
            }
        }
        showStatus()

        fun pill(text: Int, onClick: () -> Unit) = TextView(a).apply {
            setText(text); gravity = Gravity.CENTER; textSize = 14.5f; typeface = Ui.medium; maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(11, 15, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            setTextColor(Ui.primary)
            background = Ui.pill(a, Ui.withAlpha(Ui.primary, 0.12f), Ui.withAlpha(Ui.primary, 0.3f))
            foreground = Ui.ripple(a, 100f)
            setOnClickListener { if (!busy) onClick() }
        }
        fun save(): Boolean {
            val u = url.text.toString().trim()
            if (u.isNotEmpty() && !Links.valid(u)) { status.setTextColor(Ui.WARN); status.setText(R.string.dav_e_url); return false }
            p.catalogUrl = u
            return true
        }
        val row = LinearLayout(a).apply { isBaselineAligned = false }
        row.addView(pill(R.string.catalog_refresh) {
            if (!save()) return@pill
            busy = true
            status.setTextColor(Ui.TEXT2); status.setText(R.string.dav_connecting)
            Thread {
                val err = try { LinkStore.refreshCatalog(a, 0); null } catch (e: Exception) { e }
                box.post {
                    busy = false
                    if (err == null) { showStatus(); status.setTextColor(Ui.OK); a.refresh() }
                    else { status.setTextColor(Ui.WARN); status.text = if (err is org.json.JSONException || err is IllegalArgumentException)
                        a.getString(R.string.catalog_bad) else Sync.error(a, err) }
                }
            }.start()
        }, LinearLayout.LayoutParams(0, px(44f), 1f))
        row.addView(pill(R.string.catalog_default) { url.setText(LinkStore.DEFAULT_CATALOG) },
            LinearLayout.LayoutParams(0, px(44f), 1f).apply { leftMargin = px(8f) })
        box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12f) })
        box.addView(status)
        box.addView(label(a.getString(R.string.catalog_priority), 12.5f, Ui.TEXT3, 8f))

        val dialog = AlertDialog.Builder(a)
            .setView(ScrollView(a).apply { addView(box) })
            .setPositiveButton(R.string.dav_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
        Ui.glassDialog(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (busy || !save()) return@setOnClickListener
            dialog.dismiss()
            Thread { runCatching { LinkStore.refreshCatalog(a.applicationContext) }; a.runOnUiThread { a.refresh() } }.start()
        }
    }
}
