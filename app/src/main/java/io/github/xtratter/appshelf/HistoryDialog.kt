package io.github.xtratter.appshelf

import io.github.xtratter.uikit.Haptics
import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** «Что изменилось»: лента установок и удалений за период и сравнение с другим телефоном. */
object HistoryDialog {
    private enum class Period(val title: Int, val days: Int) {
        WEEK(R.string.hi_week, 7), MONTH(R.string.hi_month, 30), QUARTER(R.string.hi_quarter, 90), ALL(R.string.hi_all, 0)
    }

    fun show(a: MainActivity) {
        val dp = a.resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()
        val dayFmt = SimpleDateFormat("d MMMM yyyy, EEEE", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val p = Prefs(a)
        var period = Period.MONTH

        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20f), px(20f), px(20f), 0)
        }
        box.addView(TextView(a).apply { setText(R.string.hi_title); textSize = 20f; typeface = Ui.medium; setTextColor(Ui.TEXT) })
        val chips = LinearLayout(a).apply { setPadding(0, px(10f), 0, px(6f)) }
        box.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(chips) })
        val summary = TextView(a).apply { textSize = 13.5f; setTextColor(Ui.TEXT2); setPadding(px(2f), 0, 0, px(8f)) }
        box.addView(summary)

        var items: List<Any> = emptyList()
        val adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(i: Int) = items[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getViewTypeCount() = 2
            override fun getItemViewType(i: Int) = if (items[i] is String) 0 else 1
            override fun isEnabled(i: Int) = false
            override fun getView(i: Int, convert: View?, parent: ViewGroup): View {
                val it = items[i]
                if (it is String) return (convert as? TextView ?: TextView(a).apply {
                    textSize = 13.5f; typeface = Ui.medium; setTextColor(Ui.primary); setPadding(px(4f), px(12f), 0, px(4f))
                }).also { v -> v.text = it }
                val e = it as History.Event
                val row = convert as? LinearLayout ?: LinearLayout(a).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(px(4f), px(6f), px(4f), px(6f))
                    addView(ImageView(a), LinearLayout.LayoutParams(px(32f), px(32f)))
                    addView(LinearLayout(a).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(px(12f), 0, 0, 0)
                        addView(TextView(a).apply { textSize = 15f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                        addView(TextView(a).apply { textSize = 12.5f; setTextColor(Ui.TEXT2); maxLines = 1 })
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                }
                val icon = row.getChildAt(0) as ImageView
                val texts = row.getChildAt(1) as LinearLayout
                icon.setImageBitmap(if (Apps.isInstalled(a, e.pkg)) Icons.get(a, e.pkg, px(32f)) { notifyDataSetChanged() } else null)
                icon.background = if (icon.drawable == null) Ui.pill(a, Ui.withAlpha(if (e.installed) Ui.OK else Ui.WARN, 0.35f)) else null
                (texts.getChildAt(0) as TextView).apply {
                    text = (if (e.installed) "+ " else "− ") + e.label
                    setTextColor(if (e.installed) Ui.TEXT else Ui.TEXT2)
                }
                val src = runCatching { a.getString(Source.valueOf(e.source).title) }.getOrDefault(e.source)
                (texts.getChildAt(1) as TextView).text = a.getString(if (e.installed) R.string.hi_installed else R.string.hi_removed) +
                    " · " + timeFmt.format(Date(e.time)) + " · " + src
                return row
            }
        }
        val list = ListView(a).apply { divider = null; this.adapter = adapter }
        box.addView(list, LinearLayout.LayoutParams(-1, (a.resources.displayMetrics.heightPixels * 0.5f).toInt()))

        fun rebuild() {
            val from = if (period.days == 0) 0L else System.currentTimeMillis() - period.days * 24L * 60 * 60 * 1000
            val events = History.events(History.log(a), History.fromServer).filter { it.time >= from }
            val out = ArrayList<Any>()
            var day = ""
            for (e in events) {
                val d = dayFmt.format(Date(e.time)).replaceFirstChar { it.titlecase() }
                if (d != day) { day = d; out += d }
                out += e
            }
            items = out
            adapter.notifyDataSetChanged()
            summary.text = if (events.isEmpty()) a.getString(R.string.hi_nothing)
            else a.getString(R.string.hi_summary, events.count { it.installed }, events.count { !it.installed })
            chips.removeAllViews()
            for (per in Period.entries) chips.addView(TextView(a).apply {
                setText(per.title); textSize = 13.5f; typeface = Ui.medium; gravity = Gravity.CENTER
                setPadding(px(14f), 0, px(14f), 0)
                setTextColor(if (per == period) Ui.ON_ACCENT else Ui.TEXT)
                background = if (per == period) Ui.pill(a, Ui.primary) else Ui.pill(a, Ui.card, Ui.ink(0x33))
                setOnClickListener { period = per; Haptics.play(Haptics.Kind.TICK); rebuild() }
            }, LinearLayout.LayoutParams(-2, px(34f)).apply { rightMargin = px(8f) })
        }

        // дополнить удалениями из прошлого — по версиям списка этого телефона на сервере
        if (p.davUrl.isNotEmpty()) box.addView(TextView(a).apply {
            setText(R.string.hi_from_server); textSize = 14f; setTextColor(Ui.primary); setPadding(px(4f), px(10f), 0, px(6f))
            setOnClickListener {
                setText(R.string.dav_connecting)
                Thread {
                    val r = runCatching { loadVersions(a) }
                    a.ui {
                        r.onSuccess { History.fromServer = History.fromVersions(it); setText(a.getString(R.string.hi_loaded, it.size)); rebuild() }
                            .onFailure { e -> setText(Sync.error(a, e as? Exception ?: Exception(e))) }
                    }
                }.start()
            }
        })

        val b = AlertDialog.Builder(a).setView(box).setNegativeButton(R.string.close, null)
        if (p.davUrl.isNotEmpty()) b.setNeutralButton(R.string.hi_compare) { _, _ -> pickPhone(a) }
        val dialog = b.create()
        rebuild()
        dialog.show()
        Ui.glassDialog(dialog)
    }

    /** Версии списка этого телефона с сервера (последние 40). Только в фоне. */
    private fun loadVersions(a: MainActivity): List<Snapshot> {
        val p = Prefs(a)
        val dav = Sync.dav(p)
        val folder = Sync.deviceFolder(a, p)
        val names = dav.list(folder).filter { !it.dir && it.name.endsWith(".json", true) && it.name != SettingsIO.DAV_FILE }.map { it.name }.sorted().takeLast(40)
        return names.mapNotNull { n -> runCatching { ListFile.read(dav.get("$folder/$n").toString(Charsets.UTF_8)) }.getOrNull() }
    }

    /** Выбрать другой телефон на сервере и сравнить его последний список с этим телефоном. */
    private fun pickPhone(a: MainActivity) {
        val p = Prefs(a)
        Toast.makeText(a, R.string.dav_connecting, Toast.LENGTH_SHORT).show()
        Thread {
            val r = runCatching {
                val dav = Sync.dav(p)
                val mine = Sync.deviceFolder(a, p)
                dav.list().filter { it.dir && it.name != mine && it.name != "apk" }.mapNotNull { d ->
                    val newest = dav.list(d.name).filter { !it.dir && it.name.endsWith(".json", true) && it.name != SettingsIO.DAV_FILE }.maxByOrNull { it.modified }
                    newest?.let { d.name to it.name }
                }
            }
            a.ui {
                val phones = r.getOrElse { e -> Toast.makeText(a, Sync.error(a, e as? Exception ?: Exception(e)), Toast.LENGTH_LONG).show(); return@ui }
                if (phones.isEmpty()) { Toast.makeText(a, R.string.hi_no_phones, Toast.LENGTH_LONG).show(); return@ui }
                AlertDialog.Builder(a).setTitle(R.string.hi_compare)
                    .setItems(phones.map { it.first }.toTypedArray()) { _, i -> compare(a, phones[i].first, phones[i].second) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show().also { Ui.glassDialog(it) }
            }
        }.start()
    }

    private fun compare(a: MainActivity, phone: String, file: String) = PhoneCompare.fromServer(a, phone, "$phone/$file")
}
