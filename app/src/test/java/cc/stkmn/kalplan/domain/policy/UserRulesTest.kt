package cc.stkmn.kalplan.domain.policy

import cc.stkmn.kalplan.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class UserRulesTest {
    @Test fun senderOrSubjectOrBothBodyTermsCanIndependentlyAssignLabel() {
        val groups = listOf(
            LabelConditionGroup(listOf(LabelCondition("SENDER", "xy@example.org", "EQUALS"))),
            LabelConditionGroup(listOf(LabelCondition("SUBJECT_OR_BODY", "abc"))),
            LabelConditionGroup(listOf(LabelCondition("BODY", "def"), LabelCondition("BODY", "ghi")))
        )
        val labels = listOf(LabelPolicy("Flexible", conditionGroups = groups))
        assertEquals(listOf("Flexible"), PlanningPolicy.labels("xy@example.org", "", "", labels))
        assertEquals(listOf("Flexible"), PlanningPolicy.labels("other", "abc", "", labels))
        assertEquals(listOf("Flexible"), PlanningPolicy.labels("other", "", "abc", labels))
        assertEquals(listOf("Flexible"), PlanningPolicy.labels("other", "", "def plus ghi", labels))
        assertTrue(PlanningPolicy.labels("other", "ghi", "def", labels).isEmpty())
        assertTrue(PlanningPolicy.labels("other", "", "def", labels).isEmpty())
    }
    @Test fun blankOrUnknownConditionsDoNotMatchEverything() {
        for (condition in listOf(LabelCondition("BODY", ""), LabelCondition("UNKNOWN", "x"))) {
            assertTrue(PlanningPolicy.labels("", "x", "x", listOf(LabelPolicy("X", conditionGroups = listOf(LabelConditionGroup(listOf(condition)))))).isEmpty())
        }
    }
    @Test fun hourlyWorkAndFlatTravelCanBeCombinedWithoutHourlyTravelDoubleCharge() {
        val settings = Settings(value = ValueSettings(workCentsPerHour = 6000, travelCentsPerHour = 3000, centsPerKm = 0, flatFeePresetIds = listOf("trip")), flatFeePresets = listOf(FlatFeePreset("trip", "Trip", 2500, appliesTo = "TRAVEL", replacesHourly = true)))
        val request = StoredRequest("r", sender = "", subject = "", body = "", receivedMillis = 0)
        val result = PlanningPolicy.value(StoredCandidate(durationMinutes = 120), 60, 10.0, PlanningPolicy.valueSettings(request, settings))
        assertEquals(12000L, result.workCents)
        assertEquals(0L, result.travelCents)
        assertEquals(2500L, result.travelFlatCents)
        assertEquals(14500L, result.totalCents)
    }
    @Test fun workAndTravelFlatFeesCanBothReplaceHourlyParts() {
        val settings = Settings(value = ValueSettings(centsPerKm = 0, flatFeePresetIds = listOf("work", "trip")), flatFeePresets = listOf(
            FlatFeePreset("work", "Work", 8000, appliesTo = "WORK", replacesHourly = true),
            FlatFeePreset("trip", "Trip", 2000, appliesTo = "TRAVEL", replacesHourly = true)))
        val r = StoredRequest("r", sender = "", subject = "", body = "", receivedMillis = 0)
        val result = PlanningPolicy.value(StoredCandidate(durationMinutes = 180), 120, 20.0, PlanningPolicy.valueSettings(r, settings))
        assertEquals(10000L, result.totalCents)
        assertEquals(0L, result.workCents)
        assertEquals(8000L, result.workFlatCents)
    }
    @Test fun leapYearMonthNavigationClampsDay() {
        assertEquals(LocalDate.of(2028, 2, 29), CalendarDates.moveMonth(LocalDate.of(2027, 1, 31), 2028, 2))
        assertEquals(LocalDate.of(2027, 2, 28), CalendarDates.moveMonth(LocalDate.of(2027, 1, 31), 2027, 2))
        assertEquals(42, CalendarDates.monthDays(YearMonth.of(2028, 2)).size)
        assertEquals(DayOfWeek.MONDAY, CalendarDates.monthDays(YearMonth.of(2028, 2)).first().dayOfWeek)
    }
    @Test fun allDayAndMultiDayEventsMarkEveryOverlappingDayButNotExclusiveEnd() {
        val start = Instant.parse("2026-10-18T00:00:00Z"); val end = Instant.parse("2026-10-21T00:00:00Z")
        assertTrue(CalendarDates.overlapsDay(start, end, LocalDate.of(2026, 10, 19), ZoneOffset.UTC))
        assertFalse(CalendarDates.overlapsDay(start, end, LocalDate.of(2026, 10, 21), ZoneOffset.UTC))
    }
}
