package io.github.xtratter.appshelf

import io.github.xtratter.uikit.Haptics
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** «Настройки приложения»: сохранить (в файл или на сервер, с паролем WebDAV или без) и восстановить. */
object SettingsDialog {
    fun save(a: MainActivity) {
        val p = Prefs(a)
        val dp = a.resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(px(22f), px(20f), px(22f), 0) }
        box.addView(TextView(a).apply { setText(R.string.st_save); textSize = 20f; typeface = Ui.medium; setTextColor(Ui.TEXT) })
        box.addView(TextView(a).apply {
            setText(R.string.st_save_text); textSize = 13.5f; setTextColor(Ui.TEXT2); setLineSpacing(0f, 1.1f)
            setPadding(0, px(6f), 0, px(8f))
        })
        val warn = TextView(a).apply {
            setText(R.string.st_pass_warn); textSize = 12.5f; setTextColor(Ui.WARN); setPadding(px(32f), 0, 0, px(6f))
            visibility = android.view.View.GONE
        }
        val pass = CheckBox(a).apply {
            setText(R.string.st_with_pass); textSize = 15f; setTextColor(Ui.TEXT)
            buttonTintList = ColorStateList.valueOf(Ui.primary)
            isEnabled = p.davPass.isNotEmpty()
            setOnCheckedChangeListener { _, on -> warn.visibility = if (on) android.view.View.VISIBLE else android.view.View.GONE }
        }
        box.addView(pass); box.addView(warn)
        val b = AlertDialog.Builder(a).setView(box)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.st_to_file) { _, _ -> a.saveSettingsFile(pass.isChecked) }
        if (p.davUrl.isNotEmpty()) b.setNeutralButton(R.string.st_to_server) { _, _ ->
            val with = pass.isChecked
            Thread {
                val err = runCatching { SettingsIO.upload(a, with) }.exceptionOrNull()
                a.runOnUiThread {
                    Haptics.play(if (err == null) Haptics.Kind.SUCCESS else Haptics.Kind.ERROR)
                    Toast.makeText(a, if (err == null) a.getString(R.string.st_uploaded, Sync.deviceFolder(a, p))
                    else Sync.error(a, err as? Exception ?: Exception(err)), Toast.LENGTH_LONG).show()
                }
            }.start()
        }
        b.show().also { Ui.glassDialog(it) }
    }

    fun restore(a: MainActivity) {
        val p = Prefs(a)
        val choices = mutableListOf(a.getString(R.string.open_from_file))
        if (p.davUrl.isNotEmpty()) choices += a.getString(R.string.open_from_server)
        AlertDialog.Builder(a).setTitle(R.string.st_restore)
            .setItems(choices.toTypedArray()) { _, i -> if (i == 0) a.openSettingsFile() else fromServer(a) }
            .setNegativeButton(android.R.string.cancel, null)
            .show().also { Ui.glassDialog(it) }
    }

    private fun fromServer(a: MainActivity) {
        Toast.makeText(a, R.string.dav_connecting, Toast.LENGTH_SHORT).show()
        Thread {
            val r = runCatching { SettingsIO.phones(a) }
            a.runOnUiThread {
                val phones = r.getOrElse { e -> Toast.makeText(a, Sync.error(a, e as? Exception ?: Exception(e)), Toast.LENGTH_LONG).show(); return@runOnUiThread }
                if (phones.isEmpty()) { Toast.makeText(a, R.string.st_none_on_server, Toast.LENGTH_LONG).show(); return@runOnUiThread }
                AlertDialog.Builder(a).setTitle(R.string.st_pick_phone)
                    .setItems(phones.toTypedArray()) { _, i ->
                        Thread {
                            val text = runCatching { SettingsIO.download(a, phones[i]) }
                            a.runOnUiThread {
                                text.onSuccess { a.importSettings(it) }
                                    .onFailure { e -> Toast.makeText(a, Sync.error(a, e as? Exception ?: Exception(e)), Toast.LENGTH_LONG).show() }
                            }
                        }.start()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show().also { Ui.glassDialog(it) }
            }
        }.start()
    }
}
