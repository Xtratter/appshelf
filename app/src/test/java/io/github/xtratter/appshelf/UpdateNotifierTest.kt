package io.github.xtratter.appshelf

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateNotifierTest {
    private fun item(pkg: String, label: String, from: String, to: String) = UpdateNotifier.Item(pkg, label, from, to, "v$to")

    @Test fun onlyNewUpdatesAreFresh() {
        val a = item("a", "Alpha", "1.0", "1.1")
        val b = item("b", "Beta", "2.0", "2.1")
        assertEquals(listOf(b), UpdateNotifier.fresh(listOf(a, b), setOf(a.key)))
        assertEquals(emptyList<UpdateNotifier.Item>(), UpdateNotifier.fresh(listOf(a, b), setOf(a.key, b.key)))
        // тот же пакет, но новый релиз — снова новость
        assertEquals(listOf(item("a", "Alpha", "1.0", "1.2")), UpdateNotifier.fresh(listOf(item("a", "Alpha", "1.0", "1.2")), setOf(a.key)))
    }

    @Test fun summaryShowsFirstFiveAndEllipsis() {
        val items = (1..7).map { item("p$it", "App$it", "1", "2") }
        assertEquals("App1 1 → 2, App2 1 → 2, App3 1 → 2, App4 1 → 2, App5 1 → 2, …", UpdateNotifier.summary(items))
        assertEquals("App1 1 → 2", UpdateNotifier.summary(items.take(1)))
    }
}
