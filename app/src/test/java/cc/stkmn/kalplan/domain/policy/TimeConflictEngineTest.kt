package cc.stkmn.kalplan.domain.policy

import cc.stkmn.kalplan.domain.port.CalendarBusyStatus
import cc.stkmn.kalplan.domain.port.CalendarEventRef
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TimeConflictEngineTest {
    private val engine = TimeConflictEngine()

    @Test
    fun freeEventsDoNotBlock() {
        val event = event("free", 10, 11, CalendarBusyStatus.FREE)
        val result = engine.evaluate(t(10), t(11), listOf(event), 30, 30)

        assertEquals(TimeConflictStatus.CLEAR, result.status)
    }

    @Test
    fun overlappingBusyEventConflicts() {
        val event = event("busy", 10, 12, CalendarBusyStatus.BUSY)
        val result = engine.evaluate(t(11), t(13), listOf(event), 0, 0)

        assertEquals(TimeConflictStatus.OVERLAP, result.status)
        assertEquals(listOf("busy"), result.conflictingEventIds)
    }

    @Test
    fun insufficientGapIsTight() {
        val before = event("before", 9, 10, CalendarBusyStatus.BUSY)
        val result = engine.evaluate(t(10, 20), t(11, 20), listOf(before), 30, 0)

        assertEquals(TimeConflictStatus.BUFFER_TIGHT, result.status)
        assertEquals(20L, result.nearestGapBeforeMinutes)
    }

    private fun event(
        id: String,
        startHour: Int,
        endHour: Int,
        status: CalendarBusyStatus
    ) = CalendarEventRef(
        id = id,
        calendarId = "1",
        start = t(startHour),
        end = t(endHour),
        title = null,
        location = null,
        description = null,
        allDay = false,
        busyStatus = status
    )

    private fun t(hour: Int, minute: Int = 0): Instant =
        Instant.parse("2026-10-07T%02d:%02d:00Z".format(hour, minute))
}
