package cc.stkmn.kalplan.infrastructure.backup

import cc.stkmn.kalplan.data.*
import org.junit.Assert.*
import org.junit.Test

class PortableBackupTest {
    private val account = MailAccount("account", "Test", "test@example.org", "test@example.org", "imap.example.org", smtpHost = "")
    private val data = AppData(accounts = listOf(account), settings = Settings(originAddress = "Test place", labels = listOf(LabelPolicy("Test"))))
    private val snapshot = BackupSnapshot(createdMillis = 1, data = data, credentials = mapOf("secret_account_imap" to "private-pass"))
    private val password = "long-test-password".toCharArray()
    @Test fun encryptedRoundTripKeepsSettingsAndCredentials() {
        val encoded = PortableBackup.encode(snapshot, password)
        assertEquals(snapshot, PortableBackup.decode(encoded, password))
        assertFalse(encoded.toString(Charsets.ISO_8859_1).contains("private-pass"))
    }
    @Test fun independentExportsUseDifferentSaltAndNonce() {
        assertFalse(PortableBackup.encode(snapshot, password).contentEquals(PortableBackup.encode(snapshot, password)))
    }
    @Test fun wrongPasswordAndTamperingAreRejected() {
        val encoded = PortableBackup.encode(snapshot, password)
        assertThrows(Exception::class.java) { PortableBackup.decode(encoded, "wrong-password".toCharArray()) }
        encoded[encoded.lastIndex] = (encoded.last().toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { PortableBackup.decode(encoded, password) }
    }
    @Test fun unsupportedVersionAndCredentialPathsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { PortableBackup.validate(snapshot.copy(version = 2)) }
        assertThrows(IllegalArgumentException::class.java) { PortableBackup.validate(snapshot.copy(credentials = mapOf("../data" to "x"))) }
    }
    @Test fun restoreDoesNotStartNetworkOrReuseDeviceCalendarIds() {
        val restored = PortableBackup.forRestore(snapshot.copy(data = data.copy(calendars = listOf(CalendarPrivacy("42", included = true)), settings = data.settings.copy(syncMinutes = 5, reservationCalendarId = "42", debugSendToTest = true, addressSuggestionsEnabled = true))))
        assertEquals(0, restored.data.settings.syncMinutes)
        assertFalse(restored.data.accounts.single().enabled)
        assertTrue(restored.data.calendars.isEmpty())
        assertEquals("", restored.data.settings.reservationCalendarId)
        assertFalse(restored.data.settings.debugSendToTest)
        assertFalse(restored.data.settings.addressSuggestionsEnabled)
        assertEquals("private-pass", restored.credentials["secret_account_imap"])
    }
    @Test fun interruptedSendIsNeverMadeRetryableByRestore() {
        val r = StoredRequest("r", sender = "", subject = "", body = "", receivedMillis = 0, status = "SENDING")
        assertEquals("DELIVERY_UNKNOWN", PortableBackup.forRestore(snapshot.copy(data = data.copy(requests = listOf(r)))).data.requests.single().status)
    }
    @Test fun invalidAndOversizedFilesFailBeforePasswordWork() {
        assertThrows(IllegalArgumentException::class.java) { PortableBackup.decode(ByteArray(0), password) }
        assertThrows(IllegalArgumentException::class.java) { PortableBackup.decode(ByteArray(PortableBackup.MAX_BYTES + 1), password) }
    }
}
