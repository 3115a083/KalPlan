package cc.stkmn.kalplan.infrastructure.mail

import jakarta.mail.Session
import jakarta.mail.internet.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Properties

class MimeTextExtractorTest {
    @Test(expected = IllegalArgumentException::class) fun rejectsOversizedTextBeforeFullDecode() {
        val message = MimeMessage(Session.getInstance(Properties()))
        message.setText("a".repeat(101), "UTF-8"); message.saveChanges()
        MimeTextExtractor(maxTextChars = 100).extract(message)
    }
    @Test(expected = IllegalArgumentException::class) fun boundsMimePartTraversal() {
        val multipart = MimeMultipart()
        repeat(5) { multipart.addBodyPart(MimeBodyPart().apply { setText("hello") }) }
        val message = MimeMessage(Session.getInstance(Properties())).apply { setContent(multipart); saveChanges() }
        MimeTextExtractor(maxParts = 3).extract(message)
    }
    @Test fun onlyMetadataForAttachment() {
        val multipart = MimeMultipart().apply {
            addBodyPart(MimeBodyPart().apply { setText("Appointment 2026-10-08") })
            addBodyPart(MimeBodyPart().apply { setText("ignored"); fileName = "note.txt" })
        }
        val message = MimeMessage(Session.getInstance(Properties())).apply { setContent(multipart); saveChanges() }
        val result = MimeTextExtractor().extract(message)
        assertTrue(result.plainText!!.contains("Appointment"))
        assertFalse(result.plainText!!.contains("ignored"))
        assertEquals("note.txt", result.attachments.single().fileName)
    }
}
