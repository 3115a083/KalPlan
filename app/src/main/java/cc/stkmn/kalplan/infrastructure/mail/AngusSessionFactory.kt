package cc.stkmn.kalplan.infrastructure.mail

import jakarta.mail.Session
import java.util.Properties

object AngusSessionFactory {
    fun imap(config: MailServerConfig): Session {
        val prefix = "mail.imap"
        return Session.getInstance(baseProperties(prefix, config).apply {
            put("mail.store.protocol", "imap")
            put("$prefix.peek", "true")
            configureAuth(prefix, config.authMode)
        })
    }

    fun smtp(config: MailServerConfig): Session {
        val prefix = "mail.smtp"
        return Session.getInstance(baseProperties(prefix, config).apply {
            put("mail.transport.protocol", "smtp")
            put("$prefix.auth", "true")
            configureAuth(prefix, config.authMode)
        })
    }

    private fun baseProperties(prefix: String, config: MailServerConfig): Properties =
        Properties().apply {
            require(config.host.isNotBlank() && config.host.length <= 253 && !config.host.any { it.isWhitespace() || it == '/' }) { "Invalid mail host" }
            require(config.port in 1..65535) { "Invalid port" }
            put("$prefix.ssl.protocols", "TLSv1.2 TLSv1.3")
            put("$prefix.host", config.host)
            put("$prefix.port", config.port.toString())
            put("$prefix.connectiontimeout", "10000")
            put("$prefix.timeout", "15000")
            put("$prefix.writetimeout", "15000")
            put("$prefix.ssl.checkserveridentity", "true")
            put("$prefix.auth.ntlm.disable", "true")

            when (config.tlsMode) {
                MailTlsMode.IMPLICIT_TLS -> {
                    put("$prefix.ssl.enable", "true")
                    put("$prefix.starttls.enable", "false")
                }
                MailTlsMode.STARTTLS -> {
                    put("$prefix.ssl.enable", "false")
                    put("$prefix.starttls.enable", "true")
                    put("$prefix.starttls.required", "true")
                }
            }
        }

    private fun Properties.configureAuth(prefix: String, authMode: MailAuthMode) {
        when (authMode) {
            MailAuthMode.PASSWORD -> {
                put("$prefix.auth.mechanisms", "PLAIN LOGIN")
                put("$prefix.auth.xoauth2.disable", "true")
            }
            MailAuthMode.XOAUTH2 -> {
                put("$prefix.auth.mechanisms", "XOAUTH2")
                put("$prefix.auth.xoauth2.disable", "false")
                put("$prefix.auth.login.disable", "true")
                put("$prefix.auth.plain.disable", "true")
            }
        }
    }
}

