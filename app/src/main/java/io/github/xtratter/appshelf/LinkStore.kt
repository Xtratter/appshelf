package io.github.xtratter.appshelf

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Где лежат ссылки на источники:
 * личные — файл links.json на телефоне, он же в основной папке WebDAV (общий для всех телефонов) и копией в каждом списке;
 * каталог — sources.json из GitHub (адрес меняется в настройках), копия хранится на телефоне.
 * Личные ссылки всегда главнее каталога.
 */
object LinkStore {
    const val DEFAULT_CATALOG = "https://raw.githubusercontent.com/Xtratter/appshelf-sources/main/sources.json"
    private const val DAV_FILE = "links.json"
    private const val DAY = 24 * 60 * 60 * 1000L

    private var mine: Map<String, LinkEntry>? = null
    private var catalog: Map<String, List<Link>>? = null

    private fun mineFile(ctx: Context) = File(ctx.filesDir, "links.json")
    private fun catalogFile(ctx: Context) = File(ctx.filesDir, "catalog.json")

    @Synchronized
    fun mine(ctx: Context): Map<String, LinkEntry> = mine ?: runCatching {
        Links.parseMine(mineFile(ctx).takeIf { it.exists() }?.readText().orEmpty())
    }.getOrDefault(emptyMap()).also { mine = it }

    @Synchronized
    fun catalog(ctx: Context): Map<String, List<Link>> = catalog ?: runCatching {
        Links.parseCatalog(catalogFile(ctx).readText())
    }.getOrDefault(emptyMap()).also { catalog = it }

    /** Свои ссылки приложения (без удалённых). */
    fun get(ctx: Context, pkg: String): List<Link> = mine(ctx)[pkg]?.links.orEmpty()

    /** Все ссылки приложения: свои, затем из каталога; второе — «из каталога». */
    fun forApp(ctx: Context, pkg: String) = Links.combined(mine(ctx)[pkg]?.links, catalog(ctx)[pkg])

    /** Заменить свои ссылки приложения; затем — отправить на WebDAV в фоне. */
    fun set(ctx: Context, pkg: String, links: List<Link>) {
        synchronized(this) { save(ctx, mine(ctx) + (pkg to LinkEntry(System.currentTimeMillis(), links, note(ctx, pkg)))) }
        Prefs(ctx).linksDirty = true
        syncSoon(ctx)
    }

    /** Заметка к приложению (пустая — нет). */
    fun note(ctx: Context, pkg: String): String = mine(ctx)[pkg]?.note.orEmpty()

    /** Сохранить заметку; как и ссылки, уходит на WebDAV и в сохранённые списки. */
    fun setNote(ctx: Context, pkg: String, note: String) {
        synchronized(this) { save(ctx, mine(ctx) + (pkg to LinkEntry(System.currentTimeMillis(), get(ctx, pkg), note.trim()))) }
        Prefs(ctx).linksDirty = true
        syncSoon(ctx)
    }

    /** Ссылки из открытого списка или с сервера — добавить к своим (более новые побеждают). */
    fun import(ctx: Context, other: Map<String, LinkEntry>) {
        if (other.isEmpty()) return
        synchronized(this) {
            val merged = Links.merge(mine(ctx), other)
            if (merged != mine(ctx)) save(ctx, merged)
        }
    }

    private fun save(ctx: Context, m: Map<String, LinkEntry>) {
        mine = m
        val f = mineFile(ctx)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(Links.writeMine(m))
        tmp.renameTo(f)
    }

    // ---------- WebDAV ----------

    /** Слить свои ссылки с links.json на сервере и записать результат в обе стороны. Только в фоне. */
    fun syncDav(ctx: Context) {
        val p = Prefs(ctx)
        if (p.davUrl.isBlank()) return
        val dav = Sync.dav(p)
        val remoteText = try {
            dav.get(DAV_FILE).toString(Charsets.UTF_8)
        } catch (e: WebDav.HttpError) {
            if (e.code == 404) "" else throw e
        }
        val remote = runCatching { Links.parseMine(remoteText) }.getOrDefault(emptyMap())
        import(ctx, remote)
        val merged = mine(ctx)
        if (merged != remote) dav.put(DAV_FILE, Links.writeMine(merged).toByteArray(Charsets.UTF_8), Format.JSON.mime)
        p.linksDirty = false
        p.linksSynced = System.currentTimeMillis()
    }

    private fun syncSoon(ctx: Context) {
        val app = ctx.applicationContext
        if (Prefs(app).davUrl.isBlank()) return
        Thread { runCatching { syncDav(app) } }.start()   // не вышло — попробуем при следующем запуске
    }

    // ---------- каталог ----------

    /** Скачать каталог, если он старше [maxAge] (с проверкой ETag — без изменений файл не качается заново). Только в фоне. */
    fun refreshCatalog(ctx: Context, maxAge: Long = DAY): Boolean {
        val p = Prefs(ctx)
        val url = p.catalogUrl.trim()
        if (url.isEmpty()) {
            synchronized(this) { catalog = emptyMap(); catalogFile(ctx).delete() }
            return false
        }
        if (url == p.catalogFetchedUrl && System.currentTimeMillis() - p.catalogFetched < maxAge) return false
        val c = URL(WebDav.encodeUrl(url)).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        if (url == p.catalogFetchedUrl && p.catalogEtag.isNotEmpty() && catalogFile(ctx).exists())
            c.setRequestProperty("If-None-Match", p.catalogEtag)
        try {
            when (c.responseCode) {
                304 -> {}
                in 200..299 -> {
                    val text = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    val parsed = Links.parseCatalog(text)   // не наш формат — исключение, старую копию не трогаем
                    synchronized(this) {
                        catalogFile(ctx).writeText(text)
                        catalog = parsed
                    }
                    p.catalogEtag = c.getHeaderField("ETag").orEmpty()
                }
                else -> throw WebDav.HttpError(c.responseCode, c.responseMessage.orEmpty())
            }
        } finally {
            c.disconnect()
        }
        p.catalogFetched = System.currentTimeMillis()
        p.catalogFetchedUrl = url
        return true
    }

    /** Выгрузить свои ссылки в формате каталога (sources.json). */
    fun catalogExport(ctx: Context): String =
        Links.writeCatalog(mine(ctx).mapValues { it.value.links }.filterValues { it.isNotEmpty() })

    // ---------- открыть ----------

    private val OBTAINIUM = listOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid")

    private fun obtainium(ctx: Context) = OBTAINIUM.firstOrNull { Apps.isInstalled(ctx, it) }

    /**
     * Открыть ссылку, чтобы установить приложение [pkg]: прямую ссылку на .apk — скачать и установить прямо здесь;
     * если есть Obtainium и он умеет этот сайт — предложить и его.
     */
    fun open(ctx: Context, link: Link, pkg: String? = null, label: String = "") {
        val kind = Links.kind(link.url)
        if (kind == LinkKind.APK && ctx is MainActivity) return ApkInstaller.start(ctx, link.url, pkg, label.ifEmpty { Links.short(link.url) })
        val obt = if (kind.obtainium) obtainium(ctx) else null
        // GitHub: скачать и поставить APK из последнего релиза прямо здесь (ссылка в каталоге не устаревает с версиями)
        val github = ctx is MainActivity && Updates.repoOf(link.url) != null
        if (github && obt == null) return githubApk(ctx as MainActivity, link, pkg, label)
        if (github) {
            AlertDialog.Builder(ctx)
                .setTitle(link.label.ifBlank { ctx.getString(kind.title) } + " · " + Links.short(link.url))
                .setItems(arrayOf(ctx.getString(R.string.lk_github_apk), ctx.getString(R.string.lk_open, ctx.getString(kind.title)),
                    ctx.getString(R.string.lk_obtainium))) { _, i ->
                    when (i) {
                        0 -> githubApk(ctx as MainActivity, link, pkg, label)
                        1 -> view(ctx, Links.installUrl(link.url))
                        else -> view(ctx, "obtainium://add/" + link.url.trim(), obt)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show().also { Ui.glassDialog(it) }
            return
        }
        if (obt == null) return view(ctx, Links.installUrl(link.url))
        AlertDialog.Builder(ctx)
            .setTitle(link.label.ifBlank { ctx.getString(kind.title) } + " · " + Links.short(link.url))
            .setItems(arrayOf(ctx.getString(R.string.lk_open, ctx.getString(kind.title)), ctx.getString(R.string.lk_obtainium))) { _, i ->
                if (i == 0) view(ctx, Links.installUrl(link.url))
                else view(ctx, "obtainium://add/" + link.url.trim(), obt)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show().also { Ui.glassDialog(it) }
    }

    /** Найти APK в последнем релизе GitHub и поставить его; нет APK или сети — открыть страницу релизов. */
    private fun githubApk(a: MainActivity, link: Link, pkg: String?, label: String) {
        Toast.makeText(a, R.string.lk_github_looking, Toast.LENGTH_SHORT).show()
        val app = a.applicationContext
        Thread {
            val apk = runCatching { Updates.latestApk(app, link.url) }.getOrNull()
            a.runOnUiThread {
                if (a.isDestroyed) return@runOnUiThread
                if (apk != null) ApkInstaller.start(a, apk, pkg, label.ifEmpty { Links.short(link.url) })
                else {
                    Toast.makeText(a, R.string.lk_github_no_apk, Toast.LENGTH_LONG).show()
                    view(a, Links.installUrl(link.url))
                }
            }
        }.start()
    }

    private fun view(ctx: Context, url: String, pkg: String? = null) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { pkg?.let { setPackage(it) } }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, R.string.no_store, Toast.LENGTH_SHORT).show()
        }
    }
}
