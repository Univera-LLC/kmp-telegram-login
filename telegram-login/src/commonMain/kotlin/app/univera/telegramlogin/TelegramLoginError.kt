package app.univera.telegramlogin

/** Errors surfaced by [TelegramLogin]. */
sealed class TelegramLoginError(message: String) : Exception(message) {

    /** [TelegramLogin.configure] was not called before [TelegramLogin.login]. */
    data object NotConfigured : TelegramLoginError("TelegramLogin.configure() must be called first.")

    /** The Telegram app is not installed (and no web fallback is configured). */
    data object TelegramNotInstalled : TelegramLoginError("Telegram app is not installed.")

    /** The redirect callback contained no authorization code. */
    data object NoAuthorizationCode : TelegramLoginError("Callback URL contained no authorization code.")

    /** The user cancelled the login flow. */
    data object Cancelled : TelegramLoginError("Login was cancelled.")

    /** Telegram returned a non-2xx HTTP status. */
    data class Server(val statusCode: Int) : TelegramLoginError("Telegram returned HTTP $statusCode.")

    /** A network / transport failure occurred. */
    data class Network(val reason: String) : TelegramLoginError(reason)

    /** Any other unexpected failure. */
    data class Unexpected(val detail: String) : TelegramLoginError(detail)
}
