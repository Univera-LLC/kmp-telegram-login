package app.univera.telegramlogin

/** Errors surfaced by [TelegramLogin]. */
public sealed class TelegramLoginError(message: String) : Exception(message) {

    /** [TelegramLogin.configure] was not called before [TelegramLogin.login]. */
    public data object NotConfigured : TelegramLoginError("TelegramLogin.configure() must be called first.")

    /** The Telegram app is not installed (and no web fallback is available). */
    public data object TelegramNotInstalled : TelegramLoginError("Telegram app is not installed.")

    /** The redirect callback contained no authorization code. */
    public data object NoAuthorizationCode : TelegramLoginError("Callback URL contained no authorization code.")

    /** The user cancelled the login flow. */
    public data object Cancelled : TelegramLoginError("Login was cancelled.")

    /** Telegram returned a non-2xx HTTP status. */
    public data class Server(val statusCode: Int) : TelegramLoginError("Telegram returned HTTP $statusCode.")

    /** A network / transport failure occurred. */
    public data class Network(val reason: String) : TelegramLoginError(reason)

    /** Any other unexpected failure. */
    public data class Unexpected(val detail: String) : TelegramLoginError(detail)
}
