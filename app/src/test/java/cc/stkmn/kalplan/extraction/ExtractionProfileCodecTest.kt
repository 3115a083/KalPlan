package cc.stkmn.kalplan.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractionProfileCodecTest {
    private val codec = ExtractionProfileCodec()

    @Test
    fun profileRoundTripsWithTransformsAndSemantics() {
        val profile = ExtractionProfile(
            id = "example",
            name = "Example",
            matchers = listOf(
                MatcherRule(
                    id = "subject",
                    regex = "Auftrag",
                    source = InputSource.SUBJECT
                )
            ),
            extractors = listOf(
                ExtractorRule(
                    id = "date",
                    key = "date",
                    semantic = SemanticField.DATE,
                    regex = "^Datum:\\s*(.+)$",
                    transforms = listOf(
                        ValueTransform.Trim,
                        ValueTransform.RegexReplace(
                            regex = "\\s+",
                            replacement = " "
                        )
                    )
                )
            ),
            multipleDateMode = MultipleDateMode.ALTERNATIVE
        )

        val encoded = codec.encode(profile)
        val decoded = codec.decode(encoded)

        assertTrue(decoded.isValid)
        assertEquals(profile, decoded.profile)
        assertTrue(encoded.contains("\"schemaVersion\""))
        assertTrue(encoded.contains("\"type\""))
    }

    @Test
    fun invalidJsonReturnsGenericValidationError() {
        val result = codec.decode("{not-json")

        assertEquals(null, result.profile)
        assertTrue(result.validationErrors.any { it.code == "invalid_json" })
    }
}
