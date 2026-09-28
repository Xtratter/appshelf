package io.github.xtratter.appshelf

import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Сохранённый список: когда и на каком устройстве снят и сами приложения. */
data class Snapshot(val created: Long, val device: String, val apps: List<AppInfo>)

/** Форматы файла со списком. JSON и CSV можно открыть обратно для восстановления. */
enum class Format(val ext: String, val mime: String) {
    JSON("json", "application/json"),
    MARKDOWN("md", "text/markdown"),
    CSV("csv", "text/csv"),
}

/** Запись и чтение списка приложений. Без Android-зависимостей — проверяется unit-тестами. */
object ListFile {
    private const val MAGIC = "AppShelf"

    /** Сортировка по названию «как в словаре»: без учёта регистра, Ё рядом с Е, латиница и кириллица по алфавиту. */
    fun sorted(apps: List<AppInfo>, locale: Locale = Locale.getDefault()): List<AppInfo> {
        val c = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
        return apps.sortedWith { a, b -> c.compare(a.label, b.label).takeIf { it != 0 } ?: a.pkg.compareTo(b.pkg) }
    }

    /** Буква раздела для заголовков списка: первая буква названия, цифры и прочее — «#». */
    fun section(label: String): String {
        val ch = label.trimStart().firstOrNull() ?: return "#"
        return if (ch.isLetter()) ch.uppercaseChar().toString().replace("Ё", "Е") else "#"
    }

    fun date(ms: Long): String = if (ms <= 0) "" else day().format(Date(ms))

    private fun day() = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    private fun iso() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT)

    fun write(s: Snapshot, f: Format, sourceName: (Source) -> String = { it.name }): String = when (f) {
        Format.JSON -> json(s)
        Format.CSV -> csv(s, sourceName)
        Format.MARKDOWN -> markdown(s, sourceName)
    }

    // ---------- JSON ----------

    private fun json(s: Snapshot): String {
        val arr = JSONArray()
        for (a in s.apps) arr.put(JSONObject().apply {
            put("label", a.label)
            put("package", a.pkg)
            put("version", a.versionName)
            put("versionCode", a.versionCode)
            put("source", a.source.name)
            put("installer", a.installer)
            put("initiator", a.initiator)
            put("installed", date(a.firstInstall))
            put("installedMs", a.firstInstall)
            put("updatedMs", a.lastUpdate)
            put("system", a.system)
        })
        return JSONObject().apply {
            put("format", MAGIC)
            put("formatVersion", 1)
            put("created", iso().format(Date(s.created)))
            put("createdMs", s.created)
            put("device", s.device)
            put("count", s.apps.size)
            put("apps", arr)
        }.toString(2) + "\n"
    }

    private fun parseJson(text: String): Snapshot {
        val o = JSONObject(text)
        require(o.optString("format") == MAGIC) { "not an AppShelf list" }
        val arr = o.getJSONArray("apps")
        val apps = (0 until arr.length()).map { i ->
            val a = arr.getJSONObject(i)
            AppInfo(
                label = a.optString("label"),
                pkg = a.getString("package"),
                versionName = a.optString("version"),
                versionCode = a.optLong("versionCode"),
                installer = a.optString("installer"),
                initiator = a.optString("initiator"),
                firstInstall = a.optLong("installedMs"),
                lastUpdate = a.optLong("updatedMs"),
                system = a.optBoolean("system"),
            )
        }
        return Snapshot(o.optLong("createdMs"), o.optString("device"), apps)
    }

    // ---------- CSV ----------

    private val CSV_HEAD = listOf("label", "package", "version", "source", "installer", "initiator", "installed", "system")

    private fun csv(s: Snapshot, sourceName: (Source) -> String): String = buildString {
        append(CSV_HEAD.joinToString(",")).append("\r\n")
        for (a in s.apps) {
            append(listOf(a.label, a.pkg, a.versionName, sourceName(a.source), a.installer, a.initiator,
                date(a.firstInstall), a.system.toString()).joinToString(",") { cell(it) }).append("\r\n")
        }
    }

    private fun cell(v: String) =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    /** Разбор CSV по RFC 4180: кавычки, удвоенные кавычки и переводы строк внутри ячеек. */
    fun csvRows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && text.getOrNull(i + 1) == '"' -> { sb.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == ',' -> { row += sb.toString(); sb.clear() }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += sb.toString(); sb.clear()
                    if (row.any { it.isNotEmpty() }) rows += row
                    row = ArrayList()
                }
                else -> sb.append(ch)
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) { row += sb.toString(); if (row.any { it.isNotEmpty() }) rows += row }
        return rows
    }

    private fun parseCsv(text: String): Snapshot {
        val rows = csvRows(text.removePrefix("\uFEFF"))
        require(rows.isNotEmpty()) { "empty file" }
        val head = rows[0].map { it.trim().lowercase() }
        val iPkg = head.indexOf("package")
        require(iPkg >= 0) { "no package column" }
        fun col(r: List<String>, name: String) = head.indexOf(name).let { if (it >= 0) r.getOrElse(it) { "" } else "" }
        val apps = rows.drop(1).filter { it.getOrElse(iPkg) { "" }.isNotBlank() }.map { r ->
            AppInfo(
                label = col(r, "label").ifBlank { r[iPkg] },
                pkg = r[iPkg].trim(),
                versionName = col(r, "version"),
                installer = col(r, "installer"),
                initiator = col(r, "initiator"),
                firstInstall = parseDay(col(r, "installed")),
                system = col(r, "system").equals("true", ignoreCase = true),
            )
        }
        return Snapshot(0, "", apps)
    }

    private fun parseDay(s: String): Long = try {
        if (s.isBlank()) 0 else day().apply { timeZone = TimeZone.getDefault() }.parse(s.trim())?.time ?: 0
    } catch (e: Exception) { 0 }

    // ---------- Markdown ----------

    private fun markdown(s: Snapshot, sourceName: (Source) -> String): String = buildString {
        append("# AppShelf — ").append(s.apps.size).append(" apps\n\n")
        if (s.device.isNotEmpty()) append(s.device).append(" · ")
        append(iso().format(Date(s.created))).append("\n\n")
        append("| App | Package | Source | Installed |\n|---|---|---|---|\n")
        for (a in s.apps) {
            append("| ").append(md(a.label)).append(" | `").append(a.pkg).append("` | ")
                .append(md(sourceName(a.source))).append(" | ").append(date(a.firstInstall)).append(" |\n")
        }
    }

    private fun md(v: String) = v.replace("|", "\\|").replace("\n", " ")

    /** Прочитать сохранённый список (JSON или CSV — определяется по содержимому). */
    fun read(text: String): Snapshot {
        val t = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        return if (t.startsWith("{")) parseJson(t) else parseCsv(t)
    }
}
