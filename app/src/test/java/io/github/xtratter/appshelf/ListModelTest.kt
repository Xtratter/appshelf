package io.github.xtratter.appshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ListModelTest {
    private val apps = listOf(AppInfo("Zeta", "org.z"), AppInfo("alpha", "org.a"), AppInfo("Beta", "org.b"))

    @Test fun sortsOnceForSameList() {
        val m = ListModel()
        val first = m.sorted(apps)
        assertEquals(listOf("alpha", "Beta", "Zeta"), first.map { it.label })
        assertSame(first, m.sorted(apps))
        assertEquals(listOf("alpha", "Zeta"), m.sorted(apps.filter { it.label != "Beta" }).map { it.label })
    }

    @Test fun matchesLabelPackageAndNote() {
        val m = ListModel()
        val a = apps[1]
        assertTrue(m.matches(a, "alp", { "" }))
        assertTrue(m.matches(a, "org.a", { "" }))
        assertTrue(m.matches(a, "memo", { "My Memo" }))
        assertFalse(m.matches(a, "zzz", { "" }))
        assertFalse(m.matches(a, "alpha org", { "" }))
    }

    @Test fun memoAsksOncePerApp() {
        var n = 0
        val memo = RenderMemo({ n++; null }, { true })
        memo.update(apps[0]); memo.update(apps[0])
        assertEquals(1, n)
        assertFalse(memo.needsLink(apps[0]))   // не APK — ссылки не нужны
    }
}

class AppsReuseTest {
    private val tg = AppInfo("Telegram", "org.telegram", versionName = "10", versionCode = 100, installer = "com.android.vending", lastUpdate = 5)
    private val known = mapOf(tg.pkg to tg)

    @Test fun reusesUnchangedApp() {
        org.junit.Assert.assertSame(tg, Apps.reusable(known, "org.telegram", 5, 100))
    }

    @Test fun rereadsWhenUpdatedReinstalledOrNew() {
        org.junit.Assert.assertNull(Apps.reusable(known, "org.telegram", 6, 100))   // обновили
        org.junit.Assert.assertNull(Apps.reusable(known, "org.telegram", 5, 101))   // другая версия
        org.junit.Assert.assertNull(Apps.reusable(known, "org.new", 5, 100))        // нового в кэше нет
        org.junit.Assert.assertNull(Apps.reusable(emptyMap(), "org.telegram", 5, 100))
    }
}
