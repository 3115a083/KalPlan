package cc.stkmn.kalplan.extraction

import cc.stkmn.kalplan.domain.model.CandidateDateRelation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class ExtractionCompatibilityTest {
    private val parser = FlexibleTemporalParser()
    private val zone = ZoneId.of("Europe/Berlin")
    private val reference = ZonedDateTime.of(2026, 10, 7, 10, 0, 0, 0, zone)

    @Test
    fun parsesRelativeGermanDateAndFreeTextDuration() {
        val result = parser.parse(
            dateText = "Der Auftrag ist morgen um 12:00 Uhr",
            timeText = "Der Auftrag ist morgen um 12:00 Uhr",
            durationText = "Dauer ungefähr 2 Stunden",
            reference = reference
        )

        assertEquals(2026, result.candidates.single().date.year)
        assertEquals(10, result.candidates.single().date.monthValue)
        assertEquals(8, result.candidates.single().date.dayOfMonth)
        assertEquals(120, result.durationMinutes)
    }

    @Test
    fun extractsPostalLocationWithoutNetwork() {
        val result = LocationHeuristicExtractor().extract(
            "Bitte kommen Sie zur Musterstraße 12, 44135 Dortmund."
        )

        assertNotNull(result)
        assertTrue(result?.value?.contains("44135 Dortmund") == true)
        assertTrue(result?.value?.contains("Musterstraße 12") == true)
    }

    @Test
    fun separateStructuredDateFieldsCanBeAlternatives() {
        val profile = ExtractionProfile(
            id = "two_dates",
            name = "Two dates",
            multipleDateMode = MultipleDateMode.ALTERNATIVE,
            extractors = listOf(
                ExtractorRule(
                    id = "date1",
                    key = "date1",
                    semantic = SemanticField.DATE,
                    regex = "^Datum 1:\\s*(.+)$"
                ),
                ExtractorRule(
                    id = "date2",
                    key = "date2",
                    semantic = SemanticField.DATE,
                    regex = "^Datum 2:\\s*(.+)$"
                ),
                ExtractorRule(
                    id = "time",
                    key = "time",
                    semantic = SemanticField.TIME,
                    regex = "^Zeit:\\s*(.+)$"
                )
            )
        )
        val input = ExtractionInput(
            sender = "jobs@example.org",
            subject = "Anfrage",
            body = """
                Datum 1: 14.12.2026
                Datum 2: 16.12.2026
                Zeit: 10:00
            """.trimIndent(),
            receivedAt = Instant.parse("2026-10-07T08:00:00Z")
        )

        val result = KalPlanExtractionPipeline().extract(input, profile)

        assertEquals(2, result.candidates.size)
        assertTrue(result.candidates.all { it.dateRelation == CandidateDateRelation.ALTERNATIVE })
    }

    @Test
    fun partialProfileStillUsesFreeTextTimeFallback() {
        val profile = ExtractionProfile(
            id = "date_only",
            name = "Date only",
            extractors = listOf(
                ExtractorRule(
                    id = "date",
                    key = "date",
                    semantic = SemanticField.DATE,
                    regex = "^Datum:\\s*(.+)$"
                )
            )
        )
        val input = ExtractionInput(
            sender = "jobs@example.org",
            subject = "Anfrage",
            body = "Datum: 14.12.2026\nBeginn ist um 13:30 Uhr.",
            receivedAt = Instant.parse("2026-10-07T08:00:00Z")
        )

        val result = KalPlanExtractionPipeline().extract(input, profile)

        assertEquals(13, result.candidates.single().localStartTime?.hour)
        assertEquals(30, result.candidates.single().localStartTime?.minute)
    }
}
