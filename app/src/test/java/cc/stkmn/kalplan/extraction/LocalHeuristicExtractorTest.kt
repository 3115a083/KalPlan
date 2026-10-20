package cc.stkmn.kalplan.extraction

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class LocalHeuristicExtractorTest {
    private fun input(body: String, subject: String = "Anfrage") = ExtractionInput(
        "sender@example.invalid", subject, body, Instant.parse("2026-10-19T08:00:00Z")
    )
    private fun sources(body: String, subject: String = "Anfrage") = LocalHeuristicExtractor.temporalSources(input(body, subject), TemporalLocale.DE_DE)
    private fun parse(source: TemporalFallback) = FlexibleTemporalParser().parse(
        source.dateText, source.timeText, locale = TemporalLocale.DE_DE,
        reference = Instant.parse("2026-10-19T08:00:00Z").atZone(java.time.ZoneId.of("Europe/Berlin"))
    )

    @Test fun removesVisibleHistoryAndSignatureButKeepsCurrentComment() {
        val result = LocalHeuristicExtractor.context(input("Termin 19.10.2026, 13–16\nKommentar: heute besprechen\n> Alter Termin 20.10.2026\n-- \nAlte Signatur 21.10.2026"))
        assertTrue(result.contains("Kommentar: heute besprechen"))
        assertFalse(result.contains("Alter Termin"))
        assertFalse(result.contains("Signatur"))
    }

    @Test fun recognizesCommonReplyHeadersWithoutTreatingOrdinaryFromAsAChain() {
        listOf("On Mon, 19 Oct 2026 sender wrote:", "Am 19.10.2026 schrieb Absender:", "-----Original Message-----", "Von: sender@example.invalid\nGesendet: 18.10.2026").forEach { marker ->
            val result = LocalHeuristicExtractor.context(input("Aktuell\n$marker\nTermin 20.10.2026 09:00"))
            assertFalse(marker, result.contains("20.10.2026"))
            assertTrue(marker, result.contains("Aktuell"))
        }
        assertTrue(LocalHeuristicExtractor.context(input("From: Schulungsteam\nTermin am 19.10.2026 um 13:00")).contains("Termin am"))
    }

    @Test fun ranksAppointmentContextAboveUnrelatedInvoiceAndPreservesSourceWording() {
        val body = "Rechnung vom 01.10.2026\n\nUnser Termin ist am 19.10.2026 von 13–16 Uhr."
        val result = sources(body)
        assertEquals("Unser Termin ist am 19.10.2026 von 13–16 Uhr.", result.dateText)
        assertEquals(result.dateText, result.timeText)
        assertEquals(LocalDate.of(2026, 10, 19), parse(result).candidates.single().date)
        assertEquals(LocalTime.of(13, 0), parse(result).candidates.single().startTime)
        assertFalse(result.ambiguous)
        val sameParagraph = sources("Rechnung vom 01.10.2026. Unser Termin ist am 19.10.2026 von 13–16 Uhr.")
        assertFalse(sameParagraph.dateText.contains("01.10.2026"))
        assertEquals(LocalDate.of(2026, 10, 19), parse(sameParagraph).candidates.single().date)
    }

    @Test fun joinsComplementarySubjectAndCurrentBodyWithoutUsingQuotedDates() {
        val result = sources("Zeit: 13–16 Uhr\n> Zeit: 09–10 Uhr", "Termin am 19.10.2026")
        assertTrue(result.dateText.contains("Termin am 19.10.2026"))
        assertTrue(result.timeText.contains("Zeit: 13–16 Uhr"))
        assertEquals(LocalTime.of(13, 0), parse(result).candidates.single().startTime)
        assertFalse(result.ambiguous)
    }

    @Test fun keepsDateAlternativesAndRelativeConflictInTheChosenContext() {
        val alternatives = sources("Termin: 19.10.2026 oder 20.10.2026, jeweils 13–16 Uhr")
        assertEquals(2, parse(alternatives).candidates.size)
        val conflict = sources("Termin: HEUTE!20.10.26, 13–16 Uhr")
        assertTrue(parse(conflict).issues.any { it.code == "relative_explicit_date_conflict" })
    }

    @Test fun multipleDifferentTimesRequireReviewAndRepeatedRecordsNeverCrossPair() {
        assertTrue(sources("Termin am 19.10.2026 um 13:00, Rückruf um 17:00.").ambiguous)
        val result = sources("Datum: 19.10.2026\nZeit: 13–16 Uhr\nDatum: 20.10.2026\nZeit: 09–10 Uhr")
        assertTrue(result.ambiguous)
        assertTrue(result.dateText.contains("19.10.2026"))
        assertFalse(result.dateText.contains("20.10.2026"))
        assertFalse(result.timeText.contains("09–10"))
        assertTrue(result.evidence.contains("20.10.2026"))
    }

    @Test fun dateOnlyTextDoesNotInventATimeAndScanningIsBounded() {
        val result = sources("Datum: 19.10.2026")
        assertEquals("", result.timeText)
        assertEquals(null, parse(result).candidates.single().startTime)
        val bounded = LocalHeuristicExtractor.context(input("Aktuell\n".repeat(300) + "Geheim 20.10.2026"))
        assertFalse(bounded.contains("Geheim"))
        assertTrue(sources("Termin 19.10.2026 13:00 " + "x".repeat(30_000)).evidence.length <= 480)
    }
}
