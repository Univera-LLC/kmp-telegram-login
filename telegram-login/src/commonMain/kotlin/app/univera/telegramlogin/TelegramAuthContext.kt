package app.univera.telegramlogin

/**
 * Opaque, platform-specific handle required to launch the Telegram app.
 *
 * - **Android:** construct with the current `Context`/`Activity` —
 *   `TelegramAuthContext(activity)`.
 * - **iOS:** construct with no arguments — `TelegramAuthContext()`.
 */
public expect class TelegramAuthContext()
