package cc.stkmn.kalplan.data

import android.content.Context
import cc.stkmn.kalplan.infrastructure.security.EncryptedStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AppRepository private constructor(val context: Context) {
    private val vault = EncryptedStore(context.applicationContext)
    private val mutex = Mutex()
    val syncMutex = Mutex()
    val sendMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutable = MutableStateFlow(AppData())
    val data = mutable.asStateFlow()
    private var loaded = false
    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!loaded) {
                val value = vault.read("data")?.let { json.decodeFromString<AppData>(it) } ?: AppData()
                require(value.schema == 1) { "Unsupported data version" }
                // A killed process during SMTP must never silently retry a possibly delivered reply.
                val recovered = value.copy(requests = value.requests.map {
                    if (it.status == "SENDING") it.copy(status = "DELIVERY_UNKNOWN", failureCode = "interrupted_send") else it
                })
                if (recovered != value) vault.write("data", json.encodeToString(recovered))
                mutable.value = recovered
                loaded = true
            }
        }
    }
    suspend fun update(change: (AppData) -> AppData) = withContext(Dispatchers.IO) {
        load()
        mutex.withLock {
            val next = change(mutable.value)
            vault.write("data", json.encodeToString(next))
            mutable.value = next
        }
    }
    suspend fun request(id: String, change: (StoredRequest) -> StoredRequest) = update { state ->
        state.copy(requests = state.requests.map { if (it.id == id) change(it) else it })
    }
    suspend fun secret(id: String, outgoing: Boolean = false): String = withContext(Dispatchers.IO) {
        vault.read("secret_" + id + if (outgoing) "_smtp" else "_imap") ?: error("Credentials missing")
    }
    suspend fun saveAccount(account: MailAccount, incomingSecret: String, outgoingSecret: String) {
        withContext(Dispatchers.IO) {
            if (incomingSecret.isNotBlank()) vault.write("secret_" + account.id + "_imap", incomingSecret)
            if (outgoingSecret.isNotBlank()) vault.write("secret_" + account.id + "_smtp", outgoingSecret)
        }
        update { it.copy(accounts = it.accounts.filterNot { old -> old.id == account.id } + account) }
    }
    suspend fun oauthState(id: String): String? = withContext(Dispatchers.IO) { vault.read("oauth_" + id) }
    suspend fun saveOAuthState(id: String, state: String) = withContext(Dispatchers.IO) { vault.write("oauth_" + id, state) }
    suspend fun oauthPending(id: String): String? = withContext(Dispatchers.IO) { vault.read("oauth_pending_" + id) }
    suspend fun saveOAuthPending(id: String, request: String) = withContext(Dispatchers.IO) { vault.write("oauth_pending_" + id, request) }
    suspend fun clearOAuthPending(id: String) = withContext(Dispatchers.IO) { vault.remove("oauth_pending_" + id) }
    suspend fun routingKey(provider: String): String = withContext(Dispatchers.IO) { vault.read("route_" + provider) ?: "" }
    suspend fun saveRoutingKey(provider: String, key: String) = withContext(Dispatchers.IO) { vault.write("route_" + provider, key) }
    suspend fun removeAccount(id: String) {
        update { it.copy(accounts = it.accounts.filterNot { a -> a.id == id }) }
        withContext(Dispatchers.IO) { vault.remove("secret_" + id + "_imap"); vault.remove("secret_" + id + "_smtp"); vault.remove("oauth_" + id); vault.remove("oauth_pending_" + id) }
    }
    suspend fun log(service: String, code: String) = update {
        // Codes only, never exception.message, addresses, hosts, URLs or message text.
        val entry = "${java.time.Instant.now()} ${service.filter { c -> c.isLetterOrDigit() }} ${code.filter { c -> c.isLetterOrDigit() || c == '_' }.take(80)}"
        it.copy(diagnostics = (it.diagnostics + entry).takeLast(100))
    }
    companion object {
        @Volatile private var instance: AppRepository? = null
        fun get(context: Context): AppRepository = instance ?: synchronized(this) {
            instance ?: AppRepository(context).also { instance = it }
        }
    }
}
