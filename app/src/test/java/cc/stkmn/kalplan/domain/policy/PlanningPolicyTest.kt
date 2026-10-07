package cc.stkmn.kalplan.domain.policy

import cc.stkmn.kalplan.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PlanningPolicyTest {
    @Test fun shortLwlIsLessImportantThanLongLwl() {
        val settings = Settings()
        fun request(duration: Int) = StoredRequest("id", sender = "test@example.org", subject = "LWL", body = "", receivedMillis = 1000,
            labels = listOf("LWL"), candidates = listOf(StoredCandidate(durationMinutes = duration)), selectedCandidate = 0)
        assertTrue(PlanningPolicy.priority(request(60), settings, 1000).score < PlanningPolicy.priority(request(240), settings, 1000).score)
    }
    @Test fun fractionalWorkAndTravelRoundInCents() {
        val value = PlanningPolicy.value(StoredCandidate(durationMinutes = 61), 30, 10.0,
            ValueSettings(enabled = true, workCentsPerHour = 6000, billingStepMinutes = 15, travelCentsPerHour = 3000, centsPerKm = 42, flatCents = 1000))
        assertEquals(75, value.billedMinutes)
        assertEquals(10420L, value.totalCents)
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
