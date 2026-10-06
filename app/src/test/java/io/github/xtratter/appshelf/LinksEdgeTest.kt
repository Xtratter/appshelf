package io.github.xtratter.appshelf

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LinksEdgeTest {
    private fun l(u: String) = Link(u)

    @Test fun kindIgnoresCaseAndQuery() {
        assertEquals(LinkKind.GITHUB, Links.kind("https://GitHub.com/a/b"))
        assertEquals(LinkKind.APK, Links.kind("https://example.com/files/app.apk?token=1"))
        assertEquals(LinkKind.FDROID, Links.kind("https://apt.izzysoft.de/fdroid/index/apk/a.b"))
        assertEquals(LinkKind.SITE, Links.kind("https://example.com/"))
    }

    @Test fun validNeedsHttpAndDottedHost() {
        assertTrue(Links.valid(" https://a.b "))
        assertFalse(Links.valid("http://localhost"))
        assertFalse(Links.valid("ftp://a.b"))
        assertFalse(Links.valid("a.b"))
    }

    @Test fun sameIgnoresSchemeWwwAndSlash() {
        assertTrue(Links.same("https://www.example.com/a/", "http://Example.com/a"))
        assertFalse(Links.same("https://example.com/a", "https://example.com/b"))
    }

    @Test fun mergeKeepsOursOnTieAndTakesNewerEmptyAsDeleted() {
        val a = mapOf("p" to LinkEntry(10, listOf(l("https://a.b/1"))), "q" to LinkEntry(5, listOf(l("https://a.b/q"))))
        val b = mapOf("p" to LinkEntry(10, listOf(l("https://a.b/2"))), "q" to LinkEntry(9, emptyList()), "r" to LinkEntry(1, listOf(l("https://a.b/r"))))
        val m = Links.merge(a, b)
        assertEquals("https://a.b/1", m.getValue("p").links.single().url)
        assertTrue(m.getValue("q").links.isEmpty())          // более новое «удалено» побеждает
        assertEquals(3, m.size)
    }

    @Test fun combinedDedupesAcrossSources() {
        val mine = listOf(l("https://github.com/a/b/"))
        val cat = listOf(l("http://www.github.com/a/b"), l("https://f-droid.org/packages/x"))
        val c = Links.combined(mine, cat)
        assertEquals(listOf(false, true), c.map { it.second })
        assertEquals(2, c.size)
        assertTrue(Links.combined(null, null).isEmpty())
    }

    @Test fun parseLinksAcceptsStringsAndObjectsAndSkipsBlank() {
        val arr = JSONArray("""["https://a.b/1", {"url":" https://a.b/2 ","label":" Site "}, "", {"label":"x"}]""")
        assertEquals(listOf(Link("https://a.b/1"), Link("https://a.b/2", "Site")), Links.parseLinks(arr))
        assertTrue(Links.parseLinks(null).isEmpty())
    }

    @Test fun parseMineHandlesBlankAndRejectsOtherJson() {
        assertTrue(Links.parseMine("  ").isEmpty())
        try { Links.parseMine("""{"format":"other"}"""); fail("expected IllegalArgumentException") }
        catch (e: IllegalArgumentException) { }
    }

    @Test fun catalogAcceptsPersonalFormatAndDropsEmpty() {
        val mine = mapOf("p" to LinkEntry(1, listOf(l("https://a.b/1")), "note"), "gone" to LinkEntry(2, emptyList()))
        val cat = Links.parseCatalog(Links.writeMine(mine))
        assertEquals(setOf("p"), cat.keys)
        val back = Links.parseMine(Links.writeMine(mine))
        assertEquals("note", back.getValue("p").note)
        assertTrue(back.getValue("gone").links.isEmpty())
    }
}
