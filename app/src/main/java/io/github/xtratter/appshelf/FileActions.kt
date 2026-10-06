package io.github.xtratter.appshelf

import io.github.xtratter.uikit.Haptics
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast

private fun MainActivity.sourceName(s: Source) = getString(s.title)

/** Сохранить в файл: выбор формата, затем системный выбор места. */
internal fun MainActivity.saveToFile() {
    val formats = Format.entries
    if (installed == null) return
    val names = arrayOf(getString(R.string.fmt_json), getString(R.string.fmt_md), getString(R.string.fmt_csv))
    AlertDialog.Builder(this)
        .setTitle(R.string.save_file)
        .setItems(names) { _, i ->
            pendingFormat = formats[i]
            val day = ListFile.date(System.currentTimeMillis())
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = pendingFormat.mime
                putExtra(Intent.EXTRA_TITLE, "AppShelf-$day.${pendingFormat.ext}")
            }, MainActivity.REQ_SAVE)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show().also { Ui.glassDialog(it) }
}

/** Свои ссылки в формате каталога (sources.json) — чтобы перенести их в репозиторий каталога. */


/** Сохранить настройки в файл на телефоне (системный выбор места). */
internal fun MainActivity.saveSettingsFile(passphrase: String?) {
    settingsPassphrase = passphrase
    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/json"
        putExtra(Intent.EXTRA_TITLE, "AppShelf-settings-" + Apps.shortName(this@saveSettingsFile) + ".json")
    }, MainActivity.REQ_SETTINGS_SAVE)
}

internal fun MainActivity.openSettingsFile() {
    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
    }, MainActivity.REQ_SETTINGS_OPEN)
}

/** Применить настройки из текста: всё сразу на экране, расписание WebDAV — заново. */
internal fun MainActivity.importSettings(text: String) {
    // пароль в файле зашифрован — сначала спросим фразу, настройки при неверной фразе не меняются
    if (SettingsIO.needsPassphrase(text)) PassphraseDialog.ask(this) { applySettings(text, it) } else applySettings(text, null)
}

private fun MainActivity.applySettings(text: String, passphrase: String?) {
    val withPass = try { SettingsIO.apply(this, text, passphrase) } catch (e: Passphrase.Wrong) {
        Haptics.play(Haptics.Kind.ERROR)
        Toast.makeText(this, R.string.st_wrong_phrase, Toast.LENGTH_LONG).show()
        importSettings(text)   // спросить ещё раз
        return
    } catch (e: Exception) {
        Haptics.play(Haptics.Kind.ERROR)
        Toast.makeText(this, R.string.st_bad_file, Toast.LENGTH_LONG).show(); return
    }
    Kit.init(this)
    Sync.schedule(this)
    applyThemeInPlace {}
    Haptics.play(Haptics.Kind.SUCCESS)
    Toast.makeText(this, if (withPass) R.string.st_restored_pass else R.string.st_restored, Toast.LENGTH_LONG).show()
}

internal fun MainActivity.exportLinks() {
    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = Format.JSON.mime
        putExtra(Intent.EXTRA_TITLE, "sources.json")
    }, MainActivity.REQ_LINKS)
}

internal fun MainActivity.share() {
    val s = snapshot() ?: return
    val text = ListFile.write(s, Format.MARKDOWN, ::sourceName)
    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "AppShelf — " + ListFile.date(s.created))
        putExtra(Intent.EXTRA_TEXT, text)
    }, getString(R.string.share_list)))
}

/** Записать список в файл [uri] в фоне; [done] получает ошибку или null. */
private fun MainActivity.writeTo(uri: Uri, f: Format, done: (Exception?) -> Unit) {
    val s = snapshot() ?: return done(IllegalStateException("not loaded"))
    io.execute {
        val err = try {
            contentResolver.openOutputStream(uri, "wt")!!.use { it.write(ListFile.write(s, f, ::sourceName).toByteArray()) }
            null
        } catch (e: Exception) {
            e
        }
        main.post {
            if (err == null) {
                prefs.lastSaved = s.created
                prefs.lastSavedName = fileName(uri)
                if (!isDestroyed) renderSummary()
            }
            done(err)
        }
    }
}

internal fun MainActivity.fileName(uri: Uri): String = try {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: uri.lastPathSegment.orEmpty()
} catch (e: Exception) {
    uri.lastPathSegment.orEmpty()
}

/** Автосохранение: при каждом запуске список заново записывается в выбранный файл. */
internal fun MainActivity.autosave() {
    val u = prefs.autosaveUri.takeIf { it.isNotEmpty() } ?: return
    writeTo(Uri.parse(u), Format.JSON) { err ->
        if (err != null) {
            prefs.autosaveUri = ""
            Toast.makeText(this, R.string.autosave_failed, Toast.LENGTH_LONG).show()
            if (!isDestroyed) renderSummary()
        }
    }
}

internal fun MainActivity.autosaveDialog() {
    val on = prefs.autosaveUri.isNotEmpty()
    val b = AlertDialog.Builder(this).setTitle(R.string.autosave)
    if (on) {
        b.setMessage(getString(R.string.autosave_is_on, fileName(Uri.parse(prefs.autosaveUri))))
            .setPositiveButton(R.string.autosave_disable) { _, _ ->
                try {
                    contentResolver.releasePersistableUriPermission(Uri.parse(prefs.autosaveUri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } catch (e: Exception) {}
                prefs.autosaveUri = ""
                renderSummary()
            }
            .setNeutralButton(R.string.autosave_other) { _, _ -> pickAutosave() }
    } else {
        b.setMessage(R.string.autosave_explain).setPositiveButton(R.string.autosave_choose) { _, _ -> pickAutosave() }
    }
    b.setNegativeButton(android.R.string.cancel, null).show().also { Ui.glassDialog(it) }
}

private fun MainActivity.pickAutosave() {
    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = Format.JSON.mime
        putExtra(Intent.EXTRA_TITLE, "AppShelf.json")
    }, MainActivity.REQ_AUTOSAVE)
}

internal fun MainActivity.openList() {
    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/csv", "text/comma-separated-values",
            "text/plain", "application/octet-stream"))
    }, MainActivity.REQ_OPEN)
}

/** Результат системного выбора файла ([MainActivity.onActivityResult]); [uri] уже проверен. */
internal fun MainActivity.handleFileResult(requestCode: Int, uri: Uri) {
    when (requestCode) {
        MainActivity.REQ_SAVE -> writeTo(uri, pendingFormat) { err ->
            Haptics.play(if (err == null) Haptics.Kind.SUCCESS else Haptics.Kind.ERROR)
            Toast.makeText(this, if (err == null) getString(R.string.saved_to, fileName(uri))
            else getString(R.string.save_failed, err.message), Toast.LENGTH_LONG).show()
        }
        MainActivity.REQ_AUTOSAVE -> {
            try {
                contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (e: SecurityException) {
                // провайдер не даёт постоянный доступ — запишем хотя бы сейчас
            }
            prefs.autosaveUri = uri.toString()
            writeTo(uri, Format.JSON) { err ->
                Toast.makeText(this, if (err == null) getString(R.string.autosave_enabled, fileName(uri))
                else getString(R.string.save_failed, err.message), Toast.LENGTH_LONG).show()
                if (err != null) prefs.autosaveUri = ""
                renderSummary()
            }
        }
        MainActivity.REQ_SETTINGS_SAVE -> io.execute {
            val err = try {
                contentResolver.openOutputStream(uri, "wt")!!.use { it.write(SettingsIO.write(this, settingsPassphrase).toByteArray()) }
                null
            } catch (e: Exception) { e }
            main.post {
                settingsPassphrase = null
                if (err == null) prefs.settingsSaved = System.currentTimeMillis()
                Haptics.play(if (err == null) Haptics.Kind.SUCCESS else Haptics.Kind.ERROR)
                Toast.makeText(this, if (err == null) getString(R.string.saved_to, fileName(uri))
                else getString(R.string.save_failed, err.message), Toast.LENGTH_LONG).show()
            }
        }
        MainActivity.REQ_SETTINGS_OPEN -> io.execute {
            val text = try { contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } } catch (e: Exception) { "" }
            main.post { if (!isDestroyed) importSettings(text) }
        }
        MainActivity.REQ_APK_FOLDER -> {
            try {
                contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (e: SecurityException) {}
            prefs.apkFolderUri = uri.toString()
            prefs.apkDest = ApkBackup.Dest.FOLDER.name
            ApkBackupDialog.show(this)
        }
        MainActivity.REQ_LINKS -> io.execute {
            val err = try {
                contentResolver.openOutputStream(uri, "wt")!!.use { it.write(LinkStore.catalogExport(this).toByteArray()) }
                null
            } catch (e: Exception) {
                e
            }
            main.post {
                Toast.makeText(this, if (err == null) getString(R.string.saved_to, fileName(uri))
                else getString(R.string.save_failed, err.message), Toast.LENGTH_LONG).show()
            }
        }
        MainActivity.REQ_OPEN -> io.execute {
            val result = try {
                val text = contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                ListFile.read(text)
            } catch (e: Exception) {
                null
            }
            main.post {
                if (result == null || result.apps.isEmpty()) {
                    Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show()
                    return@post
                }
                showSnapshot(result)
            }
        }
    }
}
