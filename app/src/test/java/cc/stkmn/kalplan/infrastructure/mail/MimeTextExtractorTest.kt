package cc.stkmn.kalplan.infrastructure.mail

import jakarta.mail.Session
import jakarta.mail.internet.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Properties

class MimeTextExtractorTest {
    @org.junit.Test fun oversizedWireMessageIsRejectedBeforeContentAccess() {
        val oversized = object : jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(java.util.Properties())) {
            override fun getSize(): Int = MimeTextExtractor.MAX_WIRE_BYTES + 1
            override fun getContent(): Any = error("Oversized content must not be accessed")
        }
        org.junit.Assert.assertThrows(MimeLimitException::class.java) { MimeTextExtractor().extract(oversized) }
    }

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
