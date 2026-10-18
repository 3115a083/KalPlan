package cc.stkmn.kalplan.infrastructure.mail

import cc.stkmn.kalplan.data.MailAccount
import org.junit.Assert.*
import org.junit.Test

class MailSetupTest {
    private val account = MailAccount("id", "", " login ", "", " imap.example.org ", smtpHost = "", smtpEnabled = false)
    @Test fun receiveOnlyDoesNotRequireFromAddressOrSmtpConfiguration() {
        val normalized = MailSetup.normalized(account)
        assertEquals("login", normalized.username)
        assertEquals("imap.example.org", normalized.imapHost)
        assertEquals("login", normalized.name)
        assertEquals("", normalized.smtpHost)
    }
    @Test fun sendingStillRequiresValidReplyAddressAndSmtpHost() {
        assertThrows(IllegalArgumentException::class.java) { MailSetup.normalized(account.copy(smtpEnabled = true)) }
    }
    @Test fun sessionProvidesImapAndSmtpWithoutDisablingTlsValidation() {
        val config = MailServerConfig("localhost", 993, MailTlsMode.IMPLICIT_TLS, MailAuthMode.PASSWORD)
        val incoming = AngusSessionFactory.imap(config)
        assertEquals("org.eclipse.angus.mail.imap.IMAPStore", incoming.getStore("imap").javaClass.name)
        assertEquals("true", incoming.getProperty("mail.imap.ssl.checkserveridentity"))
        assertEquals("true", incoming.getProperty("mail.imap.peek"))
        val smtp = AngusSessionFactory.smtp(config.copy(port = 587, tlsMode = MailTlsMode.STARTTLS))
        assertEquals("true", smtp.getProperty("mail.smtp.starttls.required"))
        assertNotNull(smtp.getTransport("smtp"))
    }
    @Test fun malformedHostsAreRejectedAndErrorsDoNotExposeSecrets() {
        assertThrows(IllegalArgumentException::class.java) { MailSetup.normalized(account.copy(imapHost = "https://mail.example.org")) }
        assertEquals("mail_auth_failed", MailSetup.failureCode(jakarta.mail.AuthenticationFailedException("secret-password")))
    }
}
