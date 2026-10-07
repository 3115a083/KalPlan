package cc.stkmn.kalplan.domain.policy

import cc.stkmn.kalplan.data.StoredRequest

/** Reconcile a UIDVALIDITY reset only after comparing the actual bounded source snapshot. */
object MailIdentityPolicy {
    fun matches(a: StoredRequest, b: StoredRequest): Boolean =
        a.accountId == b.accountId && a.folder == b.folder && a.messageId == b.messageId &&
        a.sender == b.sender && a.recipient == b.recipient && a.subject == b.subject &&
        a.receivedMillis > 0 && a.receivedMillis == b.receivedMillis &&
        a.body == b.body && a.attachmentMeta == b.attachmentMeta
}
