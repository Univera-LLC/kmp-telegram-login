package app.univera.telegramlogin

import app.univera.telegramlogin.internal.Pkce
import app.univera.telegramlogin.internal.TelegramAuthContext
import app.univera.telegramlogin.internal.TelegramOAuthClient
import app.univera.telegramlogin.internal.openExternalUri
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Telegram OAuth2 + PKCE login for Kotlin Multiplatform (Android + iOS).
 *
 * Usage:
 * 1. Call [configure] once at app startup.
 * 2. Call [login] from shared code — it opens Telegram and suspends.
 * 3. Forward the redirect URL to [handle] from your platform entry point
 *    (Android `onNewIntent`, iOS `onOpenURL` / `continue`) to resume [login].
 *
 * The returned [TelegramLoginResult.Success.idToken] is a JWT — verify it on
 * your backend against Telegram's JWKS before establishing a session.
 */
object TelegramLogin {

    private data class Config(
        val clientId: String,
        val redirectUri: String,
        val scopes: List<String>,
    )

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var config: Config? = null
    private var codeVerifier: String? = null
    private var pending: CompletableDeferred<TelegramLoginResult>? = null

    private val oauth: TelegramOAuthClient by lazy {
        TelegramOAuthClient(
            HttpClient {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            },
        )
    }

    /** Stores the bot credentials. Must be called before [login]. */
    fun configure(clientId: String, redirectUri: String, scopes: List<String>) {
        config = Config(clientId, redirectUri, scopes)
    }

    /**
     * Starts the login flow: opens Telegram, then suspends until [handle]
     * receives the redirect. Returns the final [TelegramLoginResult].
     */
    suspend fun login(context: TelegramAuthContext): TelegramLoginResult {
        val cfg = config ?: return TelegramLoginResult.Failure(TelegramLoginError.NotConfigured)

        val deferred = CompletableDeferred<TelegramLoginResult>()
        mutex.withLock {
            codeVerifier = Pkce.createVerifier()
            pending = deferred
        }

        return try {
            val tgUrl = oauth.fetchCrossAppUrl(
                clientId = cfg.clientId,
                redirectUri = cfg.redirectUri,
                scopes = cfg.scopes,
                codeChallenge = Pkce.challengeFor(requireNotNull(codeVerifier)),
            )
            if (openExternalUri(context, tgUrl)) {
                deferred.await()
            } else {
                clearPending()
                TelegramLoginResult.Failure(TelegramLoginError.TelegramNotInstalled)
            }
        } catch (e: TelegramLoginError) {
            clearPending()
            TelegramLoginResult.Failure(e)
        } catch (e: Throwable) {
            clearPending()
            TelegramLoginResult.Failure(TelegramLoginError.Network(e.message ?: "Network error."))
        }
    }

    /**
     * Completes a pending [login] with the redirect [callbackUrl] delivered by
     * the OS. No-op when no login is in progress (safe to call for any URL).
     */
    fun handle(callbackUrl: String) {
        val deferred = pending ?: return
        pending = null

        val cfg = config
        if (cfg == null) {
            deferred.complete(TelegramLoginResult.Failure(TelegramLoginError.NotConfigured))
            return
        }

        val params = runCatching { Url(callbackUrl).parameters }.getOrNull()
        params?.get("error")?.let { error ->
            deferred.complete(TelegramLoginResult.Failure(TelegramLoginError.Unexpected(error)))
            return
        }
        val code = params?.get("code")
        if (code.isNullOrBlank()) {
            deferred.complete(TelegramLoginResult.Failure(TelegramLoginError.NoAuthorizationCode))
            return
        }
        val verifier = codeVerifier
        if (verifier == null) {
            deferred.complete(TelegramLoginResult.Failure(TelegramLoginError.Unexpected("No active PKCE session.")))
            return
        }

        scope.launch {
            val result = try {
                TelegramLoginResult.Success(
                    oauth.exchangeCode(
                        clientId = cfg.clientId,
                        code = code,
                        redirectUri = cfg.redirectUri,
                        codeVerifier = verifier,
                    ),
                )
            } catch (e: TelegramLoginError) {
                TelegramLoginResult.Failure(e)
            } catch (e: Throwable) {
                TelegramLoginResult.Failure(TelegramLoginError.Network(e.message ?: "Network error."))
            }
            codeVerifier = null
            deferred.complete(result)
        }
    }

    private fun clearPending() {
        pending = null
        codeVerifier = null
    }
}
