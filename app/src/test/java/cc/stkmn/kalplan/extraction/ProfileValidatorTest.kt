package cc.stkmn.kalplan.extraction

import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidatorTest {
    private val validator = ProfileValidator()

    @Test
    fun rejectsDuplicateKeysAndBrokenRegex() {
        val profile = ExtractionProfile(
            id = "x",
            name = "X",
            extractors = listOf(
                ExtractorRule(
                    id = "a",
                    key = "same",
                    regex = "("
                ),
                ExtractorRule(
                    id = "b",
                    key = "same",
                    regex = "(ok)",
                    sourceVariableKey = "missing"
                )
            )
        )

        val errors = validator.validate(profile)

        assertTrue(errors.any { it.code == "duplicate_extractor_key" })
        assertTrue(errors.any { it.code == "invalid_extractor_regex" })
        assertTrue(errors.any { it.code == "unknown_source_variable" })
    }
}
