package io.github.xtratter.appshelf

import android.app.AlertDialog
import android.text.InputType
import android.widget.LinearLayout
import android.widget.TextView

/** Окна ввода парольной фразы для пароля WebDAV в файле настроек. */
object PassphraseDialog {
    private const val PASSWORD = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

    /** Новая фраза: дважды, не короче [Passphrase.MIN_LENGTH]; окно не закрывается, пока ввод неверный. */
    fun askNew(a: MainActivity, onDone: (String) -> Unit) = show(a, true, onDone)

    /** Фраза от уже сохранённого файла. */
    fun ask(a: MainActivity, onDone: (String) -> Unit) = show(a, false, onDone)

    private fun show(a: MainActivity, create: Boolean, onDone: (String) -> Unit) {
        val kit = DialogKit(a)
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(kit.px(22f), kit.px(18f), kit.px(22f), kit.px(4f))
        }
        box.addView(kit.label(a.getString(if (create) R.string.st_phrase_new_text else R.string.st_phrase_ask_text)))
        val first = kit.field(R.string.st_phrase_hint, "", PASSWORD)
        box.addView(first, kit.gap(10f))
        val second = if (create) kit.field(R.string.st_phrase_repeat, "", PASSWORD).also { box.addView(it, kit.gap(8f)) } else null
        val error = TextView(a).apply { textSize = 13f; setTextColor(Ui.WARN); setPadding(0, kit.px(8f), 0, 0) }
        box.addView(error)
        val dialog = AlertDialog.Builder(a)
            .setTitle(if (create) R.string.st_phrase_new_title else R.string.st_phrase_ask_title)
            .setView(box)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
        Ui.glassDialog(dialog)
        // «ОК» проверяет ввод и не закрывает окно при ошибке — обработчик ставим после show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val phrase = first.text.toString()
            val problem = when {
                create && phrase.length < Passphrase.MIN_LENGTH -> a.getString(R.string.st_phrase_short, Passphrase.MIN_LENGTH)
                create && phrase != second?.text.toString() -> a.getString(R.string.st_phrase_mismatch)
                phrase.isEmpty() -> a.getString(R.string.st_phrase_hint)
                else -> null
            }
            if (problem != null) { error.text = problem; return@setOnClickListener }
            dialog.dismiss()
            onDone(phrase)
        }
    }
}
