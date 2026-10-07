package cc.stkmn.kalplan.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GuidedRuleFactoryTest {
    @Test
    fun learnedRuleToleratesUnicodeWhitespace() {
        val sample = ExtractionInput(
            sender = "",
            subject = "",
            body = "Ort oder Online:  Online",
            receivedAt = Instant.EPOCH
        )
        val candidate = GuidedRuleFactory.candidates(sample)
            .first { it.label == "Ort oder Online" }

        val rule = GuidedRuleFactory.extractor(
            candidate = candidate,
            semantic = SemanticField.ONLINE_OR_LOCATION
        )

        val profile = ExtractionProfile(
            id = "guided",
            name = "Guided",
            extractors = listOf(rule)
        )
        val real = ExtractionInput(
            sender = "",
            subject = "",
            body = "Ort oder Online:\u2002\u00A0Online",
            receivedAt = Instant.EPOCH
        )

        val result = ProfileExtractionEngine().extract(
            MailTextNormalizer().normalize(real),
            profile
        )

        assertEquals("Online", result.values[rule.key]?.value)
        assertTrue(result.issues.isEmpty())
    }
}
