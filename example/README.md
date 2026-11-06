This is a new [**Kotlin Multiplatform**](https://www.jetbrains.com/kotlin-multiplatform/) example for `teletype-sdk`.

Shared Compose UI lives in `composeApp` and runs on Android and iOS. The iOS Xcode wrapper is `iosApp`.

## Android

From the repository root:

```bash
./gradlew :example:composeApp:installDebug
```

Or open the root project in Android Studio and run the `example.composeApp` configuration.

## iOS

```bash
brew install xcodegen   # once
cd example/iosApp
xcodegen
open iosApp.xcodeproj
```

Xcode compiles the Compose framework via Gradle (`embedAndSignAppleFrameworkForXcode`) and launches the demo.

Paste a Teletype widget external id on the setup screen (or the full install `<script>`). The demo defaults to the **dev** cluster (`teletypeapp.com`).
