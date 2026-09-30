package io.github.xtratter.appshelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
