package io.github.xtratter.appshelf

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Обновления с GitHub: для установленных приложений со ссылкой на GitHub (своей или из каталога) — последний
 * релиз, не чаще раза в 6 часов (с ETag: неизменившийся ответ не расходует лимит GitHub, 60 запросов в час).
 * Новее установленного — в строке «↑ версия», чип «Обновления», в карточке «Обновить до …».
 */
object Updates {
    /** Последний релиз: тег, APK для этого телефона (или null — в релизе нет подходящего APK), страница. */
    data class Release(val tag: String, val apkUrl: String?, val page: String)

    private const val EVERY = 6 * 60 * 60 * 1000L
    private var cache: MutableMap<String, JSONObject>? = null   // репозиторий → {tag, apk, page, etag, checked}
    private fun file(ctx: Context) = File(ctx.filesDir, "updates.json")

    // ---------- без Android: проверяется unit-тестами ----------

    /** Версия из тега или versionName: первая группа чисел через точку («v1.10.2-beta» → [1, 10, 2]). */
    fun numbers(s: String): List<Int> =
        Regex("\\d+(?:\\.\\d+)*").find(s)?.value?.split('.')?.mapNotNull { it.toIntOrNull() }.orEmpty()

    /** Новее ли [tag] установленной [installed]; null — не понять (нет чисел). */
    fun newer(tag: String, installed: String): Boolean? {
        val a = numbers(tag)
        val b = numbers(installed)
        if (a.isEmpty() || b.isEmpty()) return null
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Какой APK из релиза ставить на процессор [abi]: под него, иначе универсальный, иначе без пометки о процессоре. */
    fun pickApk(names: List<String>, abi: String): String? {
        val tokens = mapOf(
            "arm64-v8a" to listOf("arm64", "aarch64", "v8a"),
            "armeabi-v7a" to listOf("armeabi", "armv7", "v7a", "arm32"),
            "x86_64" to listOf("x86_64", "x64", "amd64"),
            "x86" to listOf("x86", "i686"),
        )
        val mine = tokens[abi].orEmpty()
        val others = tokens.filterKeys { it != abi }.values.flatten().filter { t -> mine.none { it.contains(t) || t.contains(it) } }
        fun score(n: String): Int {
            val l = n.lowercase()
            return when {
                mine.any { it in l } -> 3
                "universal" in l || "all" in l.split('-', '_', '.') -> 2
                others.any { it in l } -> -10
                else -> 1
            }
        }
        return names.filter { it.endsWith(".apk", true) }.maxByOrNull { score(it) }?.takeIf { score(it) > 0 }
    }

    /** «owner/repo» из ссылки на GitHub; null — не репозиторий GitHub. */
    fun repoOf(url: String): String? {
        if (Links.kind(url) != LinkKind.GITHUB) return null
        val parts = url.trim().replace(Regex("^[a-zA-Z]+://[^/]+/"), "").substringBefore('?').substringBefore('#')
            .split('/').filter { it.isNotEmpty() }
        return if (parts.size >= 2) parts[0] + "/" + parts[1].removeSuffix(".git") else null
    }

    // ---------- с Android ----------

    @Synchronized
    private fun cache(ctx: Context): MutableMap<String, JSONObject> = cache ?: runCatching {
        val o = JSONObject(file(ctx).readText())
        o.keys().asSequence().associateWith { o.getJSONObject(it) }.toMutableMap()
    }.getOrDefault(HashMap()).also { cache = it }

    @Synchronized
    private fun save(ctx: Context) {
        val o = JSONObject()
        for ((k, v) in cache(ctx)) o.put(k, v)
        file(ctx).writeText(o.toString())
    }

    private fun repoFor(ctx: Context, pkg: String): String? =
        LinkStore.forApp(ctx, pkg).firstNotNullOfOrNull { repoOf(it.first.url) }

    /** Обновление для приложения [a], если релиз на GitHub новее; null — нет (или не проверено). */
    fun available(ctx: Context, a: AppInfo): Release? {
        val repo = repoFor(ctx, a.pkg) ?: return null
        val o = cache(ctx)[repo] ?: return null
        val tag = o.optString("tag")
        if (newer(tag, a.versionName) != true) return null
        return Release(tag, o.optString("apk").ifEmpty { null }, o.optString("page"))
    }

    /** Проверить релизы для [apps] (не чаще раза в 6 часов на репозиторий). true — что-то поменялось. Только в фоне. */
    fun check(ctx: Context, apps: List<AppInfo>, force: Boolean = false): Boolean {
        val repos = apps.mapNotNull { repoFor(ctx, it.pkg) }.distinct()
        var changed = false
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        for (repo in repos) {
            val old = cache(ctx)[repo]
            if (!force && old != null && System.currentTimeMillis() - old.optLong("checked") < EVERY) continue
            try {
                val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
                c.connectTimeout = 10_000; c.readTimeout = 20_000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.setRequestProperty("User-Agent", "AppShelf")
                old?.optString("etag")?.takeIf { it.isNotEmpty() }?.let { c.setRequestProperty("If-None-Match", it) }
                try {
                    when (c.responseCode) {
                        304 -> old?.put("checked", System.currentTimeMillis())
                        200 -> {
                            val r = JSONObject(c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
                            val assets = r.optJSONArray("assets") ?: JSONArray()
                            val byName = (0 until assets.length()).associate {
                                assets.getJSONObject(it).optString("name") to assets.getJSONObject(it).optString("browser_download_url")
                            }
                            val apk = pickApk(byName.keys.toList(), abi)?.let { byName[it] }.orEmpty()
                            val entry = JSONObject().apply {
                                put("tag", r.optString("tag_name")); put("apk", apk); put("page", r.optString("html_url"))
                                put("etag", c.getHeaderField("ETag").orEmpty()); put("checked", System.currentTimeMillis())
                            }
                            if (old?.optString("tag") != entry.optString("tag") || old.optString("apk") != apk) changed = true
                            synchronized(this) { cache(ctx)[repo] = entry }
                        }
                        404 -> synchronized(this) { cache(ctx)[repo] = JSONObject().put("checked", System.currentTimeMillis()) }   // релизов нет
                        else -> {}   // лимит или сбой — попробуем в следующий раз
                    }
                } finally {
                    c.disconnect()
                }
            } catch (e: Exception) {
                // нет сети — не страшно
            }
        }
        save(ctx)
        return changed
    }
}
