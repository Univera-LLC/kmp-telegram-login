# kmp-telegram-login

A **Kotlin Multiplatform** SDK for [Telegram's native "Log in with Telegram"](https://core.telegram.org/bots/telegram-login) flow (Android + iOS). Write the login logic **once in Kotlin** — no per-platform native SDK, no Swift/Kotlin bridge.

> Community library, not affiliated with or endorsed by Telegram. It implements the same OAuth2 + PKCE flow as Telegram's official native SDKs.

## Why

Telegram ships **separate** native SDKs for [Android](https://github.com/TelegramMessenger/telegram-login-android) and [iOS](https://github.com/TelegramMessenger/telegram-login-ios). In a KMP app you'd have to depend on both and bridge results across the Kotlin/Swift boundary. This library puts the entire flow in `commonMain`; only ~3 tiny platform primitives are `expect/actual`.

## Install

```kotlin
// build.gradle.kts (KMP module)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("app.univera.telegramlogin:telegram-login:0.1.0")
        }
    }
}
```

For pure-Swift apps, consume the generated `TelegramLogin.xcframework` (build with `./gradlew :telegram-login:assembleTelegramLoginXCFramework`).

## BotFather setup

Register your app under **@BotFather → Bot Settings → Login Widget**:

- **Android:** package name + SHA-256 of the signing key (use the **Play app-signing** key when Play App Signing is on). Redirect: `https://app<appid>-login.tg.dev/tglogin`.
- **iOS:** Bundle ID + Apple Team ID. Redirect: `https://app<appid>-login.tg.dev`.

## Usage (shared Kotlin)

```kotlin
// 1. Configure once at startup
TelegramLogin.configure(
    clientId = "YOUR_BOT_CLIENT_ID",
    redirectUri = "https://app<appid>-login.tg.dev",   // Android: add /tglogin
    scopes = listOf("openid", "phone"),
)

// 2. Start login (suspends until the redirect returns)
val result = TelegramLogin.login(context)   // TelegramAuthContext
when (result) {
    is TelegramLoginResult.Success -> sendToBackend(result.idToken) // verify JWT server-side
    is TelegramLoginResult.Failure -> show(result.error)
}
```

### Android

```kotlin
// build a context handle from the current Activity
val context = TelegramAuthContext(activity)

// forward the redirect to the SDK
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    intent.data?.let { TelegramLogin.handle(it.toString()) }
}
```

`AndroidManifest.xml` — App Link for the redirect host:

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https" android:host="app<appid>-login.tg.dev" android:pathPrefix="/tglogin" />
</intent-filter>
```

Add a `<queries>` entry so the app can open Telegram:

```xml
<queries><intent><action android:name="android.intent.action.VIEW"/><data android:scheme="tg"/></intent></queries>
```

### iOS

```swift
let context = TelegramAuthContext()           // no args on iOS

// SwiftUI
ContentView().onOpenURL { url in
    TelegramLogin.shared.handle(callbackUrl: url.absoluteString)
}
```

Add **Associated Domains** `applinks:app<appid>-login.tg.dev` and `LSApplicationQueriesSchemes` = `tg` to `Info.plist`.

## Verify the token

`Success.idToken` is a Telegram-signed OpenID Connect JWT. **Always verify it on your backend** against Telegram's JWKS (`https://oauth.telegram.org/.well-known/jwks.json`, issuer `https://oauth.telegram.org`, audience = your bot id) before establishing a session.

## Notes / limitations

- The **"verified app" badge** and the **direct app-switch** are decided by Telegram's servers (publication + platform), identical to the official SDKs — this library does not change that.
- **Web fallback** (when Telegram isn't installed): Android opens a Custom Tab; iOS runs an `ASWebAuthenticationSession`. iOS **17.4+** works with the `https` redirect out of the box. For **iOS < 17.4**, pass a custom `fallbackScheme` to `configure(...)` (and register that scheme + a matching redirect) — otherwise `login()` returns `TelegramLoginError.TelegramNotInstalled` on older iOS without Telegram. This mirrors the official iOS SDK.

## License

[MIT](LICENSE).
