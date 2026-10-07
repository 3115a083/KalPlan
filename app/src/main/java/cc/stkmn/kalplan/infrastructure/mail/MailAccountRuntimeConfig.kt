package cc.stkmn.kalplan.infrastructure.mail

data class MailAccountRuntimeConfig(
    val accountId: String,
    val username: String,
    val incoming: MailServerConfig,
    val outgoing: MailServerConfig
)

interface MailAccountConfigProvider {
    suspend fun account(accountId: String): MailAccountRuntimeConfig
}

interface MailCredentialProvider {
    /**
     * Returns either a password/app-password or a short-lived OAuth access token.
     * Implementations must never log the returned value.
     */
    suspend fun credential(accountId: String, authMode: MailAuthMode): String
}
