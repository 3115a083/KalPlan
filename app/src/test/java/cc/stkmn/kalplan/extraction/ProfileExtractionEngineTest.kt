package cc.stkmn.kalplan.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ProfileExtractionEngineTest {
    private val engine = ProfileExtractionEngine()

    @Test
    fun structuredFieldsAreExtractedInOrder() {
        val input = ExtractionInput(
            sender = "jobs@example.org",
            subject = "Auftragsanfrage",
            body = """
                Datum: 14.12.2026
                Zeit: 12:00 - 14:00
                Ort oder Online: Online
            """.trimIndent(),
            receivedAt = Instant.parse("2026-10-07T10:00:00Z")
        )

        val profile = ExtractionProfile(
            id = "structured",
            name = "Structured",
            extractors = listOf(
                ExtractorRule(
                    id = "date",
                    key = "date",
                    semantic = SemanticField.DATE,
                    regex = "^Datum:\\s*(.+)$"
                ),
                ExtractorRule(
                    id = "time",
                    key = "time",
                    semantic = SemanticField.TIME,
                    regex = "^Zeit:\\s*(.+)$"
                ),
                ExtractorRule(
                    id = "mode",
                    key = "mode",
                    semantic = SemanticField.ONLINE_OR_LOCATION,
                    regex = "^Ort oder Online:\\s*(.+)$"
                )
            )
        )

        val result = engine.extract(input, profile)

        assertTrue(result.issues.isEmpty())
        assertEquals("14.12.2026", result.values["date"]?.value)
        assertEquals("12:00 - 14:00", result.values["time"]?.value)
        assertEquals("Online", result.values["mode"]?.value)
    }

    @Test
    fun invalidRequiredRuleBecomesIssueInsteadOfCrash() {
        val input = ExtractionInput(
            sender = "x@example.org",
            subject = "",
            body = "hello",
            receivedAt = Instant.EPOCH
        )
        val profile = ExtractionProfile(
            id = "bad",
            name = "Bad",
            extractors = listOf(
                ExtractorRule(
                    id = "required",
                    key = "date",
                    semantic = SemanticField.DATE,
                    regex = "^Datum:\\s*(.+)$",
                    required = true
                )
            )
        )

        val result = engine.extract(input, profile)

        assertTrue(result.issues.any { it.code == "required_field_missing" })
    }
}
