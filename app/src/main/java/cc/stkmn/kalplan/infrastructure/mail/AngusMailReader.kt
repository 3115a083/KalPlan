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
    private val credentialProvider: MailCredentialProvider
) : MailReader {
    private val mimeTextExtractor = MimeTextExtractor()

    data class IncrementalBatch(val envelopes: List<MailEnvelope>, val cursor: String, val hasMore: Boolean)

    suspend fun listIncremental(folder: MailFolderRef, cursor: String?, limit: Int): IncrementalBatch =
        withConnectedStore(folder.accountId) { store ->
            require(limit in 1..200)
            val mailFolder = store.getFolder(folder.path)
            mailFolder.open(Folder.READ_ONLY)
            try {
                val uids = mailFolder as? UIDFolder ?: error("UID folder required")
                val validity = uids.uidValidity
                val old = cursor?.split(':')
                val last = if (old?.getOrNull(0)?.toLongOrNull() == validity) old.getOrNull(1)?.toLongOrNull() else null
                val candidates = if (last == null) {
                    val count = mailFolder.messageCount
                    if (count == 0) emptyArray() else mailFolder.getMessages((count - limit + 1).coerceAtLeast(1), count)
                } else {
                    // A bounded UID window avoids materializing the entire mailbox. Empty windows are advanced too.
                    uids.getMessagesByUID(last + 1, last + limit)
                }
                prefetch(mailFolder, candidates)
                val envelopes = candidates.map { it.toEnvelope(folder, uids, validity) }
                val nextUid = (mailFolder as? org.eclipse.angus.mail.imap.IMAPFolder)?.uidNext ?: -1L
                val checkpoint = if (last == null) candidates.maxOfOrNull { uids.getUID(it) } ?: 0L
                    else minOf(last + limit, if (nextUid > 0) maxOf(last, nextUid - 1) else last + limit)
                IncrementalBatch(envelopes, "$validity:$checkpoint", nextUid > checkpoint + 1)
            } finally { if (mailFolder.isOpen) mailFolder.close(false) }
        }

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

    data class BatchMessage(val envelope: MailEnvelope, val snapshot: MailMessageSnapshot?, val exceededLimits: Boolean = false)
    suspend fun loadBatch(folder: MailFolderRef, envelopes: List<MailEnvelope>): List<BatchMessage> =
        withConnectedStore(folder.accountId) { store ->
            require(envelopes.size <= 200)
            val mailFolder = store.getFolder(folder.path)
            mailFolder.open(Folder.READ_ONLY)
            try {
                val uids = mailFolder as? UIDFolder ?: error("UID folder required")
                val keys = envelopes.map { StableImapKey.parse(it.stableId) }
                require(keys.all { it.accountId == folder.accountId && it.folderPath == folder.path && it.uidValidity == uids.uidValidity })
                val messages = uids.getMessagesByUID(keys.map { it.uid }.toLongArray())
                prefetch(mailFolder, messages)
                val byUid = messages.associateBy { uids.getUID(it) }
                envelopes.mapIndexed { index, envelope ->
                    val message = byUid[keys[index].uid] ?: error("Source changed during sync")
                    try {
                        val content = mimeTextExtractor.extract(message)
                        BatchMessage(envelope, MailMessageSnapshot(envelope, content.plainText, content.htmlText, content.attachments))
                    } catch (_: MimeLimitException) { BatchMessage(envelope, null, true) }
                }
            } finally { if (mailFolder.isOpen) mailFolder.close(false) }
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
                prefetch(mailFolder, arrayOf(message))
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

    suspend fun downloadAttachment(folder: MailFolderRef, stableId: String, path: String, output: java.io.File): Unit =
        withConnectedStore(folder.accountId) { store ->
            val key = StableImapKey.parse(stableId)
            require(key.accountId == folder.accountId && key.folderPath == folder.path)
            val segments = if (path.isBlank()) emptyList() else path.split('/').map { it.toInt() }
            require(segments.size <= 16 && segments.all { it in 0..199 })
            val mailFolder = store.getFolder(folder.path)
            mailFolder.open(Folder.READ_ONLY)
            try {
                val uids = mailFolder as? UIDFolder ?: error("UID folder required")
                require(uids.uidValidity == key.uidValidity)
                var part: jakarta.mail.Part = uids.getMessageByUID(key.uid) ?: error("Source message unavailable")
                MimeTextExtractor.validateWireSize(part)
                for (index in segments) {
                    val multipart = part.content as? jakarta.mail.Multipart ?: error("Attachment structure changed")
                    require(index < multipart.count)
                    part = multipart.getBodyPart(index)
                }
                require(part.size <= 10 * 1024 * 1024) { "Attachment size limit" }
                part.inputStream.use { input ->
                    output.outputStream().use { target ->
                        var count = 0L
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            count += n; require(count <= 10 * 1024 * 1024) { "Attachment size limit" }
                            target.write(buffer, 0, n)
                        }
                    }
                }
            } catch (_: StackOverflowError) { output.delete(); throw MimeLimitException("Provider MIME recursion limit") }
            catch (error: Exception) { output.delete(); throw error }
            finally { if (mailFolder.isOpen) mailFolder.close(false) }
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
        val sizes = FetchProfile().apply {
            add(FetchProfile.Item.SIZE)
            add(UIDFolder.FetchProfileItem.UID)
        }
        folder.fetch(messages, sizes)
        val safe = messages.filter { it.size in 0..MimeTextExtractor.MAX_WIRE_BYTES }.toTypedArray()
        if (safe.isNotEmpty()) folder.fetch(safe, FetchProfile().apply {
            add(FetchProfile.Item.ENVELOPE)
            add("Message-ID")
        })
        // Do not eagerly fetch BODYSTRUCTURE. Bound each message before the provider parser runs.
    }

    private fun Message.toEnvelope(
        folder: MailFolderRef,
        uidFolder: UIDFolder,
        uidValidity: Long
    ): MailEnvelope {
        val uid = uidFolder.getUID(this)
        require(uid > 0) { "Server returned invalid message UID" }

        if (size !in 0..MimeTextExtractor.MAX_WIRE_BYTES) return MailEnvelope(
            StableImapKey(folder.accountId, folder.path, uidValidity, uid).encode(), null, "",
            "Mail exceeds safe import size", Instant.EPOCH)

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
            messageId = getHeader("Message-ID")?.firstOrNull()?.takeIf { it.length <= 998 },
            sender = sender.take(512),
            subject = subject.orEmpty().take(2000),
            receivedAt = received.toInstant(),
            replyTo = replyTo?.firstOrNull()?.let { (it as? InternetAddress)?.address?.take(512) }
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

