package io.github.xtratter.appshelf

import io.github.xtratter.uikit.Haptics
import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Настройки WebDAV: сервер, вход, имя файла; расписание (каждый день / по дням недели / раз в N дней, время, только Wi-Fi);
 * проверка, отправка сейчас и открытие списка с сервера.
 */
object SyncDialog {
    fun show(a: MainActivity) {
        val p = Prefs(a)
        val whenFmt = SimpleDateFormat("EEE, d MMM, HH:mm", Locale.getDefault())
        var busy = false
        val kit = DialogKit(a) { busy }
        val plan = SchedulePicker(kit, p, whenFmt)
        with(kit) {
            val box = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(22f), px(22f), px(22f), px(8f))
            }
            box.addView(TextView(a).apply {
                setText(R.string.dav_title); textSize = 20f; typeface = Ui.medium; setTextColor(Ui.TEXT)
            })
            box.addView(label(a.getString(R.string.dav_explain), top = 4f))

            // ---------- сервер ----------
            val server = card()
            val url = field(R.string.dav_url, p.davUrl, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
            val user = field(R.string.dav_user, p.davUser, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
            val pass = field(R.string.dav_pass, p.davPass, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            val file = field(R.string.dav_file, p.davDevice, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
            file.hint = Apps.shortName(a)
            server.addView(url)
            server.addView(label(a.getString(R.string.dav_url_hint), 12f, Ui.TEXT3, 4f))
            server.addView(user, gap(6f))
            server.addView(pass, gap(8f))
            server.addView(label(a.getString(R.string.dav_file_label), 12f, Ui.TEXT3, 10f))
            server.addView(file)

            // сколько версий хранить: − N +
            var keep = p.davKeep
            val keepRow = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, px(10f), 0, 0) }
            val keepText = TextView(a).apply { textSize = 15f; setTextColor(Ui.TEXT) }
            val keepHint = label("", 12f, Ui.TEXT3, 4f)
            fun showKeep() {
                keepText.text = a.getString(R.string.dav_keep, keep)
                keepHint.setText(if (keep <= 1) R.string.dav_keep_one else R.string.dav_keep_many)
            }
            fun stepKeep(d: Int) {
                val steps = intArrayOf(1, 2, 3, 5, 7, 10, 15, 20, 30, 50)
                val i = steps.indexOfFirst { it >= keep }.let { if (it < 0) steps.size - 1 else it }
                keep = steps[(i + d).coerceIn(0, steps.size - 1)]
                showKeep()
            }
            keepRow.addView(keepText, LinearLayout.LayoutParams(0, -2, 1f))
            keepRow.addView(pill("−") { stepKeep(-1) }, LinearLayout.LayoutParams(px(52f), px(40f)))
            keepRow.addView(pill("+") { stepKeep(1) }, LinearLayout.LayoutParams(px(52f), px(40f)).apply { leftMargin = px(8f) })
            server.addView(keepRow)
            server.addView(keepHint)
            showKeep()
            box.addView(server, gap(12f))

            val status = label("", 13.5f, Ui.TEXT2, 10f)
            fun showLast() {
                status.setTextColor(if (p.syncLast > 0 && !p.syncOk) Ui.WARN else Ui.TEXT2)
                status.text = when {
                    p.syncLast <= 0 -> a.getString(R.string.dav_never)
                    p.syncOk -> a.getString(R.string.dav_last_ok, whenFmt.format(Date(p.syncLast)), p.syncMsg)
                    else -> a.getString(R.string.dav_last_fail, whenFmt.format(Date(p.syncLast)), p.syncMsg)
                }
            }
            showLast()

            fun saveServer(): Boolean {
                val u = url.text.toString().trim()
                if (u.isNotEmpty() && !Regex("^https?://[^/]+.*", RegexOption.IGNORE_CASE).matches(u)) {
                    status.setTextColor(Ui.WARN); status.setText(R.string.dav_e_url)
                    return false
                }
                p.davUrl = u
                p.davUser = user.text.toString().trim()
                p.davPass = pass.text.toString()
                p.davDevice = file.text.toString().trim()
                p.davKeep = keep
                return true
            }

            /** Сетевое действие в фоне: пока идёт — «Подключаюсь…», кнопки не нажимаются. */
            fun network(work: () -> Any?, done: (Any?) -> Unit) {
                if (!saveServer()) return
                if (p.davUrl.isEmpty()) { status.setTextColor(Ui.WARN); status.setText(R.string.dav_no_server); return }
                busy = true
                status.setTextColor(Ui.TEXT2); status.setText(R.string.dav_connecting)
                Thread {
                    val r = try { work() } catch (e: Exception) { e }
                    box.post {
                        busy = false
                        if (r is Exception) {
                            status.setTextColor(Ui.WARN); status.text = Sync.error(a, r)
                            Haptics.play(Haptics.Kind.ERROR)
                        } else done(r)
                    }
                }.start()
            }

            val actions = LinearLayout(a).apply { isBaselineAligned = false }
            actions.addView(pill(a.getString(R.string.dav_check)) {
                network({ Sync.dav(p).check() }) {
                    status.setTextColor(Ui.OK); status.setText(R.string.dav_check_ok)
                    Haptics.play(Haptics.Kind.SUCCESS)
                }
            }, LinearLayout.LayoutParams(0, px(44f), 1f))
            actions.addView(pill(a.getString(R.string.dav_send_now)) {
                network({ Sync.run(a) }) { r ->
                    showLast()
                    if ((r as Sync.Result).ok) { status.setTextColor(Ui.OK); Haptics.play(Haptics.Kind.SUCCESS) }
                    else Haptics.play(Haptics.Kind.ERROR)
                    a.refreshSummary()
                }
            }, LinearLayout.LayoutParams(0, px(44f), 1f).apply { leftMargin = px(8f) })
            box.addView(actions, gap(12f))
            box.addView(status)


        // ---------- расписание ----------
            box.addView(label(a.getString(R.string.dav_schedule), 15f, Ui.primary, 14f).apply { typeface = Ui.medium })
        box.addView(plan.view, gap(6f))

            // ---------- восстановление ----------
            box.addView(label(a.getString(R.string.dav_restore_hint), 12.5f, Ui.TEXT3, 14f))
            val dialog = AlertDialog.Builder(a)
                .setView(ScrollView(a).apply { addView(box) })
                .setPositiveButton(R.string.dav_save, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create()
            box.addView(pill(a.getString(R.string.dav_open)) {
                network({ ServerLists.load(p) }) { r ->
                    @Suppress("UNCHECKED_CAST") val groups = r as List<ServerLists.Group>
                    if (groups.isEmpty()) { status.setTextColor(Ui.WARN); status.setText(R.string.dav_no_files); return@network }
                    status.text = ""
                    ServerLists.pickGroup(a, p, groups, whenFmt) { dialog.dismiss() }
                }
            }, LinearLayout.LayoutParams(-1, px(44f)).apply { topMargin = px(6f); bottomMargin = px(8f) })

            dialog.show()
            Ui.glassDialog(dialog)
            // «Сохранить» проверяет адрес и не закрывает окно при ошибке — обработчик ставим после show()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (busy || !saveServer()) return@setOnClickListener
                if (plan.sched.repeat != Repeat.OFF && p.davUrl.isEmpty()) {
                    status.setTextColor(Ui.WARN); status.setText(R.string.dav_no_server); return@setOnClickListener
                }
                p.setSchedule(plan.forSaving())
                p.syncWifi = plan.wifiOnly
                p.updNotify = plan.notifyUpdates
                // с Android 13 для уведомлений нужно разрешение; откажут — MainActivity снимет галочку
                if (p.updNotify && !UpdateNotifier.canNotify(a))
                    a.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), MainActivity.REQ_NOTIFY)
                Sync.schedule(a)
                dialog.dismiss()
                a.refreshSummary()
            }
        }
    }
}
