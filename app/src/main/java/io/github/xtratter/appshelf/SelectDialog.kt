package io.github.xtratter.appshelf

import android.app.AlertDialog
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/**
 * Какие приложения включать в сохраняемый список: галочки, поиск и «все / никакие» для найденных.
 * [done] получает новый набор исключённых пакетов.
 */
object SelectDialog {
    fun show(a: MainActivity, apps: List<AppInfo>, excluded: Set<String>, done: (Set<String>) -> Unit) {
        val dp = a.resources.displayMetrics.density
        fun px(v: Float) = (v * dp).toInt()
        val excl = HashSet(excluded)
        var shown = apps

        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20f), px(20f), px(20f), 0)
        }
        box.addView(TextView(a).apply {
            setText(R.string.select_title); textSize = 20f; typeface = Ui.medium; setTextColor(Ui.TEXT)
        })
        val counter = TextView(a).apply { textSize = 13.5f; setTextColor(Ui.TEXT2); setPadding(0, px(4f), 0, px(12f)) }
        box.addView(counter)
        val search = EditText(a).apply {
            setHint(R.string.search_hint)
            setHintTextColor(Ui.TEXT3)
            setTextColor(Ui.TEXT)
            textSize = 15f
            maxLines = 1
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            background = GlassDrawable(a, 22f, Ui.ink(0x10))
            setPadding(px(16f), px(10f), px(16f), px(10f))
        }
        box.addView(search, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(8f) })

        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(position: Int) = shown[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView as? LinearLayout ?: newRow(a, ::px)
                val app = shown[position]
                val icon = row.getChildAt(0) as ImageView
                val texts = row.getChildAt(1) as LinearLayout
                val check = row.getChildAt(2) as CheckBox
                icon.setImageBitmap(Icons.get(a, app.pkg, px(36f)) { notifyDataSetChanged() })
                (texts.getChildAt(0) as TextView).text = app.label
                (texts.getChildAt(1) as TextView).text = app.pkg + " · " + a.sourceText(app)
                check.isChecked = app.pkg !in excl
                row.alpha = if (check.isChecked) 1f else 0.55f
                return row
            }
        }
        val list = ListView(a).apply {
            divider = null
            this.adapter = adapter
            setOnItemClickListener { _, _, pos, _ ->
                val pkg = shown[pos].pkg
                if (!excl.remove(pkg)) excl += pkg
                adapter.notifyDataSetChanged()
            }
        }
        // список занимает большую часть экрана, кнопки диалога остаются видны
        box.addView(list, LinearLayout.LayoutParams(-1, (a.resources.displayMetrics.heightPixels * 0.55f).toInt()))

        val dialog = AlertDialog.Builder(a)
            .setView(box)
            .setPositiveButton(R.string.done) { _, _ -> done(excl) }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.select_all, null)
            .create()

        fun refresh() {
            val included = apps.count { it.pkg !in excl }
            counter.text = a.getString(R.string.select_count, included, apps.size)
            // «все / никакие» действует на найденные строки: если все отмечены — снимаем, иначе отмечаем
            val allOn = shown.all { it.pkg !in excl }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setText(if (allOn) R.string.select_none else R.string.select_all)
        }
        adapter.registerDataSetObserver(object : android.database.DataSetObserver() {
            override fun onChanged() = refresh()
        })
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty().trim().lowercase()
                shown = if (q.isEmpty()) apps else apps.filter { q in it.label.lowercase() || q in it.pkg.lowercase() }
                adapter.notifyDataSetChanged()
            }
        })

        dialog.show()
        Ui.glassDialog(dialog)
        // кнопка не должна закрывать диалог — ставим обработчик после show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            if (shown.all { it.pkg !in excl }) shown.forEach { excl += it.pkg } else shown.forEach { excl -= it.pkg }
            adapter.notifyDataSetChanged()
        }
        refresh()
    }

    /** Строка: значок, название и «пакет · источник», галочка справа. */
    private fun newRow(a: MainActivity, px: (Float) -> Int) = LinearLayout(a).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = px(60f)
        setPadding(px(4f), px(6f), 0, px(6f))
        background = Ui.ripple(a, 14f)
        addView(ImageView(a), LinearLayout.LayoutParams(px(36f), px(36f)))
        addView(LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12f), 0, px(8f), 0)
            addView(TextView(a).apply {
                textSize = 15f; setTextColor(Ui.TEXT); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            })
            addView(TextView(a).apply {
                textSize = 12f; setTextColor(Ui.TEXT2); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(CheckBox(a).apply {
            isClickable = false
            isFocusable = false
            buttonTintList = android.content.res.ColorStateList.valueOf(Ui.primary)
        })
    }
}
