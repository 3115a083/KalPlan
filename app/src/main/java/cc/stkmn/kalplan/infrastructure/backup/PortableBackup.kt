package cc.stkmn.kalplan.infrastructure.backup

import cc.stkmn.kalplan.data.*
import cc.stkmn.kalplan.extraction.ProfileValidator
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class BackupSnapshot(val version: Int = 1, val createdMillis: Long = System.currentTimeMillis(),
    val data: AppData, val credentials: Map<String, String> = emptyMap())

/** Portable password encryption uses platform PBKDF2 and authenticated AES-GCM, never the device key. */
object PortableBackup {
    const val MAX_BYTES = 24 * 1024 * 1024
    private val magic = "KALPBACK1".toByteArray(Charsets.US_ASCII)
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    fun encode(snapshot: BackupSnapshot, password: CharArray): ByteArray {
        require(password.size in 10..1024) { "Use at least 10 password characters" }
        validate(snapshot)
        val plain = json.encodeToString(snapshot).toByteArray(Charsets.UTF_8)
        require(plain.size <= MAX_BYTES - 100) { "Backup exceeds size limit" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
            cipher.updateAAD(magic)
            magic + salt + nonce + cipher.doFinal(plain)
        } finally { plain.fill(0) }
    }
    fun decode(bytes: ByteArray, password: CharArray): BackupSnapshot {
        require(bytes.size in (magic.size + 44)..MAX_BYTES && bytes.take(magic.size).toByteArray().contentEquals(magic)) { "Invalid or unsupported backup" }
        require(password.size in 10..1024)
        val salt = bytes.copyOfRange(magic.size, magic.size + 16)
        val nonce = bytes.copyOfRange(magic.size + 16, magic.size + 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD(magic)
        val plain = cipher.doFinal(bytes.copyOfRange(magic.size + 28, bytes.size))
        return try { json.decodeFromString<BackupSnapshot>(plain.toString(Charsets.UTF_8)).also(::validate) }
        finally { plain.fill(0) }
    }
    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 600_000, 256)
        val bytes = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        return try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
    }
    fun allowedCredentialKeys(data: AppData): Set<String> = data.accounts.flatMap { listOf("secret_${it.id}_imap", "secret_${it.id}_smtp", "oauth_${it.id}") }.toSet() +
        listOf("GOOGLE", "HERE", "TOMTOM", "ORS", "GRAPHHOPPER").map { "route_$it" }
    fun validate(snapshot: BackupSnapshot) {
        val d = snapshot.data
        require(snapshot.version == 1 && d.schema == 1) { "Unsupported backup version" }
        require(d.settings.routingDailyLimit in 0..10000 && d.settings.addressDailyLimit in 0..1000)
        require(d.routeBudgets.all { (provider, budget) -> provider in setOf("PHOTON", "GOOGLE", "HERE", "TOMTOM", "ORS", "GRAPHHOPPER") && budget.used in 0..1_000_000 && runCatching { java.time.LocalDate.parse(budget.date) }.isSuccess })
        require(d.accounts.size <= 100 && d.profiles.size <= 1000 && d.requests.size <= 100_000)
        require(d.accounts.map { it.id }.distinct().size == d.accounts.size && d.requests.map { it.id }.distinct().size == d.requests.size)
        require(d.accounts.all { it.id.matches(Regex("[a-zA-Z0-9_-]{1,60}")) && it.folders.size <= 20 && it.imapPort in 1..65535 && it.smtpPort in 1..65535 })
        require(d.profiles.all { ProfileValidator().validate(it).isEmpty() })
        require(d.settings.defaultDuration in 1..10080 && d.settings.beforeBuffer in 0..1440 && d.settings.afterBuffer in 0..1440)
        require(d.settings.syncMinutes == 0 || d.settings.syncMinutes in 5..1440)
        require(d.settings.labels.size <= 1000 && d.settings.labels.all { it.conditionGroups.size <= 20 && it.conditionGroups.all { g -> g.conditions.size <= 20 } })
        require(d.requests.all { r -> r.candidates.all { c -> c.durationMinutes in 1..10080 && (c.startMillis == null || c.endMillis == null || c.endMillis > c.startMillis) } })
        require(snapshot.credentials.keys.all { it in allowedCredentialKeys(d) } && snapshot.credentials.values.all { it.length <= 1_000_000 })
        require(d.settings.flatFeePresets.all { p -> p.flatCents in 0..100_000_000 && p.appliesTo in setOf("ORDER", "WORK", "TRAVEL") && p.distanceBands.all { it.upToKm.isFinite() && it.upToKm >= 0 && it.cents in 0..100_000_000 } })
    }
    /** Cross-device calendar IDs/URI grants are not portable. Account connections resume only after review. */
    fun forRestore(snapshot: BackupSnapshot): BackupSnapshot = snapshot.copy(data = snapshot.data.copy(
        accounts = snapshot.data.accounts.map { it.copy(enabled = false, signatureAssets = emptyList()) },
        calendars = emptyList(), cursors = emptyMap(),
        settings = snapshot.data.settings.copy(syncMinutes = 0, reservationCalendarId = "", debugSendToTest = false, addressSuggestionsEnabled = false),
        requests = snapshot.data.requests.map { r -> r.copy(reservationEventId = null,
            status = if (r.status == "SENDING") "DELIVERY_UNKNOWN" else r.status,
            routeCheckedMillis = null, travelAfterCheckedMillis = null) }
    ))
}
