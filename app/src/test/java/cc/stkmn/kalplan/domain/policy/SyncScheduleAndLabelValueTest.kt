package cc.stkmn.kalplan.domain.policy

import cc.stkmn.kalplan.data.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SyncScheduleAndLabelValueTest {
    private val json = Json { encodeDefaults = true }
    private fun noon(day: Int) = LocalDate.of(2026, 10, 19).plusDays(day.toLong() - 1).atTime(12, 0).atZone(ZoneId.of("Europe/Berlin"))
    private fun request() = StoredRequest("value", sender = "", subject = "", body = "", receivedMillis = 0, labels = listOf("Custom"))

    @Test fun freshSettingsAllowEveryWeekdayWithoutQuietHours() {
        val settings = json.decodeFromString<Settings>("{}")
        assertEquals((1..7).toSet(), settings.activeSyncWeekdays)
        assertFalse(settings.quietHoursEnabled)
        (1..7).forEach { assertFalse(PlanningPolicy.syncPaused(settings, noon(it))) }
    }

    @Test fun legacyPauseRulesMigrateToEquivalentActiveDaysAndQuietSwitch() {
        val settings = json.decodeFromString<Settings>("""{"pauseWeekends":true,"pausedWeekdays":[2],"quietFrom":"22:00","quietUntil":"06:00"}""")
        assertEquals(setOf(1, 3, 4, 5), settings.activeSyncWeekdays)
        assertTrue(settings.quietHoursEnabled)
        assertTrue(PlanningPolicy.syncPaused(settings, noon(2)))
        assertTrue(PlanningPolicy.syncPaused(settings, noon(6)))
        assertFalse(PlanningPolicy.syncPaused(settings, noon(3)))
        assertTrue(PlanningPolicy.syncPaused(settings, noon(3).withHour(23)))
        assertEquals(settings, json.decodeFromString<Settings>(json.encodeToString(settings)))
    }

    @Test fun explicitActiveDaysReplaceLegacyWeekendAndPausedDayFlags() {
        val settings = json.decodeFromString<Settings>("""{"pauseWeekends":true,"pausedWeekdays":[6],"activeSyncWeekdays":[6]}""")
        assertFalse(PlanningPolicy.syncPaused(settings, noon(6)))
        assertTrue(PlanningPolicy.syncPaused(settings, noon(1)))
        assertTrue(PlanningPolicy.syncPaused(settings.copy(activeSyncWeekdays = emptySet()), noon(6)))
    }

    @Test fun disablingQuietHoursPreservesTimesWithoutAnyHiddenPause() {
        val original = Settings(quietFrom = "22:00", quietUntil = "06:00")
        val disabled = json.decodeFromString<Settings>(json.encodeToString(original.copy(quietHoursEnabled = false)))
        assertEquals("22:00", disabled.quietFrom)
        assertEquals("06:00", disabled.quietUntil)
        assertFalse(PlanningPolicy.syncPaused(disabled, noon(1).withHour(23)))
        assertFalse(PlanningPolicy.syncPaused(disabled, noon(2).withHour(5)))
        assertTrue(PlanningPolicy.syncPaused(disabled.copy(quietHoursEnabled = true), noon(1).withHour(23)))
    }

    @Test fun blankLabelPricesDoNotInheritAnyGlobalChargesOrPresetSelection() {
        val preset = FlatFeePreset("global", "Global", flatCents = 5000)
        val settings = Settings(value = ValueSettings(workCentsPerHour = 9000, travelCentsPerHour = 3000,
            centsPerKm = 80, flatCents = 1000, flatFeePresetId = "global", flatFeePresetIds = listOf("global"),
            distanceBands = listOf(DistanceBand(100.0, 2000)), flatFeeComponents = listOf(preset)),
            flatFeePresets = listOf(preset), labels = listOf(LabelPolicy("Custom", valueOverride = ValueOverride())))
        val value = PlanningPolicy.valueSettings(request(), settings)
        assertEquals(0L, PlanningPolicy.value(StoredCandidate(durationMinutes = 120), 90, 40.0, value).totalCents)
        assertTrue(value.flatFeePresetIds.isEmpty())
        assertTrue(value.flatFeePresetId.isEmpty())
        assertTrue(value.distanceBands.isEmpty())
        assertTrue(value.flatFeeComponents.isEmpty())
    }

    @Test fun onlyDisablingEntireLabelOverrideRestoresGlobalPrices() {
        val global = ValueSettings(workCentsPerHour = 7500, travelCentsPerHour = 2500, centsPerKm = 50, flatCents = 1000)
        val settings = Settings(value = global, labels = listOf(LabelPolicy("Custom", valueOverride = null)))
        assertEquals(global, PlanningPolicy.valueSettings(request(), settings))
    }

    @Test fun explicitLabelTravelFlatFeeCombinesWithWorkRateWithoutGlobalFeeLeakage() {
        val trip = FlatFeePreset("trip", "Trip", 2500, appliesTo = "TRAVEL", replacesHourly = true)
        val global = FlatFeePreset("global", "Global", 9000)
        val settings = Settings(value = ValueSettings(flatFeePresetIds = listOf("global")), flatFeePresets = listOf(global, trip),
            labels = listOf(LabelPolicy("Custom", valueOverride = ValueOverride(workCentsPerHour = 6000, flatFeePresetIds = listOf("trip")))))
        val value = PlanningPolicy.value(StoredCandidate(durationMinutes = 120), 60, 10.0, PlanningPolicy.valueSettings(request(), settings))
        assertEquals(14500L, value.totalCents)
        assertEquals(2500L, value.travelFlatCents)
    }
}
