package cc.stkmn.kalplan.infrastructure.oauth

import android.content.Context
import android.net.Uri
import cc.stkmn.kalplan.data.AppRepository
import net.openid.appauth.*
import net.openid.appauth.connectivity.ConnectionBuilder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OAuthAccess {
    private val tokenMutex = Mutex()
    const val REDIRECT = "cc.stkmn.kalplan:/oauth2redirect"
    fun httpsEndpoint(value: String): Uri {
        val uri = Uri.parse(value)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null)
        return uri
    }
    fun service(context: Context): AuthorizationService = AuthorizationService(context,
        AppAuthConfiguration.Builder().setConnectionBuilder(ConnectionBuilder { uri ->
            httpsEndpoint(uri.toString())
            (URL(uri.toString()).openConnection() as HttpsURLConnection).apply {
                connectTimeout = 8000; readTimeout = 15000; instanceFollowRedirects = false
            }
        }).build())
    suspend fun accessToken(context: Context, repository: AppRepository, accountId: String): String = tokenMutex.withLock {
        val saved = repository.oauthState(accountId) ?: error("OAuth sign-in required")
        val state = AuthState.jsonDeserialize(saved)
        val account = repository.data.value.accounts.first { it.id == accountId }
        require(state.authorizationServiceConfiguration?.tokenEndpoint?.toString() == account.oauthTokenEndpoint)
        val service = service(context)
        try {
            val token = suspendCancellableCoroutine<String> { continuation ->
                state.performActionWithFreshTokens(service) { accessToken, _, error ->
                    if (continuation.isActive) {
                        if (error != null || accessToken.isNullOrBlank()) continuation.resumeWithException(IllegalStateException("OAuth refresh failed"))
                        else continuation.resume(accessToken)
                    }
                }
            }
            repository.saveOAuthState(accountId, state.jsonSerializeString())
            token
        } finally { service.dispose() }
    }
}
