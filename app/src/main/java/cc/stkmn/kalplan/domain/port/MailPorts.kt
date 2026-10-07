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

data class MailReplyDraft(
    val sourceStableId: String,
    val recipient: String,
    val subject: String,
    val plainTextBody: String
)

interface MailReader {
    suspend fun listFolders(accountId: String): List<MailFolderRef>
    suspend fun listNewMessages(folder: MailFolderRef, since: Instant?): List<MailEnvelope>
}

interface MailSender {
    suspend fun sendReply(draft: MailReplyDraft): String
}

interface OAuthTokenProvider {
    suspend fun accessToken(accountId: String): String
}
