package cc.stkmn.kalplan.infrastructure.reply

import cc.stkmn.kalplan.data.*
import org.junit.Assert.*
import org.junit.Test

class ReplyPolicyTest {
    private val request = StoredRequest("id", accountId = "account", sender = "real@example.org", subject = "", body = "", receivedMillis = 0)
    @Test fun debugOverridesRealRecipient() {
        assertEquals("test@example.org", ReplyPolicy.recipient(request, Settings(debug = true, debugTestAddress = "test@example.org")))
    }
    @Test(expected = IllegalArgumentException::class) fun debugCannotFallbackToRealRecipient() {
        ReplyPolicy.recipient(request, Settings(debug = true))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsHeaderInjection() { ReplyPolicy.address("safe@example.org\r\nBcc: hidden@example.org") }
    @Test(expected = IllegalArgumentException::class) fun rejectsMultipleRecipients() { ReplyPolicy.address("a@example.org,b@example.org") }
    @Test fun sendFenceExcludesUnknownAndSendingStates() {
        assertFalse(ReplyPolicy.canSend(request.copy(status = "SENDING")))
        assertFalse(ReplyPolicy.canSend(request.copy(status = "DELIVERY_UNKNOWN")))
        assertFalse(ReplyPolicy.canSend(request.copy(demo = true)))
        assertTrue(ReplyPolicy.canSend(request))
    }
    @Test fun rejectsUnsafeThreadHeader() {
        assertNull(ReplyPolicy.messageId("<id@example.org>\nBcc: bad@example.org"))
        assertEquals("<id@example.org>", ReplyPolicy.messageId("<id@example.org>"))
    }
}
