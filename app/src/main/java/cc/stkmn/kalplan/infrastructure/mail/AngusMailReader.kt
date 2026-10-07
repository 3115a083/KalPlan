package cc.stkmn.kalplan.infrastructure.mail

import cc.stkmn.kalplan.domain.port.MailEnvelope
import cc.stkmn.kalplan.domain.port.MailFolderRef
import cc.stkmn.kalplan.domain.port.MailMessageSnapshot
import cc.stkmn.kalplan.domain.port.MailReader
import jakarta.mail.FetchProfile
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Store
import jakarta.mail.UIDFolder
import jakarta.mail.internet.InternetAddress
import jakarta.mail.search.ComparisonTerm
import jakarta.mail.search.ReceivedDateTerm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Date

class AngusMailReader(
    private val accountConfigProvider: MailAccountConfigProvider,
    private val credentialProvider: MailCredentialProvider,
    private val mimeTextExtractor: MimeTextExtractor = MimeTextExtractor()
) : MailReader {

    override suspend fun listFolders(accountId: String): List<MailFolderRef> =
        withConnectedStore(accountId) { store ->
            store.defaultFolder
                .list("*")
                .asSequence()
                .filter { (it.type and Folder.HOLDS_MESSAGES) != 0 }
                .map { MailFolderRef(accountId = accountId, path = it.fullName) }
                .sortedBy { it.path.lowercase() }
                .toList()
        }

    override suspend fun listNewMessages(
        folder: MailFolderRef,
        since: Instant?,
        limit: Int
    ): List<MailEnvelope> {
        require(limit in 1..2_000) { "limit must be between 1 and 2000" }

        return withConnectedStore(folder.accountId) { store ->
            val mailFolder = store.getFolder(folder.path)
            mailFolder.open(Folder.READ_ONLY)
            try {
                val uidFolder = mailFolder as? UIDFolder
                    ?: error("Configured IMAP folder does not expose stable UIDs")
                val uidValidity = uidFolder.uidValidity

                val candidates = if (since == null) {
                    val count = mailFolder.messageCount
                    if (count <= 0) {
                        emptyArray()
                    } else {
                        val first = (count - limit + 1).coerceAtLeast(1)
                        mailFolder.getMessages(first, count)
                    }
                } else {
                    mailFolder.search(
                        ReceivedDateTerm(
                            ComparisonTerm.GE,
                            Date.from(since)
                        )
                    ).takeLast(limit).toTypedArray()
                }

                prefetch(mailFolder, candidates)
                candidates
                    .map { it.toEnvelope(folder, uidFolder, uidValidity) }
                    .sortedByDescending { it.receivedAt }
            } finally {
                if (mailFolder.isOpen) mailFolder.close(false)
            }
        }
    }

    override suspend fun loadMessage(
        folder: MailFolderRef,
        stableId: String
    ): MailMessageSnapshot {
        val key = StableImapKey.parse(stableId)
        require(key.accountId == folder.accountId) { "Message account mismatch" }
        require(key.folderPath == folder.path) { "Message folder mismatch" }

        return withConnectedStore(folder.accountId) { store ->
            val mailFolder = store.getFolder(folder.path)
            mailFolder.open(Folder.READ_ONLY)
            try {
                val uidFolder = mailFolder as? UIDFolder
                    ?: error("Configured IMAP folder does not expose stable UIDs")
                require(uidFolder.uidValidity == key.uidValidity) {
                    "Folder UIDVALIDITY changed; cached message key is stale"
                }

                val message = uidFolder.getMessageByUID(key.uid)
                    ?: error("Message no longer exists on server")
                val envelope = message.toEnvelope(folder, uidFolder, key.uidValidity)
                val content = mimeTextExtractor.extract(message)

                MailMessageSnapshot(
                    envelope = envelope,
                    plainText = content.plainText,
                    htmlText = content.htmlText,
                    attachments = content.attachments
                )
            } finally {
                if (mailFolder.isOpen) mailFolder.close(false)
            }
        }
    }

    private suspend fun <T> withConnectedStore(
        accountId: String,
        block: (Store) -> T
    ): T = withContext(Dispatchers.IO) {
        val account = accountConfigProvider.account(accountId)
        val credential = credentialProvider.credential(accountId, account.incoming.authMode)
        val store = AngusSessionFactory.imap(account.incoming).getStore("imap")
        try {
            store.connect(
                account.incoming.host,
                account.incoming.port,
                account.username,
                credential
            )
            block(store)
        } finally {
            runCatching { if (store.isConnected) store.close() }
        }
    }

    private fun prefetch(folder: Folder, messages: Array<Message>) {
        if (messages.isEmpty()) return
        val profile = FetchProfile().apply {
            add(FetchProfile.Item.ENVELOPE)
            add(FetchProfile.Item.CONTENT_INFO)
            add(UIDFolder.FetchProfileItem.UID)
        }
        folder.fetch(messages, profile)
    }

    private fun Message.toEnvelope(
        folder: MailFolderRef,
        uidFolder: UIDFolder,
        uidValidity: Long
    ): MailEnvelope {
        val uid = uidFolder.getUID(this)
        require(uid > 0) { "Server returned invalid message UID" }

        val sender = from
            ?.firstOrNull()
            ?.let { address ->
                (address as? InternetAddress)?.address ?: address.toString()
            }
            .orEmpty()

        val received = receivedDate ?: sentDate ?: Date(0)

        return MailEnvelope(
            stableId = StableImapKey(
                accountId = folder.accountId,
                folderPath = folder.path,
                uidValidity = uidValidity,
                uid = uid
            ).encode(),
            messageId = getHeader("Message-ID")?.firstOrNull(),
            sender = sender,
            subject = subject.orEmpty(),
            receivedAt = received.toInstant()
        )
    }
}

internal data class StableImapKey(
    val accountId: String,
    val folderPath: String,
    val uidValidity: Long,
    val uid: Long
) {
    fun encode(): String = listOf(
        accountId,
        folderPath,
        uidValidity.toString(),
        uid.toString()
    ).joinToString("|") { it.replace("%", "%25").replace("|", "%7C") }

    companion object {
        fun parse(value: String): StableImapKey {
            val parts = value.split("|")
            require(parts.size == 4) { "Invalid stable IMAP key" }
            fun decode(text: String) = text.replace("%7C", "|").replace("%25", "%")
            return StableImapKey(
                accountId = decode(parts[0]),
                folderPath = decode(parts[1]),
                uidValidity = decode(parts[2]).toLong(),
                uid = decode(parts[3]).toLong()
            )
        }
    }
}
