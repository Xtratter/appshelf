package io.github.xtratter.appshelf

import android.app.AlertDialog
import android.content.ClipboardManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Добавить или изменить свою ссылку на источник приложения; ссылка из буфера обмена подставляется сама. */
object LinkEditDialog {
    fun show(a: MainActivity, pkg: String, label: String, old: Link?, done: () -> Unit) {
        val dp = a.resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(22f), px(22f), px(22f), px(4f))
        }
        box.addView(TextView(a).apply {
            setText(if (old == null) R.string.lk_add else R.string.lk_edit); textSize = 20f; typeface = Ui.medium; setTextColor(Ui.TEXT)
        })
        box.addView(TextView(a).apply { text = label; textSize = 13.5f; setTextColor(Ui.TEXT2); setPadding(0, px(2f), 0, px(12f)) })
        fun field(hint: Int, type: Int) = EditText(a).apply {
            setHint(hint); setHintTextColor(Ui.TEXT3); setTextColor(Ui.TEXT)
            textSize = 15f; inputType = type
            background = GlassDrawable(a, 16f, Ui.ink(0x10))
            setPadding(px(14f), px(10f), px(14f), px(10f))
        }
        val url = field(R.string.lk_url, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val name = field(R.string.lk_label, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        val kind = TextView(a).apply { textSize = 13f; setTextColor(Ui.TEXT2); setPadding(px(4f), px(6f), 0, px(10f)) }
        box.addView(url)
        box.addView(kind)
        box.addView(name)
        box.addView(TextView(a).apply {
            setText(R.string.lk_hint); textSize = 12f; setTextColor(Ui.TEXT3); setLineSpacing(0f, 1.1f); setPadding(px(4f), px(8f), 0, 0)
        })

        fun preview() {
            val u = url.text.toString().trim()
            kind.setTextColor(Ui.TEXT2)
            kind.text = if (!Links.valid(u)) a.getString(R.string.lk_need_url)
            else a.getString(Links.kind(u).title) + " · " + Links.short(u)
            name.hint = if (Links.valid(u)) a.getString(Links.kind(u).title) else a.getString(R.string.lk_label)
        }
        url.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = preview()
        })
        if (old != null) {
            url.setText(old.url); name.setText(old.label)
        } else {
            // скопировали ссылку перед тем, как открыть окно, — сразу подставляем
            val clip = runCatching {
                a.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(a)?.toString()?.trim()
            }.getOrNull()
            if (clip != null && Links.valid(clip) && LinkStore.get(a, pkg).none { Links.same(it.url, clip) }) url.setText(clip)
        }
        preview()

        val b = AlertDialog.Builder(a)
            .setView(box)
            .setPositiveButton(R.string.dav_save, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> done() }
        if (old != null) b.setNeutralButton(R.string.lk_delete) { _, _ ->
            LinkStore.set(a, pkg, LinkStore.get(a, pkg).filterNot { it == old })
            done()
        }
        val dialog = b.create()
        dialog.show()
        Ui.glassDialog(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val u = url.text.toString().trim()
            if (!Links.valid(u)) { kind.setTextColor(Ui.WARN); kind.setText(R.string.lk_need_url); return@setOnClickListener }
            val link = Link(u, name.text.toString().trim())
            val list = LinkStore.get(a, pkg).toMutableList()
            val i = if (old != null) list.indexOf(old) else -1
            if (i >= 0) list[i] = link
            else if (list.none { Links.same(it.url, u) }) list += link
            LinkStore.set(a, pkg, list)
            dialog.dismiss()
            done()
        }
    }
}
