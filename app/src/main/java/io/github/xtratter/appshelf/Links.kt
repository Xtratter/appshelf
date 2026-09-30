package io.github.xtratter.appshelf

import org.json.JSONArray
import org.json.JSONObject

/** Ссылка, откуда можно скачать приложение: GitHub, Telegram, сайт… [label] — подпись (необязательно). */
data class Link(val url: String, val label: String = "")

/** Что за ссылка — по адресу. [obtainium] — Obtainium умеет ставить и обновлять приложения оттуда. */
enum class LinkKind(val title: Int, val obtainium: Boolean = false) {
    GITHUB(R.string.lk_github, true),
    GITLAB(R.string.lk_gitlab, true),
    CODEBERG(R.string.lk_codeberg, true),
    FDROID(R.string.lk_fdroid),
    TELEGRAM(R.string.lk_telegram),
    FOURPDA(R.string.lk_4pda),
    APK(R.string.lk_apk),
    SITE(R.string.lk_site),
}

/**
 * Личные ссылки и заметка одного приложения и когда их меняли. При слиянии (телефон, WebDAV, открытый список)
 * побеждают более новые; пустой список — ссылки удалены (чтобы удаление тоже доходило до других телефонов).
 */
data class LinkEntry(val updated: Long, val links: List<Link>, val note: String = "")

/** Ссылки: тип, короткая подпись, слияние, файлы. Без Android-зависимостей — проверяется unit-тестами. */
object Links {
    private const val MINE = "AppShelf-links"
    private const val CATALOG = "AppShelf-sources"

    private fun host(url: String) = Regex("^[a-zA-Z]+://([^/?#:]+)").find(url.trim())?.groupValues?.get(1)?.lowercase()?.removePrefix("www.").orEmpty()

    private fun path(url: String) = url.trim().replace(Regex("^[a-zA-Z]+://[^/?#]+"), "").substringBefore('?').substringBefore('#').trim('/')

    fun kind(url: String): LinkKind {
        val h = host(url)
        return when {
            path(url).endsWith(".apk", true) -> LinkKind.APK
            h == "github.com" -> LinkKind.GITHUB
            h == "gitlab.com" -> LinkKind.GITLAB
            h == "codeberg.org" -> LinkKind.CODEBERG
            h == "f-droid.org" || h == "apt.izzysoft.de" -> LinkKind.FDROID
            h == "t.me" || h == "telegram.me" -> LinkKind.TELEGRAM
            h == "4pda.to" || h == "4pda.ru" -> LinkKind.FOURPDA
            else -> LinkKind.SITE
        }
    }

    /** Коротко, что за ссылка: «Xtratter/droidtop», «@channel», «example.com». */
    fun short(url: String): String {
        val h = host(url)
        val parts = path(url).split('/').filter { it.isNotEmpty() }
        return when (kind(url)) {
            LinkKind.GITHUB, LinkKind.GITLAB, LinkKind.CODEBERG -> parts.take(2).joinToString("/").ifEmpty { h }
            LinkKind.TELEGRAM -> parts.firstOrNull()?.let { if (it == "s" || it == "c") parts.getOrNull(1) else it }?.let { "@$it" } ?: h
            LinkKind.APK -> parts.lastOrNull() ?: h
            else -> h
        }
    }

    /**
     * Куда вести, чтобы установить: для корня репозитория GitHub / Codeberg — сразу последний релиз,
     * для GitLab — список релизов; остальные ссылки — как есть.
     */
    fun installUrl(url: String): String {
        val parts = path(url).split('/').filter { it.isNotEmpty() }
        if (parts.size != 2) return url.trim()
        val base = url.trim().substringBefore('?').substringBefore('#').trimEnd('/')
        return when (kind(url)) {
            LinkKind.GITHUB -> "$base/releases/latest"
            LinkKind.CODEBERG, LinkKind.GITLAB -> "$base/releases"
            else -> url.trim()
        }
    }

    /** Похожа ли строка на ссылку, которую можно сохранить. */
    fun valid(url: String) = Regex("^https?://[^\\s/?#]+\\.[^\\s/?#]+.*", RegexOption.IGNORE_CASE).matches(url.trim())

    /** Для сравнения: одна и та же ссылка без «www», «/» в конце и регистра адреса. */
    fun same(a: String, b: String) = key(a) == key(b)

    private fun key(url: String) = url.trim().lowercase().replace(Regex("^https?://(www\\.)?"), "").trimEnd('/')

    /** Слияние личных ссылок: по каждому приложению побеждает более новая запись (при равенстве — [a]). */
    fun merge(a: Map<String, LinkEntry>, b: Map<String, LinkEntry>): Map<String, LinkEntry> {
        val out = HashMap(a)
        for ((pkg, e) in b) {
            val mine = out[pkg]
            if (mine == null || e.updated > mine.updated) out[pkg] = e
        }
        return out
    }

    /** Что показать для приложения: сначала свои ссылки, затем из каталога (без повторов); true — из каталога. */
    fun combined(mine: List<Link>?, catalog: List<Link>?): List<Pair<Link, Boolean>> {
        val out = ArrayList<Pair<Link, Boolean>>()
        mine?.forEach { l -> if (out.none { same(it.first.url, l.url) }) out += l to false }
        catalog?.forEach { l -> if (out.none { same(it.first.url, l.url) }) out += l to true }
        return out
    }

    // ---------- файлы ----------

    fun linksJson(list: List<Link>) = JSONArray().apply {
        for (l in list) put(JSONObject().apply { put("url", l.url); if (l.label.isNotBlank()) put("label", l.label) })
    }

    fun parseLinks(arr: JSONArray?): List<Link> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i)
            val url = o?.optString("url") ?: arr.optString(i)
            if (url.isNullOrBlank()) null else Link(url.trim(), o?.optString("label").orEmpty().trim())
        }
    }

    /** Личные ссылки (links.json на телефоне и в WebDAV): с датой изменения, удалённые — пустым списком. */
    fun writeMine(m: Map<String, LinkEntry>): String {
        val apps = JSONObject()
        for (pkg in m.keys.sorted()) {
            val e = m.getValue(pkg)
            apps.put(pkg, JSONObject().apply {
                put("updated", e.updated); put("links", linksJson(e.links))
                if (e.note.isNotBlank()) put("note", e.note)
            })
        }
        return JSONObject().apply { put("format", MINE); put("version", 1); put("apps", apps) }.toString(2) + "\n"
    }

    fun parseMine(text: String): Map<String, LinkEntry> {
        if (text.isBlank()) return emptyMap()
        val o = JSONObject(text)
        require(o.optString("format") == MINE) { "not AppShelf links" }
        val apps = o.optJSONObject("apps") ?: return emptyMap()
        return apps.keys().asSequence().associateWith { pkg ->
            val e = apps.getJSONObject(pkg)
            LinkEntry(e.optLong("updated"), parseLinks(e.optJSONArray("links")), e.optString("note"))
        }
    }

    /** Каталог (sources.json): только ссылки, без дат и удалённых. */
    fun writeCatalog(m: Map<String, List<Link>>): String {
        val apps = JSONObject()
        for (pkg in m.keys.sorted()) if (m.getValue(pkg).isNotEmpty()) apps.put(pkg, linksJson(m.getValue(pkg)))
        return JSONObject().apply { put("format", CATALOG); put("version", 1); put("apps", apps) }.toString(2) + "\n"
    }

    /** Каталог: у приложения — массив ссылок (или объект с полем «links», как в личном файле). */
    fun parseCatalog(text: String): Map<String, List<Link>> {
        val o = JSONObject(text)
        require(o.optString("format") == CATALOG || o.optString("format") == MINE) { "not an AppShelf catalog" }
        val apps = o.optJSONObject("apps") ?: return emptyMap()
        return apps.keys().asSequence().associateWith { pkg ->
            apps.optJSONArray(pkg)?.let { parseLinks(it) } ?: parseLinks(apps.optJSONObject(pkg)?.optJSONArray("links"))
        }.filterValues { it.isNotEmpty() }
    }
}
