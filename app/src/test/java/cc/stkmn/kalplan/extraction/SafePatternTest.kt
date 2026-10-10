package cc.stkmn.kalplan.extraction

import org.junit.Assert.*
import org.junit.Test

class SafePatternTest {
    @Test fun nestedQuantifierDoesNotBacktrackExponentially() {
        assertFalse(SafePattern.compile("(a+)+$").matcher("a".repeat(100_000) + "!").find())
    }
    @Test fun unsupportedBackreferenceFailsProfileValidation() {
        val p = ExtractionProfile(id = "p", name = "p", extractors = listOf(ExtractorRule("e", "e", regex = "(a)\\1")))
        assertTrue(ProfileValidator().validate(p).isNotEmpty())
    }
    @Test fun htmlDoesNotIncludeActiveContent() {
        val text = MailTextNormalizer().htmlToText("<p>Datum: 2026-10-08</p><script>secret()</script><p>Beginn: 14:00</p>")
        assertTrue(text.contains("2026-10-08")); assertFalse(text.contains("secret"))
    }
}
