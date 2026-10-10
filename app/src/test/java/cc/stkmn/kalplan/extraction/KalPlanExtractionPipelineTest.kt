package cc.stkmn.kalplan.extraction

import cc.stkmn.kalplan.domain.model.AppointmentMode
import cc.stkmn.kalplan.domain.model.CandidateDateRelation
import cc.stkmn.kalplan.domain.model.RequestLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class KalPlanExtractionPipelineTest {
    private val pipeline = KalPlanExtractionPipeline()

    @Test
    fun structuredOnlineRequestUsesProfileEvidence() {
        val input = ExtractionInput(
            sender = "dispo@example.org",
            subject = "Anfrage",
            body = """
                ￼
                Datum:  14.12.2026
                Zeit:  12:00 - 14:00
                ￼
                Ort oder Online:  Online
            """.trimIndent(),
            receivedAt = Instant.parse("2026-10-07T08:00:00Z")
        )

        val profile = structuredProfile()
        val result = pipeline.extract(input, profile)

        assertEquals(1, result.candidates.size)
        val candidate = result.candidates.single()
        assertEquals(AppointmentMode.ONLINE, candidate.mode)
        assertEquals(true, candidate.online)
        assertNull(candidate.locationText)
        assertEquals(14, candidate.localDate?.dayOfMonth)
        assertEquals(12, candidate.localStartTime?.hour)
        assertEquals(14, candidate.localEndTime?.hour)
    }

    @Test
    fun alternativeDatesRemainTwoCandidates() {
        val input = ExtractionInput(
            sender = "dispo@example.org",
            subject = "Anfrage",
            body = """
                Datum: 14.12.2026 oder 16.12.2026
                Zeit: 10:00
                Ort oder Online: Dortmund
            """.trimIndent(),
            receivedAt = Instant.parse("2026-10-07T08:00:00Z")
        )

        val result = pipeline.extract(input, structuredProfile())

        assertEquals(2, result.candidates.size)
        assertTrue(result.candidates.all { it.dateRelation == CandidateDateRelation.ALTERNATIVE })
        assertTrue(result.issues.any { it.code == "alternative_dates" })
    }

    @Test
    fun multipleLabelsCanMatchSameRequest() {
        val input = ExtractionInput(
            sender = "planung@hospital.example",
            subject = "Medizin Auftrag",
            body = "Fiber inspection",
            receivedAt = Instant.parse("2026-10-07T08:00:00Z")
        )
        val rules = listOf(
            LabelRule(
                id = "sender",
                label = RequestLabel("hospital", "Klinik", priority = 30),
                source = LabelRuleSource.SENDER,
                regex = "@hospital\\.example$"
            ),
            LabelRule(
                id = "medicine",
                label = RequestLabel("med", "Medizin", priority = 80),
                source = LabelRuleSource.SUBJECT,
                regex = "\\bMedizin\\b"
            ),
            LabelRule(
                id = "fiber",
                label = RequestLabel("fiber", "Fiber", priority = 10),
                source = LabelRuleSource.BODY,
                regex = "\\bFiber\\b"
            )
        )

        val result = pipeline.extract(input, profile = null, labelRules = rules)

        assertEquals(listOf("med", "hospital", "fiber"), result.labels.map { it.id })
    }

    private fun structuredProfile() = ExtractionProfile(
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
                id = "where",
                key = "where",
                semantic = SemanticField.ONLINE_OR_LOCATION,
                regex = "^Ort oder Online:\\s*(.+)$"
            )
        )
    )
}
