package app.univera.telegramlogin.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSURL
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication

@OptIn(ExperimentalForeignApi::class)
internal actual fun secureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    if (size == 0) return bytes
    bytes.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
    }
    return bytes
}

internal actual val sdkPlatformParam: String = "ios_sdk"

/** iOS handle. No state required — the shared `UIApplication` performs the open. */
actual class TelegramAuthContext actual constructor()

internal actual fun openExternalUri(context: TelegramAuthContext, uri: String): Boolean {
    val url = NSURL.URLWithString(uri) ?: return false
    val application = UIApplication.sharedApplication
    if (!application.canOpenURL(url)) return false
    application.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    return true
}
