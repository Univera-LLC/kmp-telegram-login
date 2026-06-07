package app.univera.telegramlogin.internal

/** Cryptographically secure random bytes for the PKCE code verifier. */
internal expect fun secureRandomBytes(size: Int): ByteArray

/** Platform marker sent to Telegram's `/crossapp` endpoint (`android_sdk` / `ios_sdk`). */
internal expect val sdkPlatformParam: String

/**
 * Opaque, platform-specific handle required to launch the Telegram app.
 *
 * - **Android:** construct with the current `Context`/`Activity` —
 *   `TelegramAuthContext(activity)`.
 * - **iOS:** construct with no arguments — `TelegramAuthContext()`.
 */
expect class TelegramAuthContext()

/**
 * Opens [uri] — a `tg://` cross-app link or its `https` universal link — in the
 * Telegram app. Returns `false` if it cannot be opened (e.g. Telegram missing).
 */
internal expect fun openExternalUri(context: TelegramAuthContext, uri: String): Boolean
