package cc.stkmn.kalplan.domain.port

import java.time.Instant

data class MailFolderRef(
    val accountId: String,
    val path: String
)

data class MailEnvelope(
    val stableId: String,
    val messageId: String?,
    val sender: String,
    val subject: String,
    val receivedAt: Instant
)

data class MailAttachmentMeta(
    val fileName: String?,
    val mimeType: String,
    val sizeBytes: Int?,
    val disposition: String?,
    val contentId: String?,
    val inline: Boolean
)

data class MailMessageSnapshot(
    val envelope: MailEnvelope,
    val plainText: String?,
    val htmlText: String?,
    val attachments: List<MailAttachmentMeta>
)

interface MailReader {
    suspend fun listFolders(accountId: String): List<MailFolderRef>

    /**
     * Lists at most [limit] recent messages. The caller is responsible for local deduplication.
     * Implementations must not mutate Seen/Answered/Deleted flags.
     */
    suspend fun listNewMessages(
        folder: MailFolderRef,
        since: Instant?,
        limit: Int = 200
    ): List<MailEnvelope>

    suspend fun loadMessage(
        folder: MailFolderRef,
        stableId: String
    ): MailMessageSnapshot
}

data class MailReplyDraft(
    val sourceStableId: String,
    val recipient: String,
    val subject: String,
    val plainTextBody: String
)

interface MailSender {
    suspend fun sendReply(draft: MailReplyDraft): String
}

interface OAuthTokenProvider {
    suspend fun accessToken(accountId: String): String
}
