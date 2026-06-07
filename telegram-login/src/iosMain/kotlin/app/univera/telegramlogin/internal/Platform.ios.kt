package app.univera.telegramlogin.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.cValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionCallback
import platform.Foundation.NSError
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

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

// Retain the active session + its presentation anchor; ASWebAuthenticationSession
// is deallocated (and the sheet dismissed) without a strong reference.
private var activeWebAuthSession: ASWebAuthenticationSession? = null
private var activeAnchorProvider: NSObject? = null

private const val CANCELED_LOGIN_CODE = 1L // ASWebAuthenticationSessionErrorCodeCanceledLogin

private class WebAuthAnchorProvider :
    NSObject(),
    ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession,
    ): ASPresentationAnchor = UIApplication.sharedApplication.keyWindow ?: UIWindow()
}

@OptIn(ExperimentalForeignApi::class)
private fun isAtLeastIos174(): Boolean {
    val target = cValue<NSOperatingSystemVersion> {
        majorVersion = 17.convert()
        minorVersion = 4.convert()
        patchVersion = 0.convert()
    }
    return NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(target)
}

internal actual fun openWebAuth(
    context: TelegramAuthContext,
    authUrl: String,
    callbackHost: String,
    fallbackScheme: String?,
    onComplete: (callbackUrl: String?, cancelled: Boolean) -> Unit,
): Boolean {
    val url = NSURL.URLWithString(authUrl) ?: return false
    val provider = WebAuthAnchorProvider()

    val completion: (NSURL?, NSError?) -> Unit = { callbackURL, error ->
        activeWebAuthSession = null
        activeAnchorProvider = null
        when {
            callbackURL != null -> onComplete(callbackURL.absoluteString, false)
            error != null && error.code == CANCELED_LOGIN_CODE -> onComplete(null, true)
            else -> onComplete(null, false)
        }
    }

    val session = when {
        // iOS 17.4+: intercept the https universal-link callback directly.
        isAtLeastIos174() && callbackHost.isNotEmpty() -> ASWebAuthenticationSession(
            uRL = url,
            callback = ASWebAuthenticationSessionCallback.callbackWithHTTPSHost(callbackHost, path = "/"),
            completionHandler = completion,
        )
        // iOS < 17.4: an https callback can't be intercepted — needs a custom scheme.
        fallbackScheme != null -> ASWebAuthenticationSession(
            uRL = url,
            callbackURLScheme = fallbackScheme,
            completionHandler = completion,
        )
        else -> return false
    }

    session.presentationContextProvider = provider
    session.prefersEphemeralWebBrowserSession = false
    activeWebAuthSession = session
    activeAnchorProvider = provider
    return session.start()
}
