package app.univera.telegramlogin.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionCallback
import platform.Foundation.NSError
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

internal actual fun openWebAuth(
    context: TelegramAuthContext,
    authUrl: String,
    callbackHost: String,
    onComplete: (callbackUrl: String?, cancelled: Boolean) -> Unit,
): Boolean {
    val url = NSURL.URLWithString(authUrl) ?: return false
    val provider = WebAuthAnchorProvider()

    val session = ASWebAuthenticationSession(
        uRL = url,
        callback = ASWebAuthenticationSessionCallback.callbackWithHTTPSHost(callbackHost, path = "/"),
        completionHandler = { callbackURL: NSURL?, error: NSError? ->
            activeWebAuthSession = null
            activeAnchorProvider = null
            when {
                callbackURL != null -> onComplete(callbackURL.absoluteString, false)
                error != null && error.code == CANCELED_LOGIN_CODE -> onComplete(null, true)
                else -> onComplete(null, false)
            }
        },
    )
    session.presentationContextProvider = provider
    session.prefersEphemeralWebBrowserSession = false
    activeWebAuthSession = session
    activeAnchorProvider = provider
    return session.start()
}
