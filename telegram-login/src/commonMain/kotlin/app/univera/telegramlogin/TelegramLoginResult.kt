package app.univera.telegramlogin

/** Outcome of [TelegramLogin.login]. */
public sealed interface TelegramLoginResult {

    /**
     * Login succeeded. [idToken] is a Telegram-signed OpenID Connect JWT —
     * send it to your backend and verify it against Telegram's JWKS before
     * trusting any claims.
     */
    public data class Success(val idToken: String) : TelegramLoginResult

    /** Login failed; inspect [error]. */
    public data class Failure(val error: TelegramLoginError) : TelegramLoginResult
}
