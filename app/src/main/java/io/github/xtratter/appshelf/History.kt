package io.github.xtratter.appshelf

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * «Что изменилось»: журнал установок и удалений.
 * Установки — по дате установки из Android; удаления AppShelf замечает сам при каждом открытии (приложение было,
 * а теперь нет) и может дополнить их из версий списка на сервере WebDAV (сравнивая соседние версии).
 */
object History {
    /** Одно событие: приложение [pkg] установлено ([installed] = true) или удалено в момент [time]. */
    data class Event(val pkg: String, val label: String, val source: String, val installed: Boolean, val time: Long)

    /** Что знаем о приложении: когда появилось и когда пропало (0 — не пропадало или уже вернулось). */
    data class Entry(val label: String, val source: String, val installed: Long, val removed: Long)

    // ---------- без Android: проверяется unit-тестами ----------

    /**
     * Обновить журнал [log] по текущему списку [apps] в момент [now]: новые — установлены (дата установки из Android),
     * вернувшиеся — установлены заново, пропавшие — удалены сейчас.
     */
    fun update(log: Map<String, Entry>, apps: List<AppInfo>, now: Long): Map<String, Entry> {
        val out = HashMap(log)
        val have = HashSet<String>()
        for (a in apps) {
            have += a.pkg
            val e = out[a.pkg]
            if (e == null || e.removed > 0)
                out[a.pkg] = Entry(a.label, a.source.name, if (e != null) maxOf(a.firstInstall, e.removed) else a.firstInstall, 0)
        }
        for ((pkg, e) in log) if (pkg !in have && e.removed == 0L) out[pkg] = e.copy(removed = now)
        return out
    }

    /** События из версий списка: между соседними версиями — что появилось и что пропало (по времени новой версии). */
    fun fromVersions(versions: List<Snapshot>): List<Event> {
        val sorted = versions.filter { it.created > 0 }.sortedBy { it.created }
        val out = ArrayList<Event>()
        for (i in 1 until sorted.size) {
            val before = sorted[i - 1].apps.associateBy { it.pkg }
            val after = sorted[i].apps.associateBy { it.pkg }
            for ((pkg, a) in before) if (pkg !in after) out += Event(pkg, a.label, a.source.name, false, sorted[i].created)
            for ((pkg, a) in after) if (pkg !in before) out += Event(pkg, a.label, a.source.name, true,
                a.firstInstall.takeIf { it > 0 } ?: sorted[i].created)
        }
        return out
    }

    /** Все события журнала (и из версий), самые свежие сверху, без повторов. */
    fun events(log: Map<String, Entry>, extra: List<Event> = emptyList()): List<Event> {
        val out = ArrayList<Event>()
        for ((pkg, e) in log) {
            if (e.installed > 0) out += Event(pkg, e.label, e.source, true, e.installed)
            if (e.removed > 0) out += Event(pkg, e.label, e.source, false, e.removed)
        }
        // из версий — только то, чего журнал не знает (с точностью до суток)
        val day = 24 * 60 * 60 * 1000L
        for (x in extra) if (out.none { it.pkg == x.pkg && it.installed == x.installed && kotlin.math.abs(it.time - x.time) < day }) out += x
        return out.sortedByDescending { it.time }
    }

    /** Сравнение двух списков: что есть только в [a] и что только в [b] (по названию). */
    fun compare(a: List<AppInfo>, b: List<AppInfo>): Pair<List<AppInfo>, List<AppInfo>> {
        val ap = a.mapTo(HashSet()) { it.pkg }
        val bp = b.mapTo(HashSet()) { it.pkg }
        return ListFile.sorted(a.filter { it.pkg !in bp }) to ListFile.sorted(b.filter { it.pkg !in ap })
    }

    // ---------- с Android ----------

    private fun file(ctx: Context) = File(ctx.filesDir, "history.json")
    private var log: Map<String, Entry>? = null
    /** События из версий на сервере (загружаются по кнопке, живут до закрытия приложения). */
    var fromServer: List<Event> = emptyList()

    @Synchronized
    fun log(ctx: Context): Map<String, Entry> = log ?: runCatching {
        val o = JSONObject(file(ctx).readText())
        o.keys().asSequence().associateWith { k ->
            val e = o.getJSONObject(k)
            Entry(e.optString("l"), e.optString("s"), e.optLong("i"), e.optLong("r"))
        }
    }.getOrDefault(emptyMap()).also { log = it }

    /** Отметить текущий список установленных (вызывается при каждом чтении списка). Только в фоне. */
    @Synchronized
    fun record(ctx: Context, apps: List<AppInfo>) {
        val old = log(ctx)
        val new = update(old, apps.filter { !it.system }, System.currentTimeMillis())
        if (new == old) return
        log = new
        val o = JSONObject()
        for ((k, e) in new) o.put(k, JSONObject().apply { put("l", e.label); put("s", e.source); put("i", e.installed); put("r", e.removed) })
        file(ctx).writeText(o.toString())
    }
}
