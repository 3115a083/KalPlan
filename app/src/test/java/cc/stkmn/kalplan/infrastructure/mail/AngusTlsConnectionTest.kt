package cc.stkmn.kalplan.infrastructure.mail

import jakarta.mail.AuthenticationFailedException
import jakarta.mail.Folder
import jakarta.mail.MessagingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory

/** Real Angus protocol and JSSE handshakes against loopback only, without provider accounts. */
class AngusTlsConnectionTest {
    @Test fun `implicit TLS authenticates and lists folders without selecting or changing mail`() {
        assertFolderList(MailTlsMode.IMPLICIT_TLS)
    }

    @Test fun `STARTTLS upgrades before authentication and lists folders`() {
        assertFolderList(MailTlsMode.STARTTLS)
    }

    @Test fun `required STARTTLS fails closed when the server does not advertise it`() {
        ImapFixture(MailTlsMode.STARTTLS, advertiseStartTls = false).use { server ->
            val store = session(server, MailTlsMode.STARTTLS).getStore("imap")
            try {
                assertThrows(MessagingException::class.java) {
                    store.connect("localhost", server.port, "fixture-user", "fixture-password")
                }
                assertFalse(server.commands.contains("AUTHENTICATE"))
                assertFalse(server.commands.contains("LOGIN"))
            } finally { if (store.isConnected) store.close() }
        }
    }

    @Test fun `untrusted certificate is rejected before credentials are sent`() {
        ImapFixture(MailTlsMode.IMPLICIT_TLS).use { server ->
            // Production trust configuration, deliberately without the fixture CA.
            val store = AngusSessionFactory.imap(config(server, MailTlsMode.IMPLICIT_TLS)).getStore("imap")
            try {
                val error = assertThrows(MessagingException::class.java) {
                    store.connect("localhost", server.port, "fixture-user", "fixture-password")
                }
                assertEquals("mail_tls_failed", MailSetup.failureCode(error))
                assertFalse(server.commands.contains("AUTHENTICATE"))
            } finally { if (store.isConnected) store.close() }
        }
    }

    @Test fun `trusted certificate for the wrong hostname is rejected`() {
        ImapFixture(MailTlsMode.IMPLICIT_TLS).use { server ->
            // Certificate SAN contains localhost only, never the IP address.
            val store = session(server, MailTlsMode.IMPLICIT_TLS).getStore("imap")
            try {
                assertThrows(MessagingException::class.java) {
                    store.connect("127.0.0.1", server.port, "fixture-user", "fixture-password")
                }
                assertFalse(server.commands.contains("AUTHENTICATE"))
            } finally { if (store.isConnected) store.close() }
        }
    }

    @Test fun `server authentication refusal is classified without exposing its message`() {
        ImapFixture(MailTlsMode.IMPLICIT_TLS, rejectAuthentication = true).use { server ->
            val store = session(server, MailTlsMode.IMPLICIT_TLS).getStore("imap")
            try {
                val error = assertThrows(AuthenticationFailedException::class.java) {
                    store.connect("localhost", server.port, "fixture-user", "fixture-password")
                }
                assertEquals("mail_auth_failed", MailSetup.failureCode(error))
            } finally { if (store.isConnected) store.close() }
        }
    }

    private fun assertFolderList(mode: MailTlsMode) {
        ImapFixture(mode).use { server ->
            val store = session(server, mode).getStore("imap")
            try {
                store.connect("localhost", server.port, "fixture-user", "fixture-password")
                val folders = store.defaultFolder.list("*").filter { it.type and Folder.HOLDS_MESSAGES != 0 }
                assertEquals(listOf("INBOX", "Archive"), folders.map { it.fullName })
            } finally { if (store.isConnected) store.close() }
            server.awaitCompletion()
            assertTrue(server.commands.contains("AUTHENTICATE"))
            assertTrue(server.commands.contains("LIST"))
            assertFalse(server.commands.any { it in setOf("SELECT", "STORE", "EXPUNGE", "APPEND", "DELETE") })
            if (mode == MailTlsMode.STARTTLS) {
                assertTrue(server.commands.indexOf("STARTTLS") < server.commands.indexOf("AUTHENTICATE"))
            }
        }
    }

    private fun config(server: ImapFixture, mode: MailTlsMode) =
        MailServerConfig("localhost", server.port, mode, MailAuthMode.PASSWORD)

    private fun session(server: ImapFixture, mode: MailTlsMode) =
        AngusSessionFactory.imap(config(server, mode)).apply {
            // Trust exactly the test CA. Leave hostname validation and protocol policy intact.
            properties["mail.imap.ssl.socketFactory"] = clientContext().socketFactory
            properties["mail.imap.connectiontimeout"] = "3000"
            properties["mail.imap.timeout"] = "3000"
        }

    private class ImapFixture(
        private val mode: MailTlsMode,
        private val advertiseStartTls: Boolean = true,
        private val rejectAuthentication: Boolean = false
    ) : Closeable {
        private val tls = serverContext()
        private val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "kalplan-local-imap-fixture").apply { isDaemon = true }
        }
        private val listener: ServerSocket = if (mode == MailTlsMode.IMPLICIT_TLS)
            tls.serverSocketFactory.createServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        else ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = listener.localPort
        val commands: MutableList<String> = Collections.synchronizedList(mutableListOf())
        @Volatile private var active: Socket? = null
        private val future = executor.submit {
            listener.accept().use { accepted ->
                active = accepted
                accepted.soTimeout = 4000
                serve(accepted)
            }
        }

        private fun serve(initial: Socket) {
            var socket = initial
            var reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
            var writer = socket.getOutputStream().bufferedWriter(Charsets.US_ASCII)
            fun send(line: String) { writer.write(line + "\r\n"); writer.flush() }
            send("* OK KalPlan loopback fixture")
            while (true) {
                val line = reader.readLine() ?: return
                val parts = line.split(' ', limit = 3)
                val tag = parts[0]
                val command = parts.getOrNull(1)?.uppercase() ?: error("Missing command")
                commands += command // Never retain credentials or command arguments.
                when (command) {
                    "CAPABILITY" -> {
                        val upgrade = if (mode == MailTlsMode.STARTTLS && advertiseStartTls && socket !is SSLSocket) " STARTTLS" else ""
                        send("* CAPABILITY IMAP4rev1 AUTH=PLAIN$upgrade")
                        send("$tag OK capability")
                    }
                    "STARTTLS" -> {
                        send("$tag OK begin TLS")
                        socket = (tls.socketFactory.createSocket(socket, "localhost", port, true) as SSLSocket).apply {
                            useClientMode = false
                            soTimeout = 4000
                            startHandshake()
                        }
                        active = socket
                        reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                        writer = socket.getOutputStream().bufferedWriter(Charsets.US_ASCII)
                    }
                    "AUTHENTICATE" -> {
                        check(socket is SSLSocket) { "Credentials attempted without TLS" }
                        send("+ ")
                        check(reader.readLine() != null) // Discard fixture SASL payload immediately.
                        if (rejectAuthentication) send("$tag NO [AUTHENTICATIONFAILED] fixture refusal")
                        else send("$tag OK authenticated")
                    }
                    "LIST" -> {
                        send("* LIST (\\HasNoChildren) \"/\" \"INBOX\"")
                        send("* LIST (\\HasNoChildren) \"/\" \"Archive\"")
                        send("$tag OK listed")
                    }
                    "NOOP" -> send("$tag OK noop") // Angus checks pooled connections with a read-only keepalive.
                    "LOGOUT" -> { send("* BYE fixture closed"); send("$tag OK logout"); return }
                    else -> error("Unexpected IMAP command: $command")
                }
            }
        }

        fun awaitCompletion() { future.get(5, TimeUnit.SECONDS) }
        override fun close() {
            runCatching { active?.close() }
            listener.close()
            future.cancel(true)
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    companion object {
        private fun resource(name: String) = checkNotNull(AngusTlsConnectionTest::class.java.getResourceAsStream("/$name"))
        private fun serverContext(): SSLContext {
            val password = "fixture-only".toCharArray()
            val keys = KeyStore.getInstance("PKCS12").apply { resource("mail-localhost.p12").use { load(it, password) } }
            val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, password) }
            return SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, null) }
        }
        private fun clientContext(): SSLContext {
            val ca = resource("mail-test-ca.pem").use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
            val trust = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("fixture-ca", ca) }
            val managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }
            return SSLContext.getInstance("TLS").apply { init(null, managers.trustManagers, null) }
        }
    }
}
