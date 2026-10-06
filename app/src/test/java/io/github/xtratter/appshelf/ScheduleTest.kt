package io.github.xtratter.appshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ScheduleTest {
    private val tz = TimeZone.getTimeZone("Europe/Moscow")
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm EEE", Locale.ROOT).apply { timeZone = tz }
    private fun t(s: String) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { timeZone = tz }.parse(s)!!.time
    private fun Long?.str() = this?.let { fmt.format(it) }

    // 2026-09-30 — среда
    private val now = t("2026-09-30 14:00")

    @Test
    fun daily() {
        assertEquals("2026-09-30 21:00 Wed", Schedule(Repeat.DAILY, 21, 0).next(now, tz).str())
        assertEquals("2026-10-01 09:30 Thu", Schedule(Repeat.DAILY, 9, 30).next(now, tz).str())
        // ровно в назначенное время — уже следующий день
        assertEquals("2026-10-01 14:00 Thu", Schedule(Repeat.DAILY, 14, 0).next(now, tz).str())
    }

    @Test
    fun weekdays() {
        val monFri = Schedule.bit(Calendar.MONDAY) or Schedule.bit(Calendar.FRIDAY)
        assertEquals("2026-10-02 21:00 Fri", Schedule(Repeat.WEEKDAYS, 21, 0, monFri).next(now, tz).str())
        val wed = Schedule.bit(Calendar.WEDNESDAY)
        assertEquals("2026-09-30 21:00 Wed", Schedule(Repeat.WEEKDAYS, 21, 0, wed).next(now, tz).str())
        assertEquals("2026-10-07 08:00 Wed", Schedule(Repeat.WEEKDAYS, 8, 0, wed).next(now, tz).str())
        assertNull(Schedule(Repeat.WEEKDAYS, 21, 0, 0).next(now, tz))
        assertNull(Schedule(Repeat.OFF, 21, 0).next(now, tz))
    }

    @Test
    fun everyNDays() {
        val s = Schedule(Repeat.EVERY_N, 21, 0, every = 3).anchoredAt(now, tz)
        assertEquals("2026-09-30 21:00 Wed", s.anchor.str())
        assertEquals("2026-09-30 21:00 Wed", s.next(now, tz).str())
        assertEquals("2026-10-03 21:00 Sat", s.next(t("2026-09-30 21:00"), tz).str())
        // через много периодов ритм тот же
        assertEquals("2027-01-01 21:00 Fri", s.next(t("2026-12-30 12:00"), tz).str())
    }

    @Test
    fun keepsLocalTimeAcrossDst() {
        val berlin = TimeZone.getTimeZone("Europe/Berlin")
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { timeZone = berlin }
        // 25 октября 2026 — переход на зимнее время
        val next = Schedule(Repeat.DAILY, 21, 0).next(f.parse("2026-10-25 22:00")!!.time, berlin)
        assertEquals("2026-10-26 21:00", f.format(next!!))
    }

    @Test
    fun encodesUrl() {
        assertEquals("https://h/remote.php/dav/files/me/My%20Apps",
            WebDav.encodeUrl("https://h/remote.php/dav/files/me/My Apps"))
        assertEquals("https://h:8443/%D0%9F%D0%B0%D0%BF%D0%BA%D0%B0", WebDav.encodeUrl("https://h:8443/Папка"))
        // уже закодированное не кодируется второй раз
        assertEquals("https://h/a%20b", WebDav.encodeUrl("https://h/a%20b"))
        assertEquals("AppShelf-POCO%20F3.json", WebDav.encodeSegment("AppShelf-POCO F3.json"))
        assertEquals("a%2Fb%3Fc", WebDav.encodeSegment("a/b?c"))
    }

    @Test
    fun versions() {
        val ms = t("2026-09-30 13:53")
        assertEquals("AppShelf-POCO F3_2026-09-30_135300.json", ListFile.versionName("AppShelf-POCO F3.json", ms, tz))
        assertEquals("list_2026-09-30_135300", ListFile.versionName("list", ms, tz))
        val names = listOf(
            "AppShelf-POCO F3.json",                       // старый файл без даты не трогаем
            "AppShelf-POCO F3_2026-09-28_210000.json",
            "AppShelf-POCO F3_2026-09-30_135300.json",
            "AppShelf-POCO F3_2026-09-29_210000.json",
            "AppShelf-Pixel 8_2026-09-01_210000.json",     // другой телефон
            "notes.md",
        )
        assertEquals(listOf("AppShelf-POCO F3_2026-09-28_210000.json"), ListFile.oldVersions(names, "AppShelf-POCO F3.json", 2))
        assertEquals(emptyList<String>(), ListFile.oldVersions(names, "AppShelf-POCO F3.json", 10))
    }

    @Test
    fun offAndEmptyWeekdaysNeverRun() {
        assertNull(Schedule(Repeat.OFF, 9, 0).next(now, tz))
        assertNull(Schedule(Repeat.WEEKDAYS, 9, 0, days = 0).next(now, tz))
    }

    @Test
    fun singleWeekdayWrapsToNextWeek() {
        val wed = Schedule(Repeat.WEEKDAYS, 9, 0, days = Schedule.bit(Calendar.WEDNESDAY))
        assertEquals("2026-10-07 09:00 Wed", wed.next(now, tz).str())   // в эту среду 09:00 уже прошло
    }

    @Test
    fun crossesYearAndLeapDay() {
        assertEquals("2027-01-01 08:00 Fri", Schedule(Repeat.DAILY, 8, 0).next(t("2026-12-31 23:00"), tz).str())
        assertEquals("2028-02-29 12:00 Tue", Schedule(Repeat.DAILY, 12, 0).next(t("2028-02-28 23:45"), tz).str())
    }

    @Test
    fun everyNKeepsThePhaseFromTheAnchor() {
        val anchor = t("2026-09-01 10:00")
        val s = Schedule(Repeat.EVERY_N, 10, 0, every = 3, anchor = anchor)
        assertEquals("2026-10-01 10:00 Thu", s.next(now, tz).str())       // 1 сен + 30 дней
        assertEquals("2026-09-01 10:00 Tue", s.next(t("2026-08-01 00:00"), tz).str())   // якорь в будущем
        // every < 1 считается как 1
        assertEquals("2026-10-01 10:00 Thu", Schedule(Repeat.EVERY_N, 10, 0, every = 0, anchor = anchor).next(now, tz).str())
    }

    @Test
    fun anchoredAtStartsAtTheNearestTime() {
        assertEquals("2026-09-30 21:00 Wed", Schedule(Repeat.EVERY_N, 21, 0, every = 2).anchoredAt(now, tz).anchor.str())
        assertEquals("2026-10-01 09:00 Thu", Schedule(Repeat.EVERY_N, 9, 0, every = 2).anchoredAt(now, tz).anchor.str())
    }

    @Test
    fun nextIsAlwaysInTheFuture() {
        val s = listOf(Schedule(Repeat.DAILY, 3, 15), Schedule(Repeat.WEEKDAYS, 22, 5, days = 0x2A),
            Schedule(Repeat.EVERY_N, 7, 30, every = 5, anchor = t("2026-01-01 07:30")))
        var x = t("2026-03-01 00:00")
        repeat(400) {
            for (sc in s) assertTrue("$sc at ${fmt.format(x)}", sc.next(x, tz)!! > x)
            x += 17 * 60 * 60 * 1000L + 13 * 60 * 1000L
        }
    }
}
