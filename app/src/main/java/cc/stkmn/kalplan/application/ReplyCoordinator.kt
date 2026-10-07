package cc.stkmn.kalplan.application

import android.content.Context
import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.domain.port.*
import cc.stkmn.kalplan.infrastructure.mail.*
import cc.stkmn.kalplan.infrastructure.reply.ReplyPolicy
import cc.stkmn.kalplan.infrastructure.calendar.AndroidReservationWriter
import cc.stkmn.kalplan.infrastructure.widget.RequestSurfaces
import jakarta.mail.Message
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Date

class ReplyCoordinator(private val context: Context, private val repository: AppRepository) {
    /** Invoked only after the review screen and the second confirmation dialog. */
    suspend fun send(id: String, accept: Boolean, body: String, expectedRecipient: String, expected: StoredRequest): String = repository.sendMutex.withLock {
        repository.load()
        val request = repository.data.value.requests.first { it.id == id }
        require(request == expected) { "Request changed. Review again." }
        require(body.isNotBlank() && body.length <= 100_000)
        val settings = repository.data.value.settings
        if (request.demo) return@withLock "demo_no_send"
        if (settings.debug && !settings.debugSendToTest) return@withLock "debug_simulated"
        require(ReplyPolicy.canSend(request)) { "Already handled or no mail account" }
        val recipient = ReplyPolicy.recipient(request, settings)
        require(recipient == expectedRecipient) { "Recipient changed. Review again." }
        val account = repository.data.value.accounts.first { it.id == request.accountId && it.enabled }
        val providers = AccountProviders(repository)
        // Reload source to defend against stale UIDVALIDITY and changed Reply-To/header provenance.
        val source = AngusMailReader(providers, providers).loadMessage(MailFolderRef(account.id, request.folder), request.sourceStableId ?: request.id)
        require(source.envelope.messageId == request.messageId && source.envelope.sender == request.sender)
        if (!settings.debug) require(ReplyPolicy.address(source.envelope.replyTo ?: source.envelope.sender) == recipient)
        val credential = if (account.authMode == "XOAUTH2") cc.stkmn.kalplan.infrastructure.oauth.OAuthAccess.accessToken(context, repository, account.id) else repository.secret(account.id, true)
        val session = AngusSessionFactory.smtp(account.runtime().outgoing)
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(ReplyPolicy.address(account.address)))
            setRecipient(Message.RecipientType.TO, InternetAddress(recipient))
            setSubject(ReplyPolicy.header((if (settings.debug) "[KalPlan TEST] " else "") + "Re: " + request.subject.take(700)), "UTF-8")
            setText(body + if (account.signature.isNotBlank()) "\n\n" + account.signature else "", "UTF-8")
            sentDate = Date()
            ReplyPolicy.messageId(request.messageId)?.let { setHeader("In-Reply-To", it); setHeader("References", it) }
            saveChanges()
        }
        if (accept) {
            require(!request.unclear && request.candidate?.startMillis != null) { "Confirm extracted fields first" }
            val assessment = Planner(context, repository).assess(request)
            require(assessment.status == "FEASIBLE") { "Final calendar check requires fully feasible appointment" }
        }
        // Durable at-most-once fence. An SMTP timeout is delivery-unknown, never auto-retry.
        repository.request(id) { it.copy(status = "SENDING", replyId = message.messageID, failureCode = null) }
        var acknowledged = false
        var attempted = false
        try {
            withContext(Dispatchers.IO) {
                session.getTransport("smtp").use { transport ->
                    transport.connect(account.smtpHost, account.smtpPort, account.username, credential)
                    // Recheck once connected, immediately before the SMTP DATA operation.
                    if (accept) require(Planner(context, repository).assess(request).status == "FEASIBLE") { "Calendar changed before sending" }
                    attempted = true
                    transport.sendMessage(message, message.allRecipients)
                    acknowledged = true
                }
            }
        } catch (error: Exception) {
            if (!acknowledged) {
                if (!attempted) {
                    repository.request(id) { it.copy(status = request.status, replyId = null, failureCode = "send_blocked_before_data") }
                    repository.log("smtp", "blocked_before_data")
                    return@withLock "send_blocked"
                }
                repository.request(id) { it.copy(status = "DELIVERY_UNKNOWN", failureCode = error.javaClass.simpleName) }
                repository.log("smtp", "delivery_unknown")
                RequestSurfaces.updateWidgets(context)
                return@withLock "delivery_unknown"
            }
        }
        repository.request(id) { it.copy(status = if (settings.debug) "TEST_SENT" else if (accept) "WAITING" else "DECLINED") }
        if (accept && !settings.debug && settings.reservationCalendarId.isNotBlank()) {
            try {
                val candidate = requireNotNull(request.candidate)
                val description = when (settings.reservationDescription) {
                    "FULL" -> request.body
                    "EXCLUDE" -> settings.reservationExcludeBlocks.fold(request.body) { text, block -> if (block.isBlank()) text else text.replace(block, "") }
                    "EXCERPTS" -> settings.reservationExcerpts.filter { it.isNotBlank() && request.body.contains(it) }.joinToString("\n")
                    "TEMPLATE" -> settings.reservationTemplate.replace("{id}", request.id).replace("{subject}", request.subject)
                        .replace("{date}", Instant.ofEpochMilli(requireNotNull(candidate.startMillis)).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString())
                        .replace("{time}", Instant.ofEpochMilli(requireNotNull(candidate.startMillis)).atZone(java.time.ZoneId.systemDefault()).toLocalTime().toString())
                    else -> "KalPlan-ID: ${request.id}"
                }
                val eventId = AndroidReservationWriter(context).createReservation(ReservationDraft(settings.reservationCalendarId,
                    Instant.ofEpochMilli(requireNotNull(candidate.startMillis)), Instant.ofEpochMilli(requireNotNull(candidate.endMillis)),
                    "[Reserviert] " + request.labels.firstOrNull().orEmpty().ifBlank { request.subject }.take(120),
                    if (candidate.mode == "ONLINE") settings.originAddress else candidate.location, description.take(100_000)))
                repository.request(id) { it.copy(status = "RESERVED", reservationEventId = eventId) }
            } catch (error: Exception) {
                repository.request(id) { it.copy(status = "RESERVATION_FAILED", failureCode = error.javaClass.simpleName) }
                repository.log("calendar", "reservation_failed")
                RequestSurfaces.updateWidgets(context)
                return@withLock "reservation_failed"
            }
        }
        RequestSurfaces.cancelNotification(context, id)
        RequestSurfaces.updateWidgets(context)
        "reply_sent"
    }
}
