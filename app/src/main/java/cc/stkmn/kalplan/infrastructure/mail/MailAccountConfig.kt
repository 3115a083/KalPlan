package cc.stkmn.kalplan.infrastructure.mail

enum class MailTlsMode {
    IMPLICIT_TLS,
    STARTTLS
}

enum class MailAuthMode {
    PASSWORD,
    XOAUTH2
}

data class MailServerConfig(
    val host: String,
    val port: Int,
    val tlsMode: MailTlsMode,
    val authMode: MailAuthMode
)
