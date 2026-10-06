package io.github.xtratter.appshelf

import org.junit.Assert.assertEquals
import org.junit.Test

class RestorePlanTest {
    private fun app(pkg: String, installer: String = "", initiator: String = "") = AppInfo(pkg, pkg, installer = installer, initiator = initiator)
    private val none = emptyList<Link>()

    @Test fun backupWinsOverEverything() {
        assertEquals(Way.BACKUP, RestorePlan.wayOf(app("a", "com.android.vending"), true, listOf(Link("https://github.com/x/y"))))
    }

    @Test fun githubAndApkLinksInstallHere() {
        assertEquals(Way.DIRECT, RestorePlan.wayOf(app("a"), false, listOf(Link("https://github.com/x/y"))))
        assertEquals(Way.DIRECT, RestorePlan.wayOf(app("a"), false, listOf(Link("https://example.com/app.apk"))))
    }

    @Test fun otherLinksOpenAPage() {
        assertEquals(Way.PAGE, RestorePlan.wayOf(app("a"), false, listOf(Link("https://4pda.to/forum/x"))))
        assertEquals(Way.PAGE, RestorePlan.wayOf(app("a"), false, listOf(Link("https://t.me/chan"))))
    }

    @Test fun linkBeatsStoreAndFirstLinkDecides() {
        val links = listOf(Link("https://example.com/site"), Link("https://github.com/x/y"))
        assertEquals(Way.PAGE, RestorePlan.wayOf(app("a", "com.android.vending"), false, links))
    }

    @Test fun storeOnlyWhenTheAppHasOne() {
        assertEquals(Way.STORE, RestorePlan.wayOf(app("a", "com.android.vending"), false, none))
        assertEquals(Way.STORE, RestorePlan.wayOf(app("a", "org.fdroid.fdroid"), false, none))
        assertEquals(Way.STORE, RestorePlan.wayOf(app("a", "ru.vk.store"), false, none))
        assertEquals(Way.STORE, RestorePlan.wayOf(app("a", "com.some.market"), false, none))        // чужой установщик
        assertEquals(Way.SEARCH, RestorePlan.wayOf(app("a", "com.android.packageinstaller"), false, none))   // APK-файл
        assertEquals(Way.SEARCH, RestorePlan.wayOf(app("a", "io.github.xtratter.appshelf"), false, none))
        assertEquals(Way.SEARCH, RestorePlan.wayOf(app("a"), false, none))
    }

    @Test fun groupsKeepOrderAndSkipEmpty() {
        val apps = listOf(app("s1"), app("p1", "com.android.vending"), app("b1"), app("s2"))
        val g = RestorePlan.group(apps, { it == "b1" }, { none })
        assertEquals(listOf(Way.BACKUP, Way.STORE, Way.SEARCH), g.keys.toList())
        assertEquals(listOf("s1", "s2"), g.getValue(Way.SEARCH).map { it.pkg })
    }
}
