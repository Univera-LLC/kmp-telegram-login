package app.univera.telegramlogin

import app.univera.telegramlogin.internal.TelegramOAuthClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TelegramOAuthClientTest {

    private fun jsonClient(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpClient =
        HttpClient(
            MockEngine {
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    @Test
    fun fetchCrossAppUrl_parsesUrl() = runTest {
        val oauth = TelegramOAuthClient(jsonClient("""{"url":"tg://login?token=abc"}"""), baseUrl = "https://example.test")
        val url = oauth.fetchCrossAppUrl("cid", "https://app1-login.tg.dev", listOf("phone"), "challenge")
        assertEquals("tg://login?token=abc", url)
    }

    @Test
    fun exchangeCode_parsesIdToken() = runTest {
        val oauth = TelegramOAuthClient(jsonClient("""{"id_token":"JWT.123"}"""), baseUrl = "https://example.test")
        val token = oauth.exchangeCode("cid", "auth-code", "https://app1-login.tg.dev", "verifier")
        assertEquals("JWT.123", token)
    }

    @Test
    fun exchangeCode_serverError_throwsServer() = runTest {
        val oauth = TelegramOAuthClient(jsonClient("", HttpStatusCode.InternalServerError), baseUrl = "https://example.test")
        assertFailsWith<TelegramLoginError.Server> {
            oauth.exchangeCode("cid", "auth-code", "https://app1-login.tg.dev", "verifier")
        }
    }

    @Test
    fun exchangeCode_missingToken_throwsUnexpected() = runTest {
        val oauth = TelegramOAuthClient(jsonClient("""{"error":"invalid_grant"}"""), baseUrl = "https://example.test")
        assertFailsWith<TelegramLoginError.Unexpected> {
            oauth.exchangeCode("cid", "auth-code", "https://app1-login.tg.dev", "verifier")
        }
    }
}
