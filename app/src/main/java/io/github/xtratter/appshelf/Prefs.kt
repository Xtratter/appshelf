package io.github.xtratter.appshelf

import android.content.Context
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Сохранённые настройки. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    var theme by str("theme", Theme.DEFAULT.name)
    fun theme(): Theme = runCatching { Theme.valueOf(theme) }.getOrDefault(Theme.DEFAULT)


    /** Показывать системные приложения (предустановленные, без обновлений из магазина — тоже). */
    var showSystem by bool("show_system", false)

    /** Резервные копии APK: куда ([ApkBackup.Dest]), папка на телефоне, что копировать, обновлять ли при отправке. */
    var apkDest by str("apk_dest", "")
    var apkFolderUri by str("apk_folder_uri", "")
    var apkScope by str("apk_scope", ApkBackup.Scope.APK_ONLY.name)
    var apkAuto by bool("apk_auto", false)

    /** Сила отклика вибрацией ([Haptics.Level]). */
    var haptics by str("haptics", Haptics.Level.MEDIUM.name)
    fun haptics(): Haptics.Level = runCatching { Haptics.Level.valueOf(haptics) }.getOrDefault(Haptics.Level.MEDIUM)
    var hapticsEngine by str("haptics_engine", Haptics.Engine.AUTO.name)



    /** Пакеты, которые не включаем в сохраняемый список (файл, «Поделиться», автосохранение). */
    var excluded: Set<String>
        get() = sp.getStringSet("excluded", null)?.toSet() ?: emptySet()   // копия: set из prefs менять нельзя
        set(v) = sp.edit().putStringSet("excluded", HashSet(v)).apply()

    /** Файл автосохранения (content://…, выбран пользователем) — список обновляется в нём при каждом запуске. */
    var autosaveUri by str("autosave_uri", "")
    /** Когда список последний раз сохранялся (любым способом) и куда. */
    var lastSaved by long("last_saved", 0L)
    var lastSavedName by str("last_saved_name", "")

    // ---------- WebDAV ----------

    /** Папка на сервере, например https://cloud.example.com/remote.php/dav/files/имя/AppShelf. */
    var davUrl by str("dav_url", "")
    var davUser by str("dav_user", "")
    /** Пароль хранится зашифрованным ключом Android Keystore ([Secret]). */
    var davPass: String
        get() = Secret.decrypt(sp.getString("dav_pass", null).orEmpty())
        set(v) = sp.edit().putString("dav_pass", if (v.isEmpty()) "" else Secret.encrypt(v)).apply()
    /** Папка этого телефона на сервере; пустая — имя телефона («POCO F3»). */
    var davDevice by str("dav_device", "")
    /** Сколько последних версий хранить на сервере; 1 — один файл, который каждый раз перезаписывается. */
    var davKeep by int("dav_keep", 10)

    var syncRepeat by str("sync_repeat", Repeat.OFF.name)
    /** По умолчанию — будни (Calendar: 1 = воскресенье … 7 = суббота). */
    var syncDays by int("sync_days", (2..6).sumOf { Schedule.bit(it) })
    var syncEvery by int("sync_every", 3)
    var syncHour by int("sync_hour", 21)
    var syncMinute by int("sync_minute", 0)
    var syncAnchor by long("sync_anchor", 0L)
    var syncWifi by bool("sync_wifi", false)

    fun schedule() = Schedule(runCatching { Repeat.valueOf(syncRepeat) }.getOrDefault(Repeat.OFF),
        syncHour, syncMinute, syncDays, syncEvery, syncAnchor)

    fun setSchedule(s: Schedule) {
        sp.edit().putString("sync_repeat", s.repeat.name).putInt("sync_days", s.days).putInt("sync_every", s.every)
            .putInt("sync_hour", s.hour).putInt("sync_minute", s.minute).putLong("sync_anchor", s.anchor).apply()
    }

    /** Когда запланирована следующая отправка (0 — не запланирована) и чем закончилась последняя. */
    var syncNext by long("sync_next", 0L)
    var syncLast by long("sync_last", 0L)
    var syncOk by bool("sync_ok", false)
    var syncMsg by str("sync_msg", "")
    /** Каталог ссылок (sources.json); пустой адрес — каталог выключен. */
    var catalogUrl by str("catalog_url", LinkStore.DEFAULT_CATALOG)
    var catalogFetched by long("catalog_fetched", 0L)
    var catalogFetchedUrl by str("catalog_fetched_url", "")
    var catalogEtag by str("catalog_etag", "")
    /** Свои ссылки изменены, но ещё не отправлены на WebDAV; когда последний раз сливали с сервером. */
    var linksDirty by bool("links_dirty", false)
    var linksSynced by long("links_synced", 0L)

    /** Плановая отправка ещё не состоялась (пропущена, не было сети, ошибка) — выполнить при первой возможности. */
    var syncPending by bool("sync_pending", false)

    private fun bool(key: String, def: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getBoolean(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) =
            sp.edit().putBoolean(key, value).apply()
    }

    private fun int(key: String, def: Int) = object : ReadWriteProperty<Any?, Int> {
        override fun getValue(thisRef: Any?, property: KProperty<*>) = sp.getInt(key, def)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) =
            sp.edit().putInt(key, value).apply()
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
