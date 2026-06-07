package app.univera.telegramlogin

import android.content.Context

/**
 * Android handle. Construct with an `Activity` (or any `Context`):
 * `TelegramAuthContext(activity)`. The no-arg constructor exists only to satisfy
 * the common `expect` declaration and cannot launch Telegram.
 */
public actual class TelegramAuthContext actual constructor() {
    internal var context: Context? = null

    public constructor(context: Context) : this() {
        this.context = context
    }
}
