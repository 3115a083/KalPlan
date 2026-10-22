package cc.stkmn.kalplan.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class FlexibleTemporalParserTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val reference = ZonedDateTime.of(2026, 10, 7, 10, 0, 0, 0, zone)
    private val parser = FlexibleTemporalParser()

    @Test
    fun ambiguousAndNonexistentDstTimesNeedReview() {
        listOf("2026-03-29", "2026-10-25").forEach { date ->
            val result = parser.parse(dateText = date, timeText = "02:30", reference = reference)
            assertEquals(null, result.candidates.single().start)
            assertTrue(result.issues.any { it.code == "dst_time_ambiguous_or_invalid" && it.severity == IssueSeverity.NEEDS_REVIEW })
        }
    }

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

    @Test
    fun requestedGermanDateFormatsUseTheReceivedDayAndSeparateTimeRange() {
        val received = Instant.parse("2026-10-19T08:00:00Z").atZone(zone)
        val expected = LocalDate.of(2026, 10, 19)
        listOf("19.10.26", "19.10.2026", "19-10-2026", "19.10.", "heute", "HEUTE!", "HEUTE!19.10.26").forEach { text ->
            val result = parser.parse(dateText = text, timeText = "13–16", reference = received)
            val candidate = result.candidates.single()
            assertEquals(text, expected, candidate.date)
            assertEquals(text, LocalTime.of(13, 0), candidate.startTime)
            assertEquals(text, LocalTime.of(16, 0), candidate.endTime)
            assertTrue(text, result.issues.none { it.code == "relative_explicit_date_conflict" })
        }
        assertEquals(LocalDate.of(2026, 10, 20), parser.parse("Morgen", "13–16", reference = received).candidates.single().date)
    }

    @Test
    fun equivalentRelativeAndExplicitMentionsKeepExplicitYearEvidence() {
        val received = Instant.parse("2026-10-19T08:00:00Z").atZone(zone)
        val result = parser.parse("HEUTE!19.10.26", "13:00", reference = received)
        val candidate = result.candidates.single()
        assertTrue(candidate.explicitYear)
        assertTrue(candidate.warnings.none { it == "year_inferred" })
    }

    @Test
    fun contradictoryRelativeAndExplicitDatesAreBothKeptForReview() {
        val received = Instant.parse("2026-10-19T08:00:00Z").atZone(zone)
        val result = parser.parse("HEUTE!20.10.26", "13–16", reference = received)
        assertEquals(listOf(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 20)), result.candidates.map { it.date })
        assertTrue(result.candidates.all { it.startTime == LocalTime.of(13, 0) && it.endTime == LocalTime.of(16, 0) })
        assertTrue(result.issues.any { it.code == "relative_explicit_date_conflict" && it.severity == IssueSeverity.NEEDS_REVIEW })
    }

    @Test
    fun everyDateMentionIsMaskedBeforeReadingTimeFromTheSameText() {
        val received = Instant.parse("2026-10-19T08:00:00Z").atZone(zone)
        listOf(
            "HEUTE!19.10.26 um 13:00",
            "19.10.26, 19.10.2026 um 13:00",
            "2026-10-19 und 19.10.2026 um 13:00"
        ).forEach { text ->
            val result = parser.parse(dateText = text, reference = received)
            assertEquals(text, 1, result.candidates.size)
            assertEquals(text, LocalTime.of(13, 0), result.candidates.single().startTime)
        }
        val separateDateSource = parser.parse("19.10.26", "HEUTE!19.10.26 um 13:00", reference = received)
        assertEquals(LocalTime.of(13, 0), separateDateSource.candidates.single().startTime)
        val dateWithoutTime = parser.parse("HEUTE!19.10.26", reference = received)
        assertEquals(null, dateWithoutTime.candidates.single().startTime)
        assertTrue(dateWithoutTime.issues.any { it.code == "time_missing" })
    }

    @Test
    fun mixedAbsoluteDateFormatsStaySeparateAndKeepTheirSharedTime() {
        val received = Instant.parse("2026-10-19T08:00:00Z").atZone(zone)
        val result = parser.parse("2026-10-19 oder 20.10.2026 oder 21. Oktober 2026", "13–16", reference = received)
        assertEquals(listOf(19, 20, 21), result.candidates.map { it.date.dayOfMonth })
        assertTrue(result.candidates.all { it.relation == DateRelation.ALTERNATIVE && it.startTime == LocalTime.of(13, 0) })
    }

    @Test
    fun clockRangeFragmentsAreNotInvalidDates() {
        val received = Instant.parse("2026-10-19T08:00:00Z").atZone(zone)
        listOf("14:00-15:00", "14:00 – 15:00", "14:30 - 16", "14.00-15.00 Uhr").forEach { clock ->
            val result = parser.parse("Datum: 14.12.2026\nUhrzeit: $clock", reference = received)
            assertEquals(clock, LocalDate.of(2026, 12, 14), result.candidates.single().date)
            assertEquals(clock, 14, result.candidates.single().startTime?.hour)
            assertTrue(clock, result.issues.none { it.code == "invalid_date" })
        }
    }

    @Test
    fun clockFilteringKeepsDateAlternativesAndRealInvalidDates() {
        val received = Instant.parse("2026-10-19T08:00:00Z").atZone(zone)
        val dates = parser.parse("19-10-2026 oder 20-10-2026", timeText = "14:00-15:00", reference = received)
        assertEquals(listOf(19, 20), dates.candidates.map { it.date.dayOfMonth })
        val invalid = parser.parse("31.02.2026 um 14:00-15:00", reference = received)
        assertTrue(invalid.candidates.isEmpty())
        assertTrue(invalid.issues.any { it.code == "invalid_date" })
    }

    @Test
    fun receivedTimestampAndZoneAnchorRelativeDaysAcrossMidnightAndYearRollover() {
        // UTC still says October 18, while the received local day in Berlin is October 19.
        val received = Instant.parse("2026-10-18T22:30:00Z").atZone(zone)
        assertEquals(LocalDate.of(2026, 10, 19), parser.parse("HEUTE!", "13–16", reference = received).candidates.single().date)
        val yearEnd = Instant.parse("2026-12-31T22:30:00Z").atZone(zone)
        val tomorrow = parser.parse("Morgen 01.01.2027", "13–16", reference = yearEnd)
        assertEquals(LocalDate.of(2027, 1, 1), tomorrow.candidates.single().date)
        assertTrue(tomorrow.issues.none { it.code == "relative_explicit_date_conflict" })
        assertEquals(LocalDate.of(2027, 1, 1), parser.parse("01.01.", "13–16", reference = yearEnd).candidates.single().date)
    }

}

