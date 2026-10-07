package cc.stkmn.kalplan.domain.policy

import cc.stkmn.kalplan.data.*
import org.junit.Assert.*
import org.junit.Test

class MailIdentityPolicyTest {
    private val source = StoredRequest(id = "old", accountId = "a", folder = "INBOX", messageId = "<id@example.org>",
        sender = "sender@example.org", subject = "Appointment", body = "Original", receivedMillis = 1234)
    @Test fun resetPreservesIdentityAcrossLocalEdits() {
        assertTrue(MailIdentityPolicy.matches(source.copy(manual = true, status = "DECLINED"), source.copy(id = "new")))
    }
    @Test fun collidingMessageIdWithChangedBodyIsNotSameMessage() {
        assertFalse(MailIdentityPolicy.matches(source, source.copy(body = "Replacement")))
        assertFalse(MailIdentityPolicy.matches(source, source.copy(recipient = "other@example.org")))
        assertFalse(MailIdentityPolicy.matches(source, source.copy(accountId = "other")))
    }
    @Test fun absentMessageIdUsesSnapshotAndTimestamp() {
        assertTrue(MailIdentityPolicy.matches(source.copy(messageId = null), source.copy(id = "new", messageId = null)))
        assertFalse(MailIdentityPolicy.matches(source.copy(receivedMillis = 0), source.copy(receivedMillis = 0)))
    }
}
