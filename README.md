# kmp-telegram-login

[![Kotlin](https://img.shields.io/badge/kotlin-2.3.21-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Maven Central](https://img.shields.io/maven-central/v/app.univera.telegramlogin/telegram-login)](https://central.sonatype.com/artifact/app.univera.telegramlogin/telegram-login)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iOS-blue)

A **Kotlin Multiplatform** SDK for [Telegram's native "Log in with Telegram"](https://core.telegram.org/bots/telegram-login) flow. Write the login logic **once in Kotlin** for Android **and** iOS — no per-platform native SDK, no Swift/Kotlin bridge.

> Community library implementing the same OAuth2 + PKCE flow as Telegram's official native SDKs. Not affiliated with or endorsed by Telegram.

## Features

- 🟣 **One Kotlin API** for Android + iOS — drop the two native SDKs and the bridging glue.
- 🔐 **OAuth2 + PKCE** (S256), exactly like the official SDKs — returns a Telegram-signed OpenID Connect `id_token`.
- 📱 **Native app flow** — opens the installed Telegram app via the cross-app link.
- 🌐 **Web fallback** when Telegram isn't installed — Custom Tabs (Android) / `ASWebAuthenticationSession` (iOS).
- 🧩 **Tiny surface** — `configure()`, `suspend login()`, `handle()`. Typed results & errors.
- 🪶 **Light** — Ktor + okio only. No Telegram binary, no GitHub Packages auth.

## Why

Telegram ships **separate** native SDKs for [Android](https://github.com/TelegramMessenger/telegram-login-android) and [iOS](https://github.com/TelegramMessenger/telegram-login-ios). In a KMP app you'd depend on both and shuttle results across the Kotlin/Swift boundary. This library puts the whole flow in `commonMain`; only a couple of tiny primitives are `expect/actual`.

## Requirements

- Kotlin **2.3.21+** (the library is built with 2.3.21 for broad consumer compatibility).
- Android **minSdk 23+**, iOS **15.0+** (web fallback auto-opens on iOS 17.4+ — see [Web fallback](#web-fallback)).
- A Telegram bot registered with [@BotFather](https://t.me/botfather).

---

## 1. Install

### Kotlin Multiplatform (Gradle)

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("app.univera.telegramlogin:telegram-login:0.1.0")
        }
    }
}
```

---

## 2. Register your app with @BotFather

Open [@BotFather](https://t.me/botfather) → **Bot Settings → Login Widget** and add your app. Telegram issues a per-app domain: **`https://app{appid}-login.tg.dev`**.

| Platform | Provide | Redirect URI |
|---|---|---|
| **Android** | Package name + **SHA-256** of the signing cert (use the **Play app-signing** key when Play App Signing is on; `./gradlew signingReport`) | `https://app{appid}-login.tg.dev/tglogin` |
| **iOS** | Bundle ID + **Team ID** | `https://app{appid}-login.tg.dev` |

You also need your bot's **client_id** (the numeric bot id).

---

## 3. Platform configuration

### Android — `AndroidManifest.xml`

Declare the App Link on the Activity that handles the callback, and let the app query the `tg` scheme:

```xml
<activity android:name=".MainActivity" android:launchMode="singleTask" android:exported="true">
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="https"
              android:host="app{appid}-login.tg.dev"
              android:pathPrefix="/tglogin" />
    </intent-filter>
</activity>

<queries>
    <intent>
        <action android:name="android.intent.action.VIEW" />
        <data android:scheme="tg" />
    </intent>
</queries>
```

### iOS — Associated Domains + `Info.plist`

- **Signing & Capabilities → Associated Domains:** `applinks:app{appid}-login.tg.dev`
- **`Info.plist` → `LSApplicationQueriesSchemes`:** add `tg`

> During development iOS heavily caches the Universal-Links config. Append `?mode=developer` to the Associated Domain (`applinks:app{appid}-login.tg.dev?mode=developer`) and toggle **Settings → Developer → Associated Domains Development** on a physical device. Remove it for release.

---

## 4. Initialize (once at startup)

`configure()` is part of the common API, but the **`redirectUri` is per-platform**: BotFather issues a *different* `app{appid}-login.tg.dev` host for your Android app vs your iOS app, and Android's App Link uses the `/tglogin` path while iOS uses the bare host. Only `clientId` and `scopes` are the same on both — so supply the redirect via `expect/actual`:

```kotlin
// commonMain
import app.univera.telegramlogin.TelegramLogin

internal expect val telegramRedirectUri: String

fun initTelegramLogin() = TelegramLogin.configure(
    clientId = "YOUR_BOT_CLIENT_ID",          // your bot id — same on both platforms
    redirectUri = telegramRedirectUri,
    scopes = listOf("openid", "phone"),
    // fallbackScheme = "yourapp",            // optional: iOS < 17.4 web fallback
)
```

```kotlin
// androidMain — Android app's host, WITH the /tglogin path
internal actual val telegramRedirectUri = "https://app<androidAppId>-login.tg.dev/tglogin"
```

```kotlin
// iosMain — iOS app's host, NO path
internal actual val telegramRedirectUri = "https://app<iosAppId>-login.tg.dev"
```

Call `initTelegramLogin()` once at startup (e.g. `Application.onCreate`, or a shared init invoked from each platform's entry point).

| Parameter | Description |
|---|---|
| `clientId` | **Required.** Your bot's client id — **same** on both platforms. |
| `redirectUri` | **Required, per-platform.** The `app{appid}-login.tg.dev` URL from @BotFather — host differs Android vs iOS; `/tglogin` on Android, bare host on iOS. |
| `scopes` | **Required.** e.g. `["openid", "phone"]`. |
| `fallbackScheme` | Optional custom scheme for the iOS < 17.4 web fallback. |

---

## 5. Log in (shared code)

`login()` opens Telegram and **suspends** until the redirect returns. Await it in a long-lived scope (e.g. a `ViewModel`):

```kotlin
when (val result = TelegramLogin.login(context)) {           // context: TelegramAuthContext
    is TelegramLoginResult.Success -> sendToBackend(result.idToken)
    is TelegramLoginResult.Failure -> showError(result.error)
}
```

Build the `TelegramAuthContext` per platform. In Compose Multiplatform a tiny `expect/actual` helper keeps the call site common:

```kotlin
// commonMain
@Composable expect fun rememberTelegramAuthContext(): TelegramAuthContext

// androidMain — applicationContext is enough (the SDK uses FLAG_ACTIVITY_NEW_TASK)
@Composable actual fun rememberTelegramAuthContext() =
    remember { TelegramAuthContext(LocalContext.current.applicationContext) }

// iosMain
@Composable actual fun rememberTelegramAuthContext() = remember { TelegramAuthContext() }
```

## 6. Forward the redirect to `handle()`

The OS delivers the redirect to a platform entry point; pass it to the SDK to resume `login()`:

**Android** (`Activity.onNewIntent`):
```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    intent.data?.let { TelegramLogin.handle(it.toString()) }
}
```

**iOS** (SwiftUI):
```swift
ContentView().onOpenURL { url in
    TelegramLogin.shared.handle(callbackUrl: url.absoluteString)
}
```

## 7. Verify the token (backend)

`Success.idToken` is a Telegram-signed **OpenID Connect JWT**. **Always verify it server-side** before trusting any claim:

- JWKS: `https://oauth.telegram.org/.well-known/jwks.json`
- issuer: `https://oauth.telegram.org`
- audience: your bot's client id

See [Validating ID tokens](https://core.telegram.org/bots/telegram-login#validating-id-tokens).

---

## Web fallback

When Telegram isn't installed, the SDK opens the hosted login:

- **Android:** a Custom Tab; the redirect returns through your App Link into `handle()`.
- **iOS 17.4+:** `ASWebAuthenticationSession` with the `https` callback — zero extra config.
- **iOS < 17.4:** pass a custom `fallbackScheme` to `configure()` (and register it), since `ASWebAuthenticationSession` can't intercept an `https` callback on older iOS. Without it, `login()` returns `TelegramNotInstalled`.

## Result & errors

```kotlin
sealed interface TelegramLoginResult {
    data class Success(val idToken: String)
    data class Failure(val error: TelegramLoginError)
}

sealed class TelegramLoginError {        // all extend Exception
    NotConfigured            // configure() not called
    TelegramNotInstalled     // Telegram missing and no web fallback available
    NoAuthorizationCode      // redirect had no ?code
    Cancelled                // user dismissed the flow
    Server(statusCode)       // Telegram returned non-2xx
    Network(reason)          // transport failure
    Unexpected(detail)       // anything else
}
```

## The "verified app" badge

Telegram shows the **verified** badge only when **(1)** the app is **published in the store** with the BotFather-registered package/bundle, **and (2)** login uses the `*.tg.dev` link (this SDK does). Debug / unpublished builds fall back to the unverified path — test the verified flow on a **Play internal-testing** track (iOS: **TestFlight**). This is decided by Telegram, not the SDK.

## Known limitations

- If the **process is killed** while the user is in Telegram, the in-memory PKCE verifier is lost and the login can't complete on return (no crash — it just ends silently). Same as the official SDKs.
- iOS web fallback auto-opens on **17.4+**; older iOS needs `fallbackScheme`.

## License

[MIT](LICENSE).
