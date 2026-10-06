package io.github.xtratter.appshelf

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.pm.PackageInstaller
import android.os.Build
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.io.File

/** Общее для установки, «Обновить все» и удаления: окна прогресса, файл в кэше, флаги ответа системы. */

/** Каталог кэша для скачанных APK: старые файлы убираем, остаётся один текущий. */
internal fun newApkFile(a: MainActivity, name: String): File {
    val dir = File(a.cacheDir, "apk").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    return File(dir, name)
}

/** Полоса прогресса в цвете темы; пока размер неизвестен — бегущая. */
internal fun progressBar(a: MainActivity) = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply {
    isIndeterminate = true
    progressTintList = android.content.res.ColorStateList.valueOf(Ui.primary)
    indeterminateTintList = progressTintList
}

/** Показать в полосе долю [done] из [total]; [total] ≤ 0 — размер неизвестен, полоса остаётся бегущей. */
internal fun ProgressBar.setFraction(done: Long, total: Long) {
    if (total > 0) { isIndeterminate = false; max = 1000; progress = (done * 1000 / total).toInt() }
}

/** Вертикальный блок окна прогресса: [views] с отступами сверху (первый — без отступа). */
internal fun progressBox(a: MainActivity, vararg views: Pair<android.view.View, Int>) = LinearLayout(a).apply {
    val dp = a.resources.displayMetrics.density
    orientation = LinearLayout.VERTICAL
    setPadding((24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt(), 0)
    for ((v, top) in views) addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (top * dp).toInt() })
}

/** Окно прогресса с кнопкой отмены; [onCancel] — пользователь отказался. */
internal fun progressDialog(a: MainActivity, title: String, box: View, cancel: Int, onCancel: () -> Unit): AlertDialog {
    val dialog = AlertDialog.Builder(a).setTitle(title).setView(box)
        .setNegativeButton(cancel) { _, _ -> onCancel() }.setCancelable(false).create()
    dialog.show()
    Ui.glassDialog(dialog)
    return dialog
}

internal fun progressInfo(a: MainActivity) =
    TextView(a).apply { textSize = 13.5f; setTextColor(Ui.TEXT2); setText(R.string.dav_connecting) }

/** Флаги PendingIntent для ответа PackageInstaller (с Android 12 — изменяемый). */
internal fun installerFlags() = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
