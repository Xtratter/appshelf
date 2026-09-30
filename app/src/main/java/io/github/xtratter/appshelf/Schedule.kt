package io.github.xtratter.appshelf

import java.util.Calendar
import java.util.TimeZone

/** Как часто отправлять список на сервер. */
enum class Repeat { OFF, DAILY, WEEKDAYS, EVERY_N }

/**
 * Расписание отправки: время суток и повтор — каждый день, по выбранным дням недели или раз в [every] дней.
 * [days] — дни недели битами: бит (Calendar.SUNDAY − 1) … (Calendar.SATURDAY − 1).
 * [anchor] — первый запуск для «каждые N дней», от него и отсчитываются дни.
 * Без Android-зависимостей — проверяется unit-тестами.
 */
data class Schedule(
    val repeat: Repeat,
    val hour: Int,
    val minute: Int,
    val days: Int = ALL,
    val every: Int = 1,
    val anchor: Long = 0,
) {
    companion object {
        const val ALL = 0x7F
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        fun bit(dayOfWeek: Int) = 1 shl (dayOfWeek - 1)
    }

    fun has(dayOfWeek: Int) = days and bit(dayOfWeek) != 0

    /** Ближайший запуск строго после [now]; null — расписание выключено или не выбран ни один день. */
    fun next(now: Long, tz: TimeZone = TimeZone.getDefault()): Long? {
        val c = Calendar.getInstance(tz)
        fun atTime() { c.set(Calendar.HOUR_OF_DAY, hour); c.set(Calendar.MINUTE, minute); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0) }
        when (repeat) {
            Repeat.OFF -> return null
            Repeat.DAILY, Repeat.WEEKDAYS -> {
                if (repeat == Repeat.WEEKDAYS && days and ALL == 0) return null
                c.timeInMillis = now
                atTime()
                repeat(8) {
                    if (c.timeInMillis > now && (repeat == Repeat.DAILY || has(c.get(Calendar.DAY_OF_WEEK)))) return c.timeInMillis
                    c.add(Calendar.DAY_OF_MONTH, 1)
                    atTime()   // после перехода на летнее/зимнее время час мог сдвинуться
                }
                return null
            }
            Repeat.EVERY_N -> {
                val n = every.coerceAtLeast(1)
                c.timeInMillis = if (anchor > 0) anchor else now
                atTime()
                if (c.timeInMillis <= now) {
                    // перескакиваем сразу через целые периоды, остаток добираем по одному
                    val skip = ((now - c.timeInMillis) / DAY_MS / n).toInt() * n
                    c.add(Calendar.DAY_OF_MONTH, skip); atTime()
                    while (c.timeInMillis <= now) { c.add(Calendar.DAY_OF_MONTH, n); atTime() }
                }
                return c.timeInMillis
            }
        }
    }

    /** Для «каждые N дней»: первый запуск — ближайшее [hour]:[minute] после [now]. */
    fun anchoredAt(now: Long, tz: TimeZone = TimeZone.getDefault()) =
        copy(anchor = copy(repeat = Repeat.DAILY).next(now, tz) ?: now)
}
