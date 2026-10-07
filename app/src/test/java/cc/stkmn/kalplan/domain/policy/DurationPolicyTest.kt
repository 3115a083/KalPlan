package cc.stkmn.kalplan.domain.policy

import cc.stkmn.kalplan.domain.model.RequestLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DurationPolicyTest {
    private val policy = DurationPolicy()

    @Test
    fun extractedDurationWins() {
        val result = policy.resolve(
            extractedDurationMinutes = 90,
            profileOverrideMinutes = 120,
            labels = listOf(RequestLabel("a", "A", priority = 10, defaultDurationMinutes = 180))
        )

        assertEquals(90, result.minutes)
        assertEquals(DurationSource.EXTRACTED, result.source)
        assertFalse(result.assumed)
    }

    @Test
    fun profileOverrideWinsOverLabels() {
        val result = policy.resolve(
            extractedDurationMinutes = null,
            profileOverrideMinutes = 120,
            labels = listOf(RequestLabel("a", "A", priority = 100, defaultDurationMinutes = 240))
        )

        assertEquals(120, result.minutes)
        assertEquals(DurationSource.PROFILE_OVERRIDE, result.source)
        assertTrue(result.assumed)
    }

    @Test
    fun highestPriorityLabelWins() {
        val result = policy.resolve(
            extractedDurationMinutes = null,
            profileOverrideMinutes = null,
            labels = listOf(
                RequestLabel("low", "Low", priority = 1, defaultDurationMinutes = 240),
                RequestLabel("high", "High", priority = 10, defaultDurationMinutes = 90)
            )
        )

        assertEquals(90, result.minutes)
        assertEquals("high", result.labelId)
    }

    @Test
    fun longerDurationWinsWhenLabelPriorityIsEqual() {
        val result = policy.resolve(
            extractedDurationMinutes = null,
            profileOverrideMinutes = null,
            labels = listOf(
                RequestLabel("a", "A", priority = 5, defaultDurationMinutes = 60),
                RequestLabel("b", "B", priority = 5, defaultDurationMinutes = 180)
            )
        )

        assertEquals(180, result.minutes)
        assertEquals("b", result.labelId)
    }

    @Test
    fun globalFallbackIsSixtyMinutes() {
        val result = policy.resolve(null, null, emptyList())

        assertEquals(60, result.minutes)
        assertEquals(DurationSource.GLOBAL_DEFAULT, result.source)
        assertTrue(result.assumed)
    }
}
