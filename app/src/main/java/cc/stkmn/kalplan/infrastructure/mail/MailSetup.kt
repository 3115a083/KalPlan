package cc.stkmn.kalplan.infrastructure.mail

import cc.stkmn.kalplan.data.MailAccount
import cc.stkmn.kalplan.infrastructure.reply.ReplyPolicy
import jakarta.mail.AuthenticationFailedException
import jakarta.mail.MessagingException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object MailSetup {
    fun normalized(account: MailAccount): MailAccount {
        val username = account.username.trim().ifBlank { account.address.trim() }
        require(username.isNotBlank()) { "mail_username_missing" }
        fun host(value: String) = value.trim().lowercase(java.util.Locale.ROOT).also {
            require(it.length in 1..253 && !it.any { c -> c.isWhitespace() || c in "/:@\\\\" }) { "mail_host_invalid" }
        }
        require(account.imapPort in 1..65535 && (!account.smtpEnabled || account.smtpPort in 1..65535)) { "mail_port_invalid" }
        val from = account.address.trim().ifBlank { runCatching { ReplyPolicy.address(username) }.getOrDefault("") }
        if (account.smtpEnabled) ReplyPolicy.address(from)
        return account.copy(name = account.name.trim().ifBlank { username }, username = username, address = from,
            imapHost = host(account.imapHost), smtpHost = if (account.smtpEnabled) host(account.smtpHost) else account.smtpHost.trim(),
            folders = account.folders.map { it.trim() }.filter(String::isNotBlank).distinct().also { require(it.isNotEmpty() && it.size <= 20) { "mail_folders_invalid" } })
    }
    /** Never expose server messages, addresses or credentials in connection errors. */
    fun failureCode(error: Throwable): String {
        val causes = generateSequence(error) { it.cause }.take(12).toList() +
            ((error as? MessagingException)?.nextException?.let { listOf(it) } ?: emptyList())
        return when {
            causes.any { it is AuthenticationFailedException } -> "mail_auth_failed"
            causes.any { it is UnknownHostException } -> "mail_dns_failed"
            causes.any { it is SSLException } -> "mail_tls_failed"
            causes.any { it is SocketTimeoutException } -> "mail_timeout"
            causes.any { it is ConnectException } -> "mail_connection_failed"
            else -> "mail_connection_failed"
        }
    }
}
