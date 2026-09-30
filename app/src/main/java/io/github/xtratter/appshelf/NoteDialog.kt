package io.github.xtratter.appshelf

import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Заметка к приложению: несколько строк свободного текста; сохраняется вместе со ссылками (и на WebDAV). */
object NoteDialog {
    fun show(a: MainActivity, pkg: String, label: String, done: () -> Unit) {
        val dp = a.resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()
        val old = LinkStore.note(a, pkg)
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(22f), px(22f), px(22f), px(4f))
        }
        box.addView(TextView(a).apply { setText(R.string.note_section); textSize = 20f; typeface = Ui.medium; setTextColor(Ui.TEXT) })
        box.addView(TextView(a).apply { text = label; textSize = 13.5f; setTextColor(Ui.TEXT2); setPadding(0, px(2f), 0, px(12f)) })
        val field = EditText(a).apply {
            setText(old)
            setHint(R.string.note_hint)
            setHintTextColor(Ui.TEXT3); setTextColor(Ui.TEXT)
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3; maxLines = 8
            gravity = android.view.Gravity.TOP
            background = GlassDrawable(a, 16f, Ui.ink(0x10))
            setPadding(px(14f), px(10f), px(14f), px(10f))
        }
        box.addView(field)
        val b = AlertDialog.Builder(a)
            .setView(box)
            .setPositiveButton(R.string.dav_save) { _, _ -> LinkStore.setNote(a, pkg, field.text.toString()); done() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> done() }
        if (old.isNotEmpty()) b.setNeutralButton(R.string.lk_delete) { _, _ -> LinkStore.setNote(a, pkg, ""); done() }
        val d = b.create()
        d.show()
        Ui.glassDialog(d)
        field.requestFocus()
        field.setSelection(field.text.length)
        d.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }
}
