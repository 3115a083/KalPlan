package cc.stkmn.kalplan.domain.policy

import cc.stkmn.kalplan.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PlanningPolicyTest {
    @Test fun longerRequestReceivesSlightlyHigherBaseSortScore() {
        val settings = Settings(labels = listOf(LabelPolicy("Fiber", listOf("fiber"), score = -8, shortThresholdMinutes = 120, shortScore = -35)))
        fun request(duration: Int) = StoredRequest("id", sender = "test@example.org", subject = "Fiber", body = "", receivedMillis = 1000,
            labels = listOf("Fiber"), candidates = listOf(StoredCandidate(durationMinutes = duration)), selectedCandidate = 0)
        assertTrue(PlanningPolicy.priority(request(60), settings, 1000).score < PlanningPolicy.priority(request(240), settings, 1000).score)
    }
    @Test fun labelValueOverrideOnlyReplacesConfiguredParts() {
        val global = ValueSettings(enabled = true, workCentsPerHour = 9300, travelCentsPerHour = 9300, centsPerKm = 42, flatCents = 0)
        val settings = Settings(value = global, labels = listOf(LabelPolicy("Fiber", listOf("fiber"), valueOverride = ValueOverride(workCentsPerHour = 8500, flatCents = 2500))))
        val request = StoredRequest("id", sender = "", subject = "", body = "", receivedMillis = 0, labels = listOf("Fiber"))
        val selected = PlanningPolicy.valueSettings(request, settings)
        assertEquals(8500, selected.workCentsPerHour)
        assertEquals(9300, selected.travelCentsPerHour)
        assertEquals(42, selected.centsPerKm)
        assertEquals(2500, selected.flatCents)
    }
    @Test fun highestWeightedMatchingLabelProvidesPriceOverride() {
        val settings = Settings(value = ValueSettings(enabled = true, workCentsPerHour = 9300), labels = listOf(
            LabelPolicy("General", score = 5, valueOverride = ValueOverride(workCentsPerHour = 9000)),
            LabelPolicy("Special", score = 25, valueOverride = ValueOverride(workCentsPerHour = 8500))
        ))
        val request = StoredRequest("id", sender = "", subject = "", body = "", receivedMillis = 0, labels = listOf("General", "Special"))
        assertEquals(8500, PlanningPolicy.valueSettings(request, settings).workCentsPerHour)
    }
    @Test fun quietHoursCanCrossMidnight() {
        val settings = Settings(quietFrom = "22:00", quietUntil = "06:00")
        val zone = ZoneId.of("Europe/Berlin")
        assertTrue(PlanningPolicy.syncPaused(settings, ZonedDateTime.of(2026, 10, 9, 23, 0, 0, 0, zone)))
        assertTrue(PlanningPolicy.syncPaused(settings, ZonedDateTime.of(2026, 10, 10, 5, 59, 0, 0, zone)))
        assertFalse(PlanningPolicy.syncPaused(settings, ZonedDateTime.of(2026, 10, 10, 6, 0, 0, 0, zone)))
    }
    @Test fun labelTermsSupportAllAndSubjectOnly() {
        val rules = listOf(LabelPolicy("Exact", listOf("maintenance", "night"), keywordMode = "ALL", searchIn = "SUBJECT"))
        assertEquals(listOf("Exact"), PlanningPolicy.labels("x@example.org", "Night maintenance", "", rules))
        assertTrue(PlanningPolicy.labels("x@example.org", "Night", "maintenance", rules).isEmpty())
    }
    @Test fun labelWithoutPatternsRemainsManualOnly() {
        assertTrue(PlanningPolicy.labels("x@example.org", "Anything", "Anything", listOf(LabelPolicy("Manual"))).isEmpty())
    }
    @Test fun fractionalWorkAndTravelRoundInCents() {
        val value = PlanningPolicy.value(StoredCandidate(durationMinutes = 61), 30, 10.0,
            ValueSettings(enabled = true, workCentsPerHour = 6000, billingStepMinutes = 15, travelCentsPerHour = 3000, centsPerKm = 42, flatCents = 1000))
        assertEquals(75, value.billedMinutes)
        assertEquals(10420L, value.totalCents)
    }
    @Test fun billingCanRoundUpNearestOrDown() {
        val candidate = StoredCandidate(durationMinutes = 61)
        fun billed(mode: String) = PlanningPolicy.value(candidate, null, null, ValueSettings(billingStepMinutes = 15, roundingMode = mode)).billedMinutes
        assertEquals(75, billed("UP"))
        assertEquals(60, billed("NEAREST"))
        assertEquals(60, billed("DOWN"))
    }
    @Test fun selectedFlatFeePresetIsMaterializedWithoutDuplicatingLabelValues() {
        val preset = FlatFeePreset("regional", "Regional", flatCents = 1200, distanceBands = listOf(DistanceBand(25.0, 5700)))
        val settings = Settings(value = ValueSettings(flatFeePresetId = preset.id), flatFeePresets = listOf(preset))
        val selected = PlanningPolicy.valueSettings(StoredRequest("id", sender = "", subject = "", body = "", receivedMillis = 0), settings)
        assertEquals(1200, selected.flatCents)
        assertEquals(listOf(DistanceBand(25.0, 5700)), selected.distanceBands)
    }
    @Test fun weekendAndVacationPause() {
        val saturday = ZonedDateTime.of(2026, 10, 10, 12, 0, 0, 0, ZoneId.of("Europe/Berlin"))
        assertTrue(PlanningPolicy.syncPaused(Settings(pauseWeekends = true), saturday))
        assertTrue(PlanningPolicy.syncPaused(Settings(pauseFrom = "2026-10-09", pauseUntil = "2026-10-12"), saturday))
        assertFalse(PlanningPolicy.syncPaused(Settings(syncMinutes = 0), saturday))
    }
    @Test fun labelsRequireAllConfiguredConditions() {
        val rules = listOf(LabelPolicy("Medicine", listOf("praxis"), senderContains = "@clinic.org"))
        assertTrue(PlanningPolicy.labels("user@other.org", "Praxis", "", rules).isEmpty())
        assertEquals(listOf("Medicine"), PlanningPolicy.labels("user@clinic.org", "Praxis", "", rules))
    }
    @Test fun staleNeverDeletesRequest() {
        val r = StoredRequest("id", sender = "", subject = "", body = "", receivedMillis = 0)
        assertTrue(PlanningPolicy.priority(r, Settings(staleHours = 1), 3_600_000).stale)
        assertEquals("NEW", r.status)
    }
}
