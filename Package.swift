// swift-tools-version:5.9
import PackageDescription

// Swift Package Manager manifest for pure-Swift consumers.
//
// The binary is the XCFramework produced by the KMP module. On each release
// (run on macOS):
//   ./gradlew :telegram-login:assembleTelegramLoginReleaseXCFramework
//   cd telegram-login/build/XCFrameworks/release
//   ditto -c -k --sequesterRsrc --keepParent TelegramLogin.xcframework TelegramLogin.xcframework.zip
//   swift package compute-checksum TelegramLogin.xcframework.zip
// Then attach the zip to the matching GitHub release and update url + checksum below.
let package = Package(
    name: "TelegramLogin",
    platforms: [.iOS(.v15)],
    products: [
        .library(name: "TelegramLogin", targets: ["TelegramLogin"]),
    ],
    targets: [
        .binaryTarget(
            name: "TelegramLogin",
            url: "https://github.com/Univera-LLC/kmp-telegram-login/releases/download/0.1.0/TelegramLogin.xcframework.zip",
            checksum: "REPLACE_WITH_CHECKSUM_ON_RELEASE"
        ),
    ]
)
