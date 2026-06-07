package app.univera.telegramlogin.internal

import app.univera.telegramlogin.TelegramAuthContext

/** Cryptographically secure random bytes for the PKCE code verifier. */
internal expect fun secureRandomBytes(size: Int): ByteArray

/** Platform marker sent to Telegram's `/crossapp` endpoint (`android_sdk` / `ios_sdk`). */
internal expect val sdkPlatformParam: String

/**
 * Opens [uri] — a `tg://` cross-app link or its `https` universal link — in the
 * Telegram app. Returns `false` if it cannot be opened (e.g. Telegram missing).
 */
internal expect fun openExternalUri(context: TelegramAuthContext, uri: String): Boolean

/**
 * Web fallback when the Telegram app is unavailable: opens [authUrl] in an
 * in-app browser whose redirect host is [callbackHost].
 *
 * - **iOS:** runs an `ASWebAuthenticationSession`; the captured redirect (or a
 *   cancellation) is reported through [onComplete].
 * - **Android:** opens a Custom Tab; the redirect returns via the App Link into
 *   [app.univera.telegramlogin.TelegramLogin.handle], so [onComplete] is not used.
 *
 * Returns `false` if the session/tab could not be started.
 */
internal expect fun openWebAuth(
    context: TelegramAuthContext,
    authUrl: String,
    callbackHost: String,
    fallbackScheme: String?,
    onComplete: (callbackUrl: String?, cancelled: Boolean) -> Unit,
): Boolean
