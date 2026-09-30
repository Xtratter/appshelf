package io.github.xtratter.appshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    @Test
    fun kinds() {
        assertEquals(LinkKind.GITHUB, Links.kind("https://github.com/Xtratter/droidtop"))
        assertEquals(LinkKind.GITLAB, Links.kind("https://gitlab.com/a/b"))
        assertEquals(LinkKind.CODEBERG, Links.kind("https://codeberg.org/a/b/"))
        assertEquals(LinkKind.TELEGRAM, Links.kind("https://t.me/some_channel"))
        assertEquals(LinkKind.FOURPDA, Links.kind("https://4pda.to/forum/index.php?showtopic=1"))
        assertEquals(LinkKind.FDROID, Links.kind("https://f-droid.org/packages/x/"))
        assertEquals(LinkKind.APK, Links.kind("https://github.com/a/b/releases/download/v1/app-arm64.apk"))
        assertEquals(LinkKind.SITE, Links.kind("https://www.example.com/download"))
    }

    @Test
    fun shortAndInstall() {
        assertEquals("Xtratter/droidtop", Links.short("https://github.com/Xtratter/droidtop/releases"))
        assertEquals("@some_channel", Links.short("https://t.me/some_channel/123"))
        assertEquals("@chan", Links.short("https://t.me/s/chan"))
        assertEquals("example.com", Links.short("https://www.example.com/download"))
        assertEquals("https://github.com/a/b/releases/latest", Links.installUrl("https://github.com/a/b/"))
        assertEquals("https://codeberg.org/a/b/releases", Links.installUrl("https://codeberg.org/a/b"))
        // не корень репозитория — как есть
        assertEquals("https://github.com/a/b/releases/tag/v1", Links.installUrl("https://github.com/a/b/releases/tag/v1"))
        assertEquals("https://t.me/chan", Links.installUrl("https://t.me/chan"))
    }

    @Test
    fun validAndSame() {
        assertTrue(Links.valid("https://github.com/a/b"))
        assertTrue(Links.valid("http://site.ru"))
        assertFalse(Links.valid("github.com/a/b"))
        assertFalse(Links.valid("https://localhost"))
        assertTrue(Links.same("https://www.github.com/A/b/", "http://github.com/a/b"))
    }

    @Test
    fun mergeNewerWins() {
        val gh = Link("https://github.com/a/b")
        val tg = Link("https://t.me/c", "Канал")
        val phone = mapOf("x" to LinkEntry(100, listOf(gh)), "y" to LinkEntry(300, listOf(tg)))
        val server = mapOf("x" to LinkEntry(200, emptyList()), "y" to LinkEntry(250, listOf(gh)), "z" to LinkEntry(1, listOf(tg)))
        val m = Links.merge(phone, server)
        assertEquals(emptyList<Link>(), m["x"]!!.links)       // удаление на другом телефоне — новее
        assertEquals(listOf(tg), m["y"]!!.links)             // своё новее
        assertEquals(listOf(tg), m["z"]!!.links)             // новое приложение
    }

    @Test
    fun mineFirstThenCatalog() {
        val mine = listOf(Link("https://github.com/a/b", "Моё"))
        val catalog = listOf(Link("https://github.com/a/b/"), Link("https://t.me/c"))
        assertEquals(listOf(Link("https://github.com/a/b", "Моё") to false, Link("https://t.me/c") to true),
            Links.combined(mine, catalog))
    }

    @Test
    fun filesRoundTrip() {
        val m = mapOf("x" to LinkEntry(5, listOf(Link("https://t.me/c", "Канал"))), "gone" to LinkEntry(9, emptyList()))
        assertEquals(m, Links.parseMine(Links.writeMine(m)))
        val cat = Links.parseCatalog(Links.writeCatalog(m.mapValues { it.value.links }))
        assertEquals(mapOf("x" to listOf(Link("https://t.me/c", "Канал"))), cat)
        // каталог может перечислять ссылки и просто строками
        val raw = """{"format":"AppShelf-sources","version":1,"apps":{"p":["https://github.com/a/b"]}}"""
        assertEquals(listOf(Link("https://github.com/a/b")), Links.parseCatalog(raw)["p"])
    }

    @Test
    fun linksTravelWithTheList() {
        val app = AppInfo("DroidTop", "io.github.xtratter.droidtop", installer = "com.google.android.packageinstaller")
        val links = mapOf(app.pkg to LinkEntry(42, listOf(Link("https://github.com/Xtratter/droidtop", "GitHub"))))
        val s = Snapshot(1_750_000_000_000, "POCO F3", listOf(app), links)
        assertEquals(links, ListFile.read(ListFile.write(s, Format.JSON)).links)
        val csv = ListFile.read(ListFile.write(s, Format.CSV)).links
        assertEquals(listOf(Link("https://github.com/Xtratter/droidtop")), csv[app.pkg]!!.links)
        assertTrue(ListFile.write(s, Format.MARKDOWN).contains("[GitHub](https://github.com/Xtratter/droidtop)"))
    }

    @Test
    fun notesTravelWithLinksAndLists() {
        val m = mapOf("x" to LinkEntry(5, emptyList(), "вход через Госуслуги"))
        assertEquals(m, Links.parseMine(Links.writeMine(m)))
        val app = AppInfo("X", "x")
        val s = Snapshot(1, "", listOf(app), m)
        assertEquals("вход через Госуслуги", ListFile.read(ListFile.write(s, Format.JSON)).links["x"]!!.note)
        assertEquals("вход через Госуслуги", ListFile.read(ListFile.write(s, Format.CSV)).links["x"]!!.note)
        assertTrue(ListFile.write(s, Format.MARKDOWN).contains("вход через Госуслуги"))
    }

    @Test
    fun backupNames() {
        assertEquals("org.telegram__123.apk", ApkBackup.fileName("org.telegram", 123, false))
        assertEquals("a.b__7.apks", ApkBackup.fileName("a.b", 7, true))
        assertEquals("org.telegram" to 123L, ApkBackup.parse("org.telegram__123.apk"))
        assertEquals("a.b" to 7L, ApkBackup.parse("a.b__7.apks"))
        assertEquals(null, ApkBackup.parse("AppShelf.json"))
    }
}
