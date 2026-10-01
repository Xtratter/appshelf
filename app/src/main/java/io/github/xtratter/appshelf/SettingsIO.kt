package io.github.xtratter.appshelf

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Перенос настроек AppShelf: в файл или на WebDAV (в папку этого телефона, settings.json).
 * Пароль WebDAV — только если выбрали (в файле он открытым текстом).
 */
object SettingsIO {
    private const val FORMAT = "AppShelf-settings"
    const val DAV_FILE = "settings.json"

    fun write(ctx: Context, withPassword: Boolean): String {
        val p = Prefs(ctx)
        val values = JSONObject()
        for ((k, v) in p.exportMap()) values.put(k, JSONObject().apply {
            when (v) {
                is Boolean -> { put("t", "b"); put("v", v) }
                is Int -> { put("t", "i"); put("v", v) }
                is Long -> { put("t", "l"); put("v", v) }
                is Set<*> -> { put("t", "set"); put("v", JSONArray(v.map { it.toString() }.sorted())) }
                else -> { put("t", "s"); put("v", v.toString()) }
            }
        })
        return JSONObject().apply {
            put("format", FORMAT); put("version", 1)
            put("device", Apps.device(ctx))
            put("created", System.currentTimeMillis())
            put("settings", values)
            if (withPassword) put("davPassword", p.davPass)
        }.toString(2) + "\n"
    }

    /** Разобрать и применить; true — в файле был пароль WebDAV. */
    fun apply(ctx: Context, text: String): Boolean {
        val o = JSONObject(text)
        require(o.optString("format") == FORMAT) { "not AppShelf settings" }
        val vals = o.getJSONObject("settings")
        val m = LinkedHashMap<String, Any>()
        for (k in vals.keys()) {
            val e = vals.getJSONObject(k)
            m[k] = when (e.optString("t")) {
                "b" -> e.getBoolean("v")
                "i" -> e.getInt("v")
                "l" -> e.getLong("v")
                "set" -> e.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                else -> e.getString("v")
            }
        }
        val p = Prefs(ctx)
        p.importMap(m)
        val pass = o.optString("davPassword", "")
        if (o.has("davPassword")) p.davPass = pass
        return o.has("davPassword")
    }

    /** На сервер, в папку этого телефона. Только в фоне. */
    fun upload(ctx: Context, withPassword: Boolean) {
        val p = Prefs(ctx)
        Sync.dav(p).put(Sync.deviceFolder(ctx, p) + "/" + DAV_FILE, write(ctx, withPassword).toByteArray(), "application/json")
        p.settingsSaved = System.currentTimeMillis()
    }

    /** Телефоны на сервере, у которых есть сохранённые настройки. Только в фоне. */
    fun phones(ctx: Context): List<String> {
        val dav = Sync.dav(Prefs(ctx))
        return dav.list().filter { it.dir }.map { it.name }.filter { d ->
            runCatching { dav.list(d).any { !it.dir && it.name == DAV_FILE } }.getOrDefault(false)
        }
    }

    fun download(ctx: Context, phone: String): String =
        Sync.dav(Prefs(ctx)).get("$phone/$DAV_FILE").toString(Charsets.UTF_8)
}
