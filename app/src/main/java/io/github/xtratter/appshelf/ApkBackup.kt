package io.github.xtratter.appshelf

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Резервные копии самих APK — для приложений, которые больше нигде не взять (установлены из APK-файлов).
 * Одно приложение — один файл последней версии: «пакет__versionCode.apk», а если оно из нескольких частей
 * (split APK) — «пакет__versionCode.apks» (zip с частями). Хранятся на WebDAV в общей папке «apk/»
 * или в выбранной папке на телефоне. Файл той же версии повторно не загружается, более старые удаляются.
 */
object ApkBackup {
    private const val DIR = "apk"
    private val NAME = Regex("^(.+)__(\\d+)\\.(apk|apks)$")

    enum class Dest { WEBDAV, FOLDER }
    enum class Scope { APK_ONLY, ALL }

    class Result(val saved: Int, val skipped: Int, val failed: Int, val error: String?)

    fun fileName(pkg: String, versionCode: Long, split: Boolean) = "${pkg}__$versionCode." + if (split) "apks" else "apk"

    /** «пакет__123.apk» → пакет и versionCode. */
    fun parse(name: String): Pair<String, Long>? = NAME.matchEntire(name)?.let { it.groupValues[1] to it.groupValues[2].toLong() }

    fun dest(p: Prefs): Dest? = when {
        p.apkDest == Dest.FOLDER.name && p.apkFolderUri.isNotEmpty() -> Dest.FOLDER
        p.apkDest == Dest.WEBDAV.name && p.davUrl.isNotEmpty() -> Dest.WEBDAV
        else -> null
    }

    /** Что копировать: приложения из APK-файлов (и неизвестного источника) или все, кроме системных. */
    fun targets(ctx: Context, scope: Scope): List<AppInfo> = Apps.load(ctx).filter {
        !it.system && it.pkg != ctx.packageName &&
            (scope == Scope.ALL || it.source == Source.APK || it.source == Source.UNKNOWN)
    }.let { ListFile.sorted(it) }

    // ---------- где лежат копии ----------

    /** Имена файлов копий в месте хранения. */
    private fun list(ctx: Context, p: Prefs, d: Dest): List<String> = when (d) {
        Dest.WEBDAV -> try {
            Sync.dav(p).list(DIR).filter { !it.dir }.map { it.name }
        } catch (e: WebDav.HttpError) {
            if (e.code == 404) emptyList() else throw e
        }
        Dest.FOLDER -> folderChildren(ctx, p).map { it.second }
    }

    /** Файлы в выбранной папке: (id документа, имя). */
    private fun folderChildren(ctx: Context, p: Prefs): List<Pair<String, String>> {
        val tree = Uri.parse(p.apkFolderUri)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val out = ArrayList<Pair<String, String>>()
        ctx.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) out += c.getString(0) to c.getString(1)
        }
        return out
    }

    /** Какие копии есть: пакет → имя файла (самая новая версия). */
    fun index(ctx: Context): Map<String, String> {
        val p = Prefs(ctx)
        val d = dest(p) ?: return emptyMap()
        val best = HashMap<String, Pair<Long, String>>()
        for (n in list(ctx, p, d)) {
            val (pkg, vc) = parse(n) ?: continue
            if ((best[pkg]?.first ?: -1) < vc) best[pkg] = vc to n
        }
        return best.mapValues { it.value.second }
    }

    // ---------- сохранить ----------

    /**
     * Сделать копии [apps]: пропустить уже сохранённые версии, остальные упаковать и загрузить, старые — удалить.
     * [progress] — (номер приложения, всего, название). Только в фоне.
     */
    fun run(ctx: Context, apps: List<AppInfo>, stop: AtomicBoolean = AtomicBoolean(false),
            progress: (Int, Int, String) -> Unit = { _, _, _ -> }): Result {
        val p = Prefs(ctx)
        val d = dest(p) ?: return Result(0, 0, 0, ctx.getString(R.string.bk_no_dest))
        val existing = try { list(ctx, p, d).toMutableSet() } catch (e: Exception) { return Result(0, 0, 0, Sync.error(ctx, e)) }
        var saved = 0; var skipped = 0; var failed = 0; var lastError: String? = null
        val cache = File(ctx.cacheDir, "apkbackup").apply { mkdirs() }
        for ((i, a) in apps.withIndex()) {
            if (stop.get()) break
            progress(i + 1, apps.size, a.label)
            val ai = try { ctx.packageManager.getApplicationInfo(a.pkg, 0) } catch (e: Exception) { failed++; continue }
            val splits: List<String> = ai.splitSourceDirs?.filterNotNull().orEmpty()
            val name = fileName(a.pkg, a.versionCode, splits.isNotEmpty())
            if (name in existing) { skipped++; continue }
            var packed: File? = null
            try {
                val file = if (splits.isEmpty()) File(ai.sourceDir) else pack(File(cache, "pack.apks"), ai.sourceDir, splits).also { packed = it }
                upload(ctx, p, d, name, file)
                // более старые версии этого приложения больше не нужны
                for (old in existing.filter { parse(it)?.first == a.pkg }) delete(ctx, p, d, old)
                existing.removeAll { parse(it)?.first == a.pkg }
                existing += name
                saved++
            } catch (e: Exception) {
                failed++; lastError = Sync.error(ctx, e)
            } finally {
                packed?.delete()
            }
        }
        return Result(saved, skipped, failed, lastError)
    }

    /** Части split APK — в один zip: base.apk и split_*.apk. */
    private fun pack(out: File, base: String, splits: List<String>): File {
        ZipOutputStream(out.outputStream().buffered(256 * 1024)).use { z ->
            z.setLevel(1)   // APK уже сжаты — быстрее, чем сильнее
            for (path in listOf(base) + splits) {
                val f = File(path)
                z.putNextEntry(ZipEntry(if (path == base) "base.apk" else f.name))
                f.inputStream().use { it.copyTo(z, 256 * 1024) }
                z.closeEntry()
            }
        }
        return out
    }

    private fun upload(ctx: Context, p: Prefs, d: Dest, name: String, file: File) {
        when (d) {
            Dest.WEBDAV -> Sync.dav(p).putFile("$DIR/$name", file, "application/vnd.android.package-archive")
            Dest.FOLDER -> {
                val tree = Uri.parse(p.apkFolderUri)
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val doc = DocumentsContract.createDocument(ctx.contentResolver, parent, "application/octet-stream", name)
                    ?: throw java.io.IOException("cannot create $name")
                ctx.contentResolver.openOutputStream(doc)!!.use { out -> file.inputStream().use { it.copyTo(out, 256 * 1024) } }
            }
        }
    }

    private fun delete(ctx: Context, p: Prefs, d: Dest, name: String) {
        try {
            when (d) {
                Dest.WEBDAV -> Sync.dav(p).delete("$DIR/$name")
                Dest.FOLDER -> folderChildren(ctx, p).firstOrNull { it.second == name }?.let { (id, _) ->
                    DocumentsContract.deleteDocument(ctx.contentResolver,
                        DocumentsContract.buildDocumentUriUsingTree(Uri.parse(p.apkFolderUri), id))
                }
            }
        } catch (e: Exception) {
            // не удалилась старая копия — не страшно
        }
    }

    // ---------- восстановить ----------

    /** Скачать копию [name] в [dest] (с прогрессом в байтах). Только в фоне. */
    fun fetch(ctx: Context, name: String, dest: File, progress: (Long) -> Unit) {
        val p = Prefs(ctx)
        when (dest(p) ?: throw IllegalStateException(ctx.getString(R.string.bk_no_dest))) {
            Dest.WEBDAV -> Sync.dav(p).download("$DIR/$name", dest, progress)
            Dest.FOLDER -> {
                val (id, _) = folderChildren(ctx, p).firstOrNull { it.second == name } ?: throw java.io.FileNotFoundException(name)
                val uri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(p.apkFolderUri), id)
                ctx.contentResolver.openInputStream(uri)!!.use { inp ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024); var done = 0L
                        while (true) { val n = inp.read(buf); if (n < 0) break; out.write(buf, 0, n); done += n; progress(done) }
                    }
                }
            }
        }
    }

    /** Есть ли у телефона разрешение ставить приложения — нужно для установки из копии. */
    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()
}
