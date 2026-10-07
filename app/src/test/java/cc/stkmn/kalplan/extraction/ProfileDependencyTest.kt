package cc.stkmn.kalplan.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ProfileDependencyTest {
    @Test
    fun missingSourceVariableNeverFallsBackToWholeMail() {
        val input = ExtractionInput(
            sender = "",
            subject = "",
            body = "Secret-looking value: Dortmund",
            receivedAt = Instant.EPOCH
        )
        val profile = ExtractionProfile(
            id = "dependent",
            name = "Dependent",
            extractors = listOf(
                ExtractorRule(
                    id = "container",
                    key = "container",
                    regex = "^Container:\\s*(.+)$",
                    required = false
                ),
                ExtractorRule(
                    id = "city",
                    key = "city",
                    semantic = SemanticField.CITY,
                    regex = "Dortmund",
                    group = 0,
                    sourceVariableKey = "container",
                    required = false
                )
            )
        )

        val result = ProfileExtractionEngine().extract(input, profile)

        assertFalse(result.values.containsKey("city"))
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun requiredDependentFieldReportsMissingSource() {
        val input = ExtractionInput(
            sender = "",
            subject = "",
            body = "Anything",
            receivedAt = Instant.EPOCH
        )
        val profile = ExtractionProfile(
            id = "dependent",
            name = "Dependent",
            extractors = listOf(
                ExtractorRule(
                    id = "container",
                    key = "container",
                    regex = "^Container:\\s*(.+)$"
                ),
                ExtractorRule(
                    id = "city",
                    key = "city",
                    semantic = SemanticField.CITY,
                    regex = "(.+)",
                    sourceVariableKey = "container",
                    required = true
                )
            )
        )

        val result = ProfileExtractionEngine().extract(input, profile)

        assertEquals("source_variable_missing", result.issues.single().code)
    }
}
