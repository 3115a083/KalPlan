package cc.stkmn.kalplan.infrastructure.backup

import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.domain.policy.MailIdentityPolicy

object RestorePolicy {
    /** An old backup must not erase evidence of a reply sent since that backup was created. */
    fun preserveSendFences(imported: AppData, local: AppData): AppData {
        val fenced = local.requests.filter { it.replyId != null || it.status in setOf("SENDING", "DELIVERY_UNKNOWN", "WAITING", "RESERVED", "RESERVATION_FAILED", "TEST_SENT") }
        fun same(a: StoredRequest, b: StoredRequest) = a.id == b.id ||
            a.accountId == b.accountId && a.sourceStableId != null && a.sourceStableId == b.sourceStableId || MailIdentityPolicy.matches(a, b)
        val requests = imported.requests.map { incoming ->
            val existing = fenced.firstOrNull { same(it, incoming) }
            if (existing == null) incoming else incoming.copy(status = if (existing.status == "SENDING") "DELIVERY_UNKNOWN" else existing.status,
                replyId = existing.replyId, failureCode = existing.failureCode, reservationEventId = null)
        }
        return imported.copy(requests = requests + fenced.filter { old -> requests.none { same(old, it) } }.map { it.copy(
            status = if (it.status == "SENDING") "DELIVERY_UNKNOWN" else it.status, reservationEventId = null) })
    }
}
