package app.univera.telegramlogin.internal

import app.univera.telegramlogin.TelegramLoginError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Thin client for Telegram's OAuth endpoints (`/crossapp` and `/token`). */
internal class TelegramOAuthClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    @Serializable
    private data class CrossAppResponse(val url: String? = null)

    @Serializable
    private data class TokenResponse(
        @SerialName("id_token") val idToken: String? = null,
        val error: String? = null,
    )

    /** Resolves the `tg://` cross-app URL that opens the Telegram consent UI. */
    suspend fun fetchCrossAppUrl(
        clientId: String,
        redirectUri: String,
        scopes: List<String>,
        codeChallenge: String,
    ): String {
        val response: HttpResponse = httpClient.get("$baseUrl/crossapp") {
            parameter("client_id", clientId)
            parameter("response_type", "code")
            parameter("scope", normalizeScopes(scopes))
            parameter("redirect_uri", redirectUri)
            parameter(sdkPlatformParam, "1")
            parameter("code_challenge", codeChallenge)
            parameter("code_challenge_method", "S256")
        }
        if (!response.status.isSuccess()) throw TelegramLoginError.Server(response.status.value)
        return response.body<CrossAppResponse>().url
            ?: throw TelegramLoginError.Unexpected("Telegram /crossapp returned no url.")
    }

    /** Exchanges the authorization [code] for a Telegram OIDC `id_token`. */
    suspend fun exchangeCode(
        clientId: String,
        code: String,
        redirectUri: String,
        codeVerifier: String,
    ): String {
        val response: HttpResponse = httpClient.submitForm(
            url = "$baseUrl/token",
            formParameters = Parameters.build {
                append("grant_type", "authorization_code")
                append("client_id", clientId)
                append("code", code)
                append("redirect_uri", redirectUri)
                append("code_verifier", codeVerifier)
            },
        )
        if (!response.status.isSuccess()) throw TelegramLoginError.Server(response.status.value)
        val body = response.body<TokenResponse>()
        return body.idToken ?: throw TelegramLoginError.Unexpected(body.error ?: "No id_token in response.")
    }

    /** Builds the hosted `/auth` URL used by the web fallback (no `*_sdk` marker). */
    fun buildAuthUrl(
        clientId: String,
        redirectUri: String,
        scopes: List<String>,
        codeChallenge: String,
    ): String = URLBuilder("$baseUrl/auth").apply {
        parameters.append("client_id", clientId)
        parameters.append("response_type", "code")
        parameters.append("scope", normalizeScopes(scopes))
        parameters.append("redirect_uri", redirectUri)
        parameters.append("code_challenge", codeChallenge)
        parameters.append("code_challenge_method", "S256")
    }.buildString()

    /** Ensures `openid` is always requested, de-duplicating caller scopes. */
    private fun normalizeScopes(scopes: List<String>): String =
        (listOf("openid") + scopes).distinct().joinToString(" ")

    private companion object {
        const val DEFAULT_BASE_URL = "https://oauth.telegram.org"
    }
}
