package cc.stkmn.kalplan.domain.policy

import java.time.*

object CalendarDates {
    fun weekStart(day: LocalDate): LocalDate = day.minusDays((day.dayOfWeek.value - 1).toLong())
    fun monthDays(month: YearMonth): List<LocalDate> {
        val from = weekStart(month.atDay(1))
        return (0..41).map { from.plusDays(it.toLong()) }
    }
    fun moveMonth(day: LocalDate, year: Int, month: Int): LocalDate =
        YearMonth.of(year, month).atDay(day.dayOfMonth.coerceAtMost(YearMonth.of(year, month).lengthOfMonth()))
    fun overlapsDay(start: Instant, end: Instant, day: LocalDate, zone: ZoneId): Boolean =
        start < day.plusDays(1).atStartOfDay(zone).toInstant() && end > day.atStartOfDay(zone).toInstant()
}
