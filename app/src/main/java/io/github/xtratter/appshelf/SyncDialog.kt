package io.github.xtratter.appshelf

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Настройки WebDAV: сервер, вход, имя файла; расписание (каждый день / по дням недели / раз в N дней, время, только Wi-Fi);
 * проверка, отправка сейчас и открытие списка с сервера.
 */
object SyncDialog {
    fun show(a: MainActivity) {
        val p = Prefs(a)
        val dp = a.resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()
        val whenFmt = SimpleDateFormat("EEE, d MMM, HH:mm", Locale.getDefault())
        var sched = p.schedule()
        var busy = false
        /** Обновить видимость строк расписания и «Следующая отправка…». */
        var refreshPlan: () -> Unit = {}

        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(22f), px(22f), px(22f), px(8f))
        }
        fun label(text: String, size: Float = 13.5f, color: Int = Ui.TEXT2, top: Float = 0f) = TextView(a).apply {
            this.text = text; textSize = size; setTextColor(color); setLineSpacing(0f, 1.1f)
            setPadding(0, px(top), 0, px(4f))
        }
        fun card() = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = GlassDrawable(a, 18f)
            setPadding(px(14f), px(12f), px(14f), px(12f))
        }
        fun field(hint: Int, value: String, type: Int) = EditText(a).apply {
            setHint(hint); setText(value)
            setHintTextColor(Ui.TEXT3); setTextColor(Ui.TEXT)
            textSize = 15f; maxLines = 1; inputType = type
            background = GlassDrawable(a, 16f, Ui.ink(0x10))
            setPadding(px(14f), px(10f), px(14f), px(10f))
        }
        fun pill(text: String, filled: Boolean = false, onClick: () -> Unit) = TextView(a).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 14.5f
            typeface = Ui.medium
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(11, 15, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            setPadding(px(12f), 0, px(12f), 0)
            setTextColor(if (filled) Ui.ON_ACCENT else Ui.primary)
            background = if (filled) Ui.pill(a, Ui.primary)
            else Ui.pill(a, Ui.withAlpha(Ui.primary, 0.12f), Ui.withAlpha(Ui.primary, 0.3f))
            foreground = Ui.ripple(a, 100f)
            setOnClickListener { if (!busy) onClick() }
        }
        fun gap(h: Float) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(h) }

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
                    if (r is Exception) { status.setTextColor(Ui.WARN); status.text = Sync.error(a, r) } else done(r)
                }
            }.start()
        }

        val actions = LinearLayout(a).apply { isBaselineAligned = false }
        actions.addView(pill(a.getString(R.string.dav_check)) {
            network({ Sync.dav(p).check() }) {
                status.setTextColor(Ui.OK); status.setText(R.string.dav_check_ok)
            }
        }, LinearLayout.LayoutParams(0, px(44f), 1f))
        actions.addView(pill(a.getString(R.string.dav_send_now)) {
            network({ Sync.run(a) }) { r ->
                showLast()
                if ((r as Sync.Result).ok) status.setTextColor(Ui.OK)
                a.refreshSummary()
            }
        }, LinearLayout.LayoutParams(0, px(44f), 1f).apply { leftMargin = px(8f) })
        box.addView(actions, gap(12f))
        box.addView(status)

        // ---------- расписание ----------
        box.addView(label(a.getString(R.string.dav_schedule), 15f, Ui.primary, 14f).apply { typeface = Ui.medium })
        val plan = card()
        val tint = ColorStateList.valueOf(Ui.primary)
        val modes = listOf(Repeat.OFF to R.string.rep_off, Repeat.DAILY to R.string.rep_daily,
            Repeat.WEEKDAYS to R.string.rep_weekdays, Repeat.EVERY_N to R.string.rep_every)
        val group = RadioGroup(a)
        for (m in modes) group.addView(RadioButton(a).apply {
            id = View.generateViewId(); tag = m.first
            setText(m.second); textSize = 15f; setTextColor(Ui.TEXT); buttonTintList = tint
            minHeight = px(42f)
            if (sched.repeat == m.first) isChecked = true
        })
        plan.addView(group)

        // дни недели — с первого дня недели в текущем языке (Пн для русского, Вс для английского)
        val daysRow = LinearLayout(a).apply { setPadding(0, px(6f), 0, px(4f)) }
        val names = DateFormatSymbols.getInstance().shortWeekdays
        val first = Calendar.getInstance().firstDayOfWeek
        val dayViews = ArrayList<TextView>()
        fun paintDays() {
            for (v in dayViews) {
                val on = sched.has(v.tag as Int)
                v.setTextColor(if (on) Ui.ON_ACCENT else Ui.TEXT)
                v.background = if (on) Ui.pill(a, Ui.primary) else Ui.pill(a, Ui.card, Ui.ink(0x33))
            }
        }
        for (k in 0 until 7) {
            val dow = (first - 1 + k) % 7 + 1
            val v = TextView(a).apply {
                tag = dow
                text = names[dow].replace(".", "").replaceFirstChar { it.titlecase() }.take(2)
                gravity = Gravity.CENTER; textSize = 13f; typeface = Ui.medium
                foreground = Ui.ripple(a, 100f)
                setOnClickListener { sched = sched.copy(days = sched.days xor Schedule.bit(dow)); paintDays(); refreshPlan() }
            }
            dayViews += v
            daysRow.addView(v, LinearLayout.LayoutParams(0, px(38f), 1f).apply { if (k > 0) leftMargin = px(4f) })
        }
        paintDays()
        plan.addView(daysRow)

        // «каждые N дней»: − N +
        val everyRow = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, px(6f), 0, px(4f)) }
        val everyText = TextView(a).apply { textSize = 15f; setTextColor(Ui.TEXT); gravity = Gravity.CENTER }
        fun step(d: Int) { sched = sched.copy(every = (sched.every + d).coerceIn(2, 60)); refreshPlan() }
        everyRow.addView(pill("−") { step(-1) }, LinearLayout.LayoutParams(px(52f), px(40f)))
        everyRow.addView(everyText, LinearLayout.LayoutParams(0, -2, 1f))
        everyRow.addView(pill("+") { step(1) }, LinearLayout.LayoutParams(px(52f), px(40f)))
        plan.addView(everyRow)

        val timeRow = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, px(8f), 0, 0) }
        timeRow.addView(label(a.getString(R.string.dav_time), 15f, Ui.TEXT), LinearLayout.LayoutParams(0, -2, 1f))
        val timeBtn = pill("") {
            TimePickerDialog(a, { _, h, m -> sched = sched.copy(hour = h, minute = m); refreshPlan() },
                sched.hour, sched.minute, android.text.format.DateFormat.is24HourFormat(a)).also { it.show(); Ui.glassDialog(it) }
        }
        timeRow.addView(timeBtn, LinearLayout.LayoutParams(px(96f), px(40f)))
        plan.addView(timeRow)

        val wifi = CheckBox(a).apply {
            setText(R.string.dav_wifi); textSize = 15f; setTextColor(Ui.TEXT); buttonTintList = tint
            isChecked = p.syncWifi; minHeight = px(44f)
        }
        plan.addView(wifi, gap(4f))
        val nextText = label("", 13.5f, Ui.TEXT2, 6f)
        plan.addView(nextText)
        box.addView(plan, gap(6f))

        refreshPlan = {
            val r = sched.repeat
            daysRow.visibility = if (r == Repeat.WEEKDAYS) View.VISIBLE else View.GONE
            everyRow.visibility = if (r == Repeat.EVERY_N) View.VISIBLE else View.GONE
            timeRow.visibility = if (r == Repeat.OFF) View.GONE else View.VISIBLE
            wifi.visibility = timeRow.visibility
            everyText.text = a.resources.getQuantityString(R.plurals.every_days, sched.every, sched.every)
            timeBtn.text = String.format(Locale.ROOT, "%02d:%02d", sched.hour, sched.minute)
            val preview = if (r == Repeat.EVERY_N) sched.anchoredAt(System.currentTimeMillis()) else sched
            val next = preview.next(System.currentTimeMillis())
            nextText.text = when {
                r == Repeat.OFF -> a.getString(R.string.dav_sched_off)
                next == null -> a.getString(R.string.dav_no_days)
                else -> a.getString(R.string.dav_next, whenFmt.format(Date(next)))
            }
        }
        group.setOnCheckedChangeListener { g, id ->
            sched = sched.copy(repeat = g.findViewById<View>(id).tag as Repeat)
            if (sched.repeat == Repeat.EVERY_N && sched.every < 2) sched = sched.copy(every = 2)
            refreshPlan()
        }
        refreshPlan()

        // ---------- восстановление ----------
        box.addView(label(a.getString(R.string.dav_restore_hint), 12.5f, Ui.TEXT3, 14f))
        val dialog = AlertDialog.Builder(a)
            .setView(ScrollView(a).apply { addView(box) })
            .setPositiveButton(R.string.dav_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        box.addView(pill(a.getString(R.string.dav_open)) {
            network({ serverLists(p) }) { r ->
                @Suppress("UNCHECKED_CAST") val groups = r as List<Group>
                if (groups.isEmpty()) { status.setTextColor(Ui.WARN); status.setText(R.string.dav_no_files); return@network }
                status.text = ""
                pickGroup(a, p, groups, whenFmt) { dialog.dismiss() }
            }
        }, LinearLayout.LayoutParams(-1, px(44f)).apply { topMargin = px(6f); bottomMargin = px(8f) })

        dialog.show()
        Ui.glassDialog(dialog)
        // «Сохранить» проверяет адрес и не закрывает окно при ошибке — обработчик ставим после show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (busy || !saveServer()) return@setOnClickListener
            if (sched.repeat != Repeat.OFF && p.davUrl.isEmpty()) {
                status.setTextColor(Ui.WARN); status.setText(R.string.dav_no_server); return@setOnClickListener
            }
            // отсчёт «каждые N дней» начинаем заново, только если что-то в нём поменялось
            val old = p.schedule()
            if (sched.repeat == Repeat.EVERY_N && (old.repeat != Repeat.EVERY_N || old.every != sched.every ||
                    old.hour != sched.hour || old.minute != sched.minute || old.anchor <= 0))
                sched = sched.anchoredAt(System.currentTimeMillis())
            p.setSchedule(sched)
            p.syncWifi = wifi.isChecked
            Sync.schedule(a)
            dialog.dismiss()
            a.refreshSummary()
        }
    }

    /** Списки одного телефона: [folder] — его папка на сервере («» — файлы прямо в основной папке). */
    private class Group(val folder: String, val files: List<WebDav.Entry>)

    private fun isList(e: WebDav.Entry) = !e.dir && (e.name.endsWith(".json", true) || e.name.endsWith(".csv", true))

    private fun newestFirst(files: List<WebDav.Entry>) =
        files.sortedWith(compareByDescending<WebDav.Entry> { it.modified }.thenByDescending { it.name })

    /** Списки на сервере по телефонам (папкам), самые свежие сверху; файлы из основной папки (старые версии AppShelf) — отдельно. */
    private fun serverLists(p: Prefs): List<Group> {
        val dav = Sync.dav(p)
        val root = dav.list()
        val groups = root.filter { it.dir }.map { d -> Group(d.name, newestFirst(dav.list(d.name).filter(::isList))) }
            .filter { it.files.isNotEmpty() }
            .sortedByDescending { it.files.first().modified }
        val loose = newestFirst(root.filter(::isList))
        return if (loose.isEmpty()) groups else groups + Group("", loose)
    }

    /** Выбор телефона; если он один — сразу его списки. */
    private fun pickGroup(a: MainActivity, p: Prefs, groups: List<Group>, fmt: SimpleDateFormat, opened: () -> Unit) {
        if (groups.size == 1) return pickFile(a, p, groups[0], fmt, opened)
        val items = groups.map { g ->
            val newest = g.files.first().modified.takeIf { it > 0 }?.let { a.getString(R.string.dav_newest, fmt.format(Date(it))) }
            listOfNotNull(g.folder.ifEmpty { a.getString(R.string.dav_root_files) },
                a.resources.getQuantityString(R.plurals.versions, g.files.size, g.files.size), newest).joinToString(" · ")
        }.toTypedArray()
        AlertDialog.Builder(a)
            .setTitle(R.string.dav_pick_device)
            .setItems(items) { _, i -> pickFile(a, p, groups[i], fmt, opened) }
            .setNegativeButton(android.R.string.cancel, null)
            .show().also { Ui.glassDialog(it) }
    }

    /** Открыть список с сервера (из меню «Открыть сохранённый список»). */
    fun openFromServer(a: MainActivity) {
        val p = Prefs(a)
        android.widget.Toast.makeText(a, R.string.dav_connecting, android.widget.Toast.LENGTH_SHORT).show()
        Thread {
            val r = try { serverLists(p) } catch (e: Exception) { e }
            a.runOnUiThread {
                when {
                    r is Exception -> android.widget.Toast.makeText(a, Sync.error(a, r), android.widget.Toast.LENGTH_LONG).show()
                    (r as List<*>).isEmpty() -> android.widget.Toast.makeText(a, R.string.dav_no_files, android.widget.Toast.LENGTH_LONG).show()
                    else -> @Suppress("UNCHECKED_CAST") pickGroup(a, p, r as List<Group>,
                        SimpleDateFormat("EEE, d MMM, HH:mm", Locale.getDefault())) {}
                }
            }
        }.start()
    }

    /** Выбор списка с сервера — самые свежие сверху; выбранный открывается в режиме восстановления. */
    private fun pickFile(a: MainActivity, p: Prefs, g: Group, fmt: SimpleDateFormat, opened: () -> Unit) {
        val files = g.files
        val items = files.map { f ->
            listOfNotNull(f.name, f.modified.takeIf { it > 0 }?.let { fmt.format(Date(it)) },
                f.size.takeIf { it > 0 }?.let { android.text.format.Formatter.formatShortFileSize(a, it) }).joinToString(" · ")
        }.toTypedArray()
        AlertDialog.Builder(a)
            .setTitle(g.folder.ifEmpty { a.getString(R.string.dav_pick) })
            .setItems(items) { _, i ->
                Thread {
                    val snap = try { ListFile.read(Sync.dav(p).get(if (g.folder.isEmpty()) files[i].name else g.folder + "/" + files[i].name).toString(Charsets.UTF_8)) } catch (e: Exception) { e }
                    a.runOnUiThread {
                        if (snap is Snapshot && snap.apps.isNotEmpty()) { opened(); a.showSnapshot(snap) }
                        else android.widget.Toast.makeText(a, if (snap is Exception && snap !is IllegalArgumentException &&
                                snap !is org.json.JSONException) Sync.error(a, snap) else a.getString(R.string.open_failed),
                            android.widget.Toast.LENGTH_LONG).show()
                    }
                }.start()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show().also { Ui.glassDialog(it) }
    }
}
