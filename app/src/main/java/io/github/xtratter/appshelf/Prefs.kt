package io.github.xtratter.appshelf

import android.content.Context
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Сохранённые настройки. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var theme by str("theme", Theme.STANDARD.name)
    fun theme(): Theme = runCatching { Theme.valueOf(theme) }.getOrDefault(Theme.STANDARD)

    /** Показывать системные приложения (предустановленные, без обновлений из магазина — тоже). */
    var showSystem by bool("show_system", false)

    /** Пакеты, которые не включаем в сохраняемый список (файл, «Поделиться», автосохранение). */
    var excluded: Set<String>
        get() = sp.getStringSet("excluded", null)?.toSet() ?: emptySet()   // копия: set из prefs менять нельзя
        set(v) = sp.edit().putStringSet("excluded", HashSet(v)).apply()

    /** Файл автосохранения (content://…, выбран пользователем) — список обновляется в нём при каждом запуске. */
    var autosaveUri by str("autosave_uri", "")
    /** Когда список последний раз сохранялся (любым способом) и куда. */
    var lastSaved by long("last_saved", 0L)
    var lastSavedName by str("last_saved_name", "")

    private fun bool(key: String, def: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getBoolean(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) =
            sp.edit().putBoolean(key, value).apply()
    }

    private fun long(key: String, def: Long) = object : ReadWriteProperty<Any?, Long> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getLong(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) =
            sp.edit().putLong(key, value).apply()
    }

    private fun str(key: String, def: String) = object : ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getString(key, def) ?: def
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) =
            sp.edit().putString(key, value).apply()
    }
}
