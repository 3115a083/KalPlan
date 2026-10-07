package cc.stkmn.kalplan.infrastructure.mail

import cc.stkmn.kalplan.data.AppRepository
import cc.stkmn.kalplan.data.MailAccount

fun MailAccount.runtime(): MailAccountRuntimeConfig {
    fun server(host: String, port: Int, startTls: Boolean) = MailServerConfig(host, port,
        if (startTls) MailTlsMode.STARTTLS else MailTlsMode.IMPLICIT_TLS, MailAuthMode.PASSWORD)
    return MailAccountRuntimeConfig(id, username, server(imapHost, imapPort, imapStartTls), server(smtpHost, smtpPort, smtpStartTls))
}
class AccountProviders(private val repository: AppRepository) : MailAccountConfigProvider, MailCredentialProvider {
    override suspend fun account(accountId: String): MailAccountRuntimeConfig = repository.data.value.accounts.first { it.id == accountId }.runtime()
    override suspend fun credential(accountId: String, authMode: MailAuthMode): String {
        require(authMode == MailAuthMode.PASSWORD) { "OAuth setup not available" }
        return repository.secret(accountId)
    }
}
