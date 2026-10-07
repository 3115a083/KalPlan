package cc.stkmn.kalplan.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FlexibleTemporalParserTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val reference = ZonedDateTime.of(2026, 10, 7, 10, 0, 0, 0, zone)
    private val parser = FlexibleTemporalParser()

    @Test
    fun parsesGermanDateAndRange() {
        val result = parser.parse(
            dateText = "14.12.2026",
            timeText = "12:00 - 14:00",
            reference = reference
        )

        assertEquals(1, result.candidates.size)
        assertEquals(
            ZonedDateTime.of(2026, 12, 14, 12, 0, 0, 0, zone),
            result.candidates.single().start
        )
        assertEquals(
            ZonedDateTime.of(2026, 12, 14, 14, 0, 0, 0, zone),
            result.candidates.single().end
        )
    }

    @Test
    fun alternativesStaySeparateInsteadOfBecomingRecurrence() {
        val result = parser.parse(
            dateText = "14.12.2026 oder 16.12.2026",
            timeText = "10:00",
            durationText = "1h",
            reference = reference
        )

        assertEquals(2, result.candidates.size)
        assertTrue(result.candidates.all { it.relation == DateRelation.ALTERNATIVE })
    }

    @Test
    fun conjunctionProducesMultipleOptionsNotRecurrence() {
        val result = parser.parse(
            dateText = "14.12.2026 und 16.12.2026",
            timeText = "10:00",
            reference = reference
        )

        assertEquals(2, result.candidates.size)
        assertTrue(result.candidates.all { it.relation == DateRelation.MULTIPLE_OPTIONS })
    }

    @Test
    fun suspiciousExplicitYearIsNotCorrected() {
        val result = parser.parse(
            dateText = "14.12.2024",
            timeText = "10:00",
            reference = reference
        )

        assertEquals(2024, result.candidates.single().date.year)
        assertTrue(result.candidates.single().warnings.contains("explicit_year_suspicious"))
        assertTrue(result.issues.any { it.code == "explicit_year_suspicious" })
    }

    @Test
    fun missingYearMayRollIntoNextYear() {
        val result = parser.parse(
            dateText = "15.01.",
            timeText = "10:00",
            reference = reference
        )

        assertEquals(2027, result.candidates.single().date.year)
        assertTrue(result.candidates.single().warnings.contains("year_inferred"))
    }

    @Test
    fun parsesGermanDurationVariants() {
        assertEquals(90, parser.parseDurationMinutes("1,5h"))
        assertEquals(60, parser.parseDurationMinutes("eine Stunde"))
        assertEquals(90, parser.parseDurationMinutes("90 Minuten"))
    }
}
