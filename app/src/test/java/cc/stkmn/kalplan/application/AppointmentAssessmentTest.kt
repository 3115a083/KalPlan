package cc.stkmn.kalplan.application

import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.domain.port.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AppointmentAssessmentTest {
    private fun millis(text: String) = Instant.parse(text).toEpochMilli()
    private val start = millis("2026-10-18T08:00:00Z")
    private val end = millis("2026-10-18T09:00:00Z")
    private val candidate = StoredCandidate(start, end, location = "Office", mode = "ONSITE")
    private val request = StoredRequest("request", sender = "", subject = "", body = "", receivedMillis = 0,
        candidates = listOf(candidate), selectedCandidate = 0, unclear = false)
    private val data = AppData(settings = Settings(originAddress = "Office"),
        calendars = listOf(CalendarPrivacy("work", included = true, showLocation = true)))
    private fun event(from: Long, to: Long, calendar: String = "work", location: String? = "Office") =
        CalendarEventRef("event@1", calendar, Instant.ofEpochMilli(from), Instant.ofEpochMilli(to),
            "Private title", location, "Private description", false, CalendarBusyStatus.BUSY)

    @Test fun missingOriginCannotBecomeFeasibleWithoutTravelCheck() {
        val result = AppointmentAssessment.evaluate(request, data.copy(settings = Settings()), emptyList())
        assertEquals("POSSIBLE", result.status)
        assertTrue("location_unclear" in result.reasons)
        assertEquals("", result.origin)
    }

    @Test fun manualOriginResolvesMissingDefaultWithoutInventingTravel() {
        val result = AppointmentAssessment.evaluate(request.copy(manualOrigin = "Office"),
            data.copy(settings = Settings()), emptyList())
        assertEquals("FEASIBLE", result.status)
    }

    @Test fun recentPreviousLocationCanSupplyOrigin() {
        val result = AppointmentAssessment.evaluate(request, data.copy(settings = Settings()),
            listOf(event(start - 7_200_000, start - 3_600_000)))
        assertEquals("FEASIBLE", result.status)
        assertEquals("Office", result.origin)
    }

    @Test fun unknownOriginDoesNotHideAnExistingConflict() {
        val result = AppointmentAssessment.evaluate(request, data.copy(settings = Settings()), listOf(event(start, end)))
        assertEquals("CONFLICT", result.status)
        assertTrue("time_overlap" in result.reasons)
    }

    @Test fun multiDayWindowIncludesConflictOnLastDay() {
        val lastDayEnd = millis("2026-10-20T09:00:00Z")
        val longCandidate = candidate.copy(endMillis = lastDayEnd)
        val window = requireNotNull(AppointmentAssessment.queryWindow(longCandidate, data.settings, ZoneId.of("Europe/Berlin")))
        val conflict = event(millis("2026-10-20T08:00:00Z"), lastDayEnd)
        assertFalse(conflict.end.isBefore(window.from))
        assertTrue(conflict.start.isBefore(window.to))
        val queried = listOf(conflict).filter { it.start < window.to && it.end > window.from }
        assertEquals("CONFLICT", AppointmentAssessment.evaluate(
            request.copy(candidates = listOf(longCandidate)), data, queried).status)
    }

    @Test fun calendarWindowUsesLocalDaysAcrossDstChange() {
        val c = candidate.copy(startMillis = millis("2026-10-24T21:00:00Z"), endMillis = millis("2026-10-26T08:00:00Z"))
        val window = requireNotNull(AppointmentAssessment.queryWindow(c, Settings(originThreshold = 0, beforeBuffer = 0, afterBuffer = 0), ZoneId.of("Europe/Berlin")))
        assertEquals(Instant.parse("2026-10-23T22:00:00Z"), window.from)
        assertEquals(Instant.parse("2026-10-26T23:00:00Z"), window.to)
    }

    @Test fun invalidIntervalRequiresCorrectionInsteadOfCrashing() {
        for (invalidEnd in listOf(start, start - 1)) {
            val invalid = candidate.copy(endMillis = invalidEnd)
            assertNull(AppointmentAssessment.queryWindow(invalid, data.settings, ZoneId.of("UTC")))
            val result = AppointmentAssessment.evaluate(request.copy(candidates = listOf(invalid)), data, emptyList())
            assertEquals("UNKNOWN", result.status)
            assertEquals(listOf("invalid_interval"), result.reasons)
        }
    }

    @Test fun unselectedCalendarCannotAffectAssessmentOrLeakDetails() {
        val result = AppointmentAssessment.evaluate(request, data, listOf(event(start, end, calendar = "private")))
        assertEquals("FEASIBLE", result.status)
        assertTrue(result.events.isEmpty())
    }

    @Test fun hiddenLocationCanBeUsedInternallyWithoutBeingDisplayed() {
        val privateData = data.copy(calendars = listOf(CalendarPrivacy("work", included = true, useHiddenLocationForRouting = true)))
        val result = AppointmentAssessment.evaluate(request, privateData, listOf(event(start - 7_200_000, start - 3_600_000)))
        assertEquals("FEASIBLE", result.status)
        assertEquals("", result.origin)
        assertNull(result.events.single().location)
        assertNull(result.events.single().title)
        assertNull(result.events.single().description)
    }

    @Test fun localFailedReservationStillBlocksAnotherRequest() {
        val reserved = request.copy(id = "other", status = "RESERVATION_FAILED")
        assertEquals("CONFLICT", AppointmentAssessment.evaluate(request,
            data.copy(requests = listOf(request, reserved)), emptyList()).status)
    }

    @Test fun staleTravelEstimateRequiresReview() {
        val result = AppointmentAssessment.evaluate(request.copy(manualOrigin = "Elsewhere", travelMinutes = 30,
            routeOrigin = "Elsewhere", routeDestination = "Office", routeCheckedMillis = 1000),
            data, emptyList(), nowMillis = 3_601_001)
        assertEquals("POSSIBLE", result.status)
        assertTrue("route_stale" in result.reasons)
    }
}
