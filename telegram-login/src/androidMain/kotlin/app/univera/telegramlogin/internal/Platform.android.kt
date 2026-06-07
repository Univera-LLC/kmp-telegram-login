package app.univera.telegramlogin.internal

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.security.SecureRandom

internal actual fun secureRandomBytes(size: Int): ByteArray =
    ByteArray(size).also { SecureRandom().nextBytes(it) }

internal actual val sdkPlatformParam: String = "android_sdk"

/**
 * Android handle. Construct with an `Activity` (or any `Context`):
 * `TelegramAuthContext(activity)`. The no-arg constructor exists only to
 * satisfy the common `expect` declaration and cannot launch Telegram.
 */
actual class TelegramAuthContext actual constructor() {
    internal var context: Context? = null

    constructor(context: Context) : this() {
        this.context = context
    }
}

internal actual fun openExternalUri(context: TelegramAuthContext, uri: String): Boolean {
    val ctx = context.context ?: return false
    return try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
