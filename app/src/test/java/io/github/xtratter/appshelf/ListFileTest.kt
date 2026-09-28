package io.github.xtratter.appshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ListFileTest {
    private val apps = listOf(
        AppInfo("Яндекс Музыка", "ru.yandex.music", "7.1", 700, "com.android.vending", firstInstall = 1_700_000_000_000),
        AppInfo("DroidTop", "io.github.xtratter.droidtop", "1.10", 17, "com.google.android.packageinstaller",
            "org.telegram.messenger", 1_750_000_000_000),
        AppInfo("Ёлка", "ru.elka", installer = "org.fdroid.fdroid"),
        AppInfo("aurora, \"quoted\"\nname", "com.example.q", installer = "com.aurora.store"),
        AppInfo("Camera", "com.android.camera", system = true),
    )

    @Test
    fun detectsSources() {
        assertEquals(Source.PLAY, apps[0].source)
        assertEquals(Source.APK, apps[1].source)
        assertEquals(Source.FDROID, apps[2].source)
        assertEquals(Source.AURORA, apps[3].source)
        assertEquals(Source.PREINSTALLED, apps[4].source)
        assertEquals(Source.OTHER, AppInfo("x", "x", installer = "com.some.store").source)
        assertEquals(Source.UNKNOWN, AppInfo("x", "x").source)
        assertEquals(Source.APK, AppInfo("x", "x", initiator = "com.android.chrome").source)
        // системное приложение, обновлённое из Play, — из Play
        assertEquals(Source.PLAY, AppInfo("x", "x", installer = "com.android.vending", system = true).source)
    }

    @Test
    fun sortsByNameLikeADictionary() {
        val names = ListFile.sorted(apps, Locale("ru")).map { it.label.take(5) }
        // регистр не важен, латиница перед кириллицей, «Ёлка» рядом с «Е…», а не в конце
        assertEquals(listOf("auror", "Camer", "Droid", "Ёлка", "Яндек"), names)
        assertEquals("Е", ListFile.section("ёлка"))
        assertEquals("#", ListFile.section("4PDA"))
        assertEquals("D", ListFile.section("  droid"))
    }

    @Test
    fun jsonRoundTrip() {
        val s = Snapshot(1_760_000_000_000, "Xiaomi POCO F3 · Android 16", apps)
        val back = ListFile.read(ListFile.write(s, Format.JSON))
        assertEquals(s.created, back.created)
        assertEquals(s.device, back.device)
        assertEquals(apps, back.apps)
    }

    @Test
    fun csvRoundTripKeepsQuotesCommasAndNewlines() {
        val text = ListFile.write(Snapshot(0, "", apps), Format.CSV)
        val back = ListFile.read(text).apps
        assertEquals(apps.map { it.pkg }, back.map { it.pkg })
        assertEquals("aurora, \"quoted\"\nname", back[3].label)
        assertEquals("org.telegram.messenger", back[1].initiator)
        assertEquals(ListFile.date(apps[0].firstInstall), ListFile.date(back[0].firstInstall))
        assertTrue(back[4].system)
    }

    @Test
    fun readsForeignCsvWithOnlyPackageColumn() {
        val back = ListFile.read("﻿Package\r\ncom.a\r\n\r\ncom.b\n").apps
        assertEquals(listOf("com.a", "com.b"), back.map { it.pkg })
        assertEquals("com.a", back[0].label)
    }

    @Test
    fun markdownTableEscapesPipes() {
        val md = ListFile.write(Snapshot(0, "Phone", listOf(AppInfo("A|B", "com.ab", installer = "com.android.vending"))),
            Format.MARKDOWN) { if (it == Source.PLAY) "Google Play" else it.name }
        assertTrue(md, "| A\\|B | `com.ab` | Google Play |" in md)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherJson() {
        ListFile.read("""{"apps": []}""")
    }
}
