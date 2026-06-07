package app.univera.telegramlogin

import app.univera.telegramlogin.internal.Pkce
import app.univera.telegramlogin.internal.TelegramOAuthClient
import app.univera.telegramlogin.internal.openExternalUri
import app.univera.telegramlogin.internal.openWebAuth
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
 * 2. Call [login] from shared code — it opens Telegram (or the web fallback) and suspends.
 * 3. Forward the redirect URL to [handle] from your platform entry point
 *    (Android `onNewIntent`, iOS `onOpenURL` / `continue`) to resume [login].
 *
 * The returned [TelegramLoginResult.Success.idToken] is a JWT — verify it on
 * your backend against Telegram's JWKS before establishing a session.
 */
public object TelegramLogin {

    private data class Config(
        val clientId: String,
        val redirectUri: String,
        val scopes: List<String>,
        val fallbackScheme: String? = null,
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

    /**
     * Stores the bot credentials. Must be called before [login].
     *
     * [fallbackScheme] is an optional custom URL scheme used only by the iOS web
     * fallback on **iOS &lt; 17.4** (where `ASWebAuthenticationSession` cannot
     * intercept an `https` callback). On iOS 17.4+ and on Android it is ignored.
     */
    public fun configure(
        clientId: String,
        redirectUri: String,
        scopes: List<String>,
        fallbackScheme: String? = null,
    ) {
        config = Config(clientId, redirectUri, scopes, fallbackScheme)
    }

    /**
     * Starts the login flow: opens the Telegram app when installed, otherwise
     * falls back to an in-app web auth session. Suspends until [handle] (or the
     * web session) delivers the redirect, then returns the [TelegramLoginResult].
     */
    public suspend fun login(context: TelegramAuthContext): TelegramLoginResult {
        val cfg = config ?: return TelegramLoginResult.Failure(TelegramLoginError.NotConfigured)

        val deferred = CompletableDeferred<TelegramLoginResult>()
        val challenge: String
        mutex.withLock {
            val verifier = Pkce.createVerifier()
            codeVerifier = verifier
            challenge = Pkce.challengeFor(verifier)
            pending = deferred
        }

        return try {
            val tgUrl = oauth.fetchCrossAppUrl(
                clientId = cfg.clientId,
                redirectUri = cfg.redirectUri,
                scopes = cfg.scopes,
                codeChallenge = challenge,
            )
            if (openExternalUri(context, tgUrl)) {
                deferred.await()
            } else {
                startWebFallback(context, cfg, challenge, deferred)
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
    public fun handle(callbackUrl: String) {
        val deferred = pending ?: return
        pending = null

        val cfg = config
        if (cfg == null) {
            deferred.complete(TelegramLoginResult.Failure(TelegramLoginError.NotConfigured))
            return
        }

        val params = runCatching { Url(callbackUrl).parameters }.getOrNull()
        params?.get("error")?.let { error ->
            finish(deferred, TelegramLoginResult.Failure(TelegramLoginError.Unexpected(error)))
            return
        }
        val code = params?.get("code")
        if (code.isNullOrBlank()) {
            finish(deferred, TelegramLoginResult.Failure(TelegramLoginError.NoAuthorizationCode))
            return
        }
        val verifier = codeVerifier
        if (verifier == null) {
            finish(deferred, TelegramLoginResult.Failure(TelegramLoginError.Unexpected("No active PKCE session.")))
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

    /**
     * Telegram isn't installed — open the hosted web login. On iOS the web
     * session reports the callback URL directly; on Android the redirect
     * returns through the App Link into [handle].
     */
    private suspend fun startWebFallback(
        context: TelegramAuthContext,
        cfg: Config,
        challenge: String,
        deferred: CompletableDeferred<TelegramLoginResult>,
    ): TelegramLoginResult {
        val authUrl = oauth.buildAuthUrl(
            clientId = cfg.clientId,
            redirectUri = cfg.redirectUri,
            scopes = cfg.scopes,
            codeChallenge = challenge,
        )
        val callbackHost = runCatching { Url(cfg.redirectUri).host }.getOrNull().orEmpty()

        val started = openWebAuth(context, authUrl, callbackHost, cfg.fallbackScheme) { callbackUrl, cancelled ->
            when {
                callbackUrl != null -> handle(callbackUrl)
                cancelled -> finish(deferred, TelegramLoginResult.Failure(TelegramLoginError.Cancelled))
                else -> finish(deferred, TelegramLoginResult.Failure(TelegramLoginError.Unexpected("Web authentication failed.")))
            }
        }

        return if (started) {
            deferred.await()
        } else {
            clearPending()
            TelegramLoginResult.Failure(TelegramLoginError.TelegramNotInstalled)
        }
    }

    private fun finish(deferred: CompletableDeferred<TelegramLoginResult>, result: TelegramLoginResult) {
        codeVerifier = null
        pending = null
        deferred.complete(result)
    }

    private fun clearPending() {
        pending = null
        codeVerifier = null
    }
}
