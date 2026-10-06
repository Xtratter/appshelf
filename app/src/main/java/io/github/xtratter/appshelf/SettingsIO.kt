package io.github.xtratter.appshelf

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Перенос настроек AppShelf: в файл или на WebDAV (в папку этого телефона, settings.json).
 * Пароль WebDAV — только если выбрали, и только зашифрованный парольной фразой ([Passphrase]). Старые файлы с паролем
 * открытым текстом (до 1.37.2) по-прежнему читаются.
 */
object SettingsIO {
    private const val FORMAT = "AppShelf-settings"
    const val DAV_FILE = "settings.json"

    /** Есть ли в файле пароль, зашифрованный фразой (тогда при восстановлении фразу нужно спросить). */
    fun needsPassphrase(text: String): Boolean = try { JSONObject(text).has("davPasswordEnc") } catch (e: Exception) { false }

    /** [passphrase] не null — положить в файл пароль WebDAV, зашифрованный этой фразой. */
    fun write(ctx: Context, passphrase: String?): String {
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
            if (passphrase != null && p.davPass.isNotEmpty()) put("davPasswordEnc", Passphrase.encrypt(p.davPass, passphrase))
        }.toString(2) + "\n"
    }

    /**
     * Разобрать и применить; true — в файле был пароль WebDAV. Если пароль зашифрован, нужна [passphrase];
     * при неверной фразе бросает [Passphrase.Wrong] до того, как что-либо изменено.
     */
    fun apply(ctx: Context, text: String, passphrase: String? = null): Boolean {
        val o = JSONObject(text)
        require(o.optString("format") == FORMAT) { "not AppShelf settings" }
        val encrypted = o.optJSONObject("davPasswordEnc")?.let { Passphrase.decrypt(it, passphrase ?: throw Passphrase.Wrong()) }
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
        if (encrypted != null) { p.davPass = encrypted; return true }
        if (o.has("davPassword")) { p.davPass = o.optString("davPassword", ""); return true }   // старый файл, пароль открытым текстом
        return false
    }

    /** На сервер, в папку этого телефона. Только в фоне. */
    fun upload(ctx: Context, passphrase: String?) {
        val p = Prefs(ctx)
        Sync.dav(p).put(Sync.deviceFolder(ctx, p) + "/" + DAV_FILE, write(ctx, passphrase).toByteArray(), "application/json")
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
