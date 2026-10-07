package cc.stkmn.kalplan.domain.policy

import cc.stkmn.kalplan.data.*
import org.junit.Assert.*
import org.junit.Test

class AttachmentPolicyTest {
    @Test fun sizeUncertaintyDoesNotSilentlyHideAttachment() {
        assertTrue(AttachmentPolicy.visible(StoredAttachment("logo.png", "image/png", null, true), Settings()))
        assertFalse(AttachmentPolicy.visible(StoredAttachment("logo.png", "image/png", 100, true), Settings()))
    }
    @Test fun firstRuleAndAllCriteriaControlRelevance() {
        val file = StoredAttachment("invoice.PDF", "application/pdf", 1000, false)
        val rule = AttachmentRule(show = false, mimePrefix = "application/", extension = "pdf", nameContains = "invoice", maxBytes = 2000, inline = false)
        assertFalse(AttachmentPolicy.visible(file, Settings(attachmentRules = listOf(rule))))
        assertTrue(AttachmentPolicy.visible(file.copy(size = 3000), Settings(attachmentRules = listOf(rule))))
        assertTrue(AttachmentPolicy.visible(file, Settings(attachments = "ALL", attachmentRules = listOf(rule))))
        assertFalse(AttachmentPolicy.visible(file, Settings(attachments = "IGNORE")))
    }
}
