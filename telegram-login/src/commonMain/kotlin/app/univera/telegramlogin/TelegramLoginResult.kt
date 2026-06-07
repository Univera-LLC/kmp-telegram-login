package app.univera.telegramlogin

/** Outcome of [TelegramLogin.login]. */
sealed interface TelegramLoginResult {

    /**
     * Login succeeded. [idToken] is a Telegram-signed OpenID Connect JWT —
     * send it to your backend and verify it against Telegram's JWKS before
     * trusting any claims.
     */
    data class Success(val idToken: String) : TelegramLoginResult

    /** Login failed; inspect [error]. */
    data class Failure(val error: TelegramLoginError) : TelegramLoginResult
}
