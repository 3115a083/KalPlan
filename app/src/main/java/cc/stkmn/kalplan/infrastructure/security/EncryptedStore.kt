package cc.stkmn.kalplan.infrastructure.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** All customer data and credentials live in noBackupFilesDir, encrypted with a device key. */
class EncryptedStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, "vault").apply { mkdirs() }
    private val key: SecretKey by lazy {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    @Synchronized fun read(name: String): String? {
        val file = atomic(name)
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val bytes = file.openRead().use { stream ->
            val limit = 32 * 1024 * 1024
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= limit) { "Encrypted store exceeds limit" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.size >= 29 && bytes[0] == 1.toByte()) { "Unsupported encrypted store" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(bytes.copyOfRange(13, bytes.size)).toString(Charsets.UTF_8)
    }
    @Synchronized fun write(name: String, value: String) {
        require(value.toByteArray(Charsets.UTF_8).size <= 31 * 1024 * 1024) { "Local storage limit reached" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val bytes = byteArrayOf(1) + cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val file = atomic(name)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    @Synchronized fun remove(name: String) { atomic(name).delete() }
    private fun atomic(name: String): AtomicFile {
        require(name.matches(Regex("[a-zA-Z0-9_-]{1,100}")))
        return AtomicFile(File(directory, name))
    }
    companion object { private const val ALIAS = "kalplan-vault-v1" }
}
