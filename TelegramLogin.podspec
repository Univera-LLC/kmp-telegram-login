Pod::Spec.new do |s|
  s.name                = 'TelegramLogin'
  s.version             = '0.1.0'
  s.summary             = 'Kotlin Multiplatform Telegram native login (OAuth2 + PKCE) for iOS.'
  s.homepage            = 'https://github.com/Univera-LLC/kmp-telegram-login'
  s.license             = { :type => 'MIT', :file => 'LICENSE' }
  s.authors             = { 'Univera LLC' => 'https://univera.app' }
  s.platform            = :ios, '15.0'

  # XCFramework built from the KMP module and attached to the GitHub release —
  # see PUBLISHING.md.
  s.source              = { :http => "https://github.com/Univera-LLC/kmp-telegram-login/releases/download/#{s.version}/TelegramLogin.xcframework.zip" }
  s.vendored_frameworks = 'TelegramLogin.xcframework'
end
