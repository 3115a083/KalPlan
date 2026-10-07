package cc.stkmn.kalplan.extraction

import org.junit.Assert.assertEquals
import org.junit.Test

class MailTextNormalizerTest {
    @Test
    fun removesMailArtifactsAndNormalizesSpacing() {
        val normalizer = MailTextNormalizer()
        val result = normalizer.normalizeText(
            "\uFFFCOrt\u00A0oder\u202FOnline:\u2002 Online\u200B"
        )

        assertEquals("Ort oder Online: Online", result)
    }
}
