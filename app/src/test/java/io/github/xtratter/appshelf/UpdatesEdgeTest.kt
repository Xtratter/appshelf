package io.github.xtratter.appshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdatesEdgeTest {
    @Test fun numbersTakeFirstDottedGroup() {
        assertEquals(emptyList<Int>(), Updates.numbers(""))
        assertEquals(emptyList<Int>(), Updates.numbers("nightly"))
        assertEquals(listOf(1), Updates.numbers("v1"))
        assertEquals(listOf(2024, 5, 1), Updates.numbers("release-2024.5.1-rc2"))
    }

    @Test fun newerIgnoresTrailingZerosAndSuffixes() {
        assertEquals(false, Updates.newer("1.0.0", "1.0"))
        assertEquals(false, Updates.newer("1.0", "1.0.0"))
        assertEquals(true, Updates.newer("1.0.1", "1.0"))
        assertEquals(false, Updates.newer("v1.2.3", "1.2.3-debug"))
        assertEquals(true, Updates.newer("2024.01.02", "1.9"))
        assertEquals(null, Updates.newer("1.0", ""))
        assertEquals(null, Updates.newer("", "1.0"))
    }

    @Test fun pickApkEdgeCases() {
        assertNull(Updates.pickApk(emptyList(), "arm64-v8a"))
        assertNull(Updates.pickApk(listOf("source.zip", "app.apks"), "arm64-v8a"))
        assertEquals("App-Release.APK", Updates.pickApk(listOf("App-Release.APK"), "arm64-v8a"))
        // x86 не должен путаться с x86_64 и наоборот
        assertEquals("app-x86.apk", Updates.pickApk(listOf("app-x86_64.apk", "app-x86.apk"), "x86"))
        assertEquals("app-x86_64.apk", Updates.pickApk(listOf("app-x86_64.apk", "app-x86.apk"), "x86_64"))
        assertNull(Updates.pickApk(listOf("app-x86_64.apk"), "x86"))   // 64-битная сборка на 32-битный x86 не встанет
        assertNull(Updates.pickApk(listOf("app-arm64-v8a.apk"), "armeabi-v7a"))
        assertEquals("app-armeabi-v7a.apk", Updates.pickApk(listOf("app-arm64-v8a.apk", "app-armeabi-v7a.apk"), "armeabi-v7a"))
    }

    @Test fun repoOfCleansUrl() {
        assertEquals("a/b", Updates.repoOf("https://github.com/a/b"))
        assertEquals("a/b", Updates.repoOf("http://github.com/a/b/"))
        assertEquals("a/b", Updates.repoOf("https://github.com/a/b.git"))
        assertEquals("a/b", Updates.repoOf("https://github.com/a/b?tab=readme#top"))
        assertEquals("a/b", Updates.repoOf(" https://github.com/a/b/releases/tag/v1 "))
        assertNull(Updates.repoOf("https://github.com/a"))
        assertNull(Updates.repoOf("https://gitlab.com/a/b"))
    }
}
