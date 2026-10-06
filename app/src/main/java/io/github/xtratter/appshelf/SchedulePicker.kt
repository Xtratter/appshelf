package io.github.xtratter.appshelf

import io.github.xtratter.uikit.Haptics
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

/** Расписание отправки: режим повтора, дни недели или «каждые N дней», время, только Wi-Fi и подпись «Следующая отправка…». */
internal class SchedulePicker(private val kit: DialogKit, private val p: Prefs, private val whenFmt: SimpleDateFormat) {
    var sched = p.schedule()
        private set
    private lateinit var refresh: () -> Unit
    private lateinit var wifi: CheckBox
    val wifiOnly get() = wifi.isChecked
    val view: LinearLayout = build()

    /** Расписание для сохранения: отсчёт «каждые N дней» начинаем заново, только если что-то в нём поменялось. */
    fun forSaving(): Schedule {
        val old = p.schedule()
        if (sched.repeat == Repeat.EVERY_N && (old.repeat != Repeat.EVERY_N || old.every != sched.every ||
                old.hour != sched.hour || old.minute != sched.minute || old.anchor <= 0))
            sched = sched.anchoredAt(System.currentTimeMillis())
        return sched
    }

    private fun build(): LinearLayout = with(kit) {
        val a = a
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
                setOnClickListener { sched = sched.copy(days = sched.days xor Schedule.bit(dow)); paintDays(); refresh() }
            }
            dayViews += v
            daysRow.addView(v, LinearLayout.LayoutParams(0, px(38f), 1f).apply { if (k > 0) leftMargin = px(4f) })
        }
        paintDays()
        plan.addView(daysRow)

        // «каждые N дней»: − N +
        val everyRow = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, px(6f), 0, px(4f)) }
        val everyText = TextView(a).apply { textSize = 15f; setTextColor(Ui.TEXT); gravity = Gravity.CENTER }
        fun step(d: Int) { sched = sched.copy(every = (sched.every + d).coerceIn(2, 60)); refresh() }
        everyRow.addView(pill("−") { step(-1) }, LinearLayout.LayoutParams(px(52f), px(40f)))
        everyRow.addView(everyText, LinearLayout.LayoutParams(0, -2, 1f))
        everyRow.addView(pill("+") { step(1) }, LinearLayout.LayoutParams(px(52f), px(40f)))
        plan.addView(everyRow)

        val timeRow = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, px(8f), 0, 0) }
        timeRow.addView(label(a.getString(R.string.dav_time), 15f, Ui.TEXT), LinearLayout.LayoutParams(0, -2, 1f))
        val timeBtn = pill("") {
            TimePickerDialog(a, { _, h, m -> sched = sched.copy(hour = h, minute = m); refresh() },
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

        refresh = {
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
            refresh()
        }
        refresh()
        plan
    }
}
