package cc.stkmn.kalplan.infrastructure.oauth

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import cc.stkmn.kalplan.data.AppRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Private account setup activity. AppAuth handles browser callback, state and PKCE. */
class OAuthActivity : ComponentActivity() {
    private val repository by lazy { AppRepository.get(this) }
    private val service by lazy { OAuthAccess.service(this) }
    private val result = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
        lifecycleScope.launch {
            try {
                repository.load()
                val accountId = requireNotNull(intent.getStringExtra("account_id"))
                val data = activityResult.data ?: error("OAuth cancelled")
                val response = AuthorizationResponse.fromIntent(data) ?: error("OAuth authorization failed")
                val expected = repository.oauthPending(accountId) ?: error("No pending authorization")
                val request = AuthorizationRequest.jsonDeserialize(expected)
                require(response.state == request.state && response.request.jsonSerializeString() == expected) { "OAuth request mismatch" }
                val state = AuthState(response, AuthorizationException.fromIntent(data))
                val token = suspendCancellableCoroutine<TokenResponse> { continuation ->
                    service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, exception ->
                        if (continuation.isActive) {
                            if (tokenResponse == null || exception != null) continuation.resumeWithException(IllegalStateException("OAuth token exchange failed"))
                            else continuation.resume(tokenResponse)
                        }
                    }
                }
                state.update(token, null)
                repository.saveOAuthState(accountId, state.jsonSerializeString())
                repository.clearOAuthPending(accountId)
                Toast.makeText(this@OAuthActivity, "OAuth verbunden / OAuth connected", Toast.LENGTH_LONG).show()
            } catch (_: Exception) { Toast.makeText(this@OAuthActivity, "OAuth fehlgeschlagen / OAuth failed", Toast.LENGTH_LONG).show() }
            finish()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        lifecycleScope.launch {
            try {
                repository.load()
                val accountId = requireNotNull(intent.getStringExtra("account_id"))
                val account = repository.data.value.accounts.first { it.id == accountId && it.authMode == "XOAUTH2" }
                require(account.oauthClientId.isNotBlank() && account.oauthScope.isNotBlank())
                val configuration = AuthorizationServiceConfiguration(OAuthAccess.httpsEndpoint(account.oauthAuthorizationEndpoint), OAuthAccess.httpsEndpoint(account.oauthTokenEndpoint))
                val request = AuthorizationRequest.Builder(configuration, account.oauthClientId, ResponseTypeValues.CODE, android.net.Uri.parse(OAuthAccess.REDIRECT))
                    .setScope(account.oauthScope).setLoginHint(account.username).build()
                require(request.codeVerifier != null && request.codeVerifierChallengeMethod == "S256")
                repository.saveOAuthPending(accountId, request.jsonSerializeString())
                result.launch(service.getAuthorizationRequestIntent(request))
            } catch (_: Exception) { Toast.makeText(this@OAuthActivity, "OAuth-Konfiguration prüfen / Check OAuth setup", Toast.LENGTH_LONG).show(); finish() }
        }
    }
    override fun onDestroy() { service.dispose(); super.onDestroy() }
}
