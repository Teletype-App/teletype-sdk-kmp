# teletype-sdk-kmp

Kotlin Multiplatform SDK for embedding the [Teletype](https://teletype.app) online chat into Android and iOS apps.

It reuses the same backend protocol as the Teletype **web widget** (`/widget`) — REST API + `socket.io` v2 — and renders a **native Compose Multiplatform** chat UI (no WebView).

The public surface mirrors `teletype-react-native-sdk` / the web `_teletypeWidget.*` API.

> 🇷🇺 Документация на русском: [README_RUS.md](./README_RUS.md)

## Features

- Real-time messaging over socket.io v2 (typing, seen/received receipts)
- Drop-in `ChatWidget` (floating button) and `ChatScreen`
- Headless `TeletypeClient` + Compose state for custom UIs
- Message history, pending queue, auto-reconnect
- Operator rating, attachments, unread badge
- Persisted session (person id + appeal id)
- English / Russian localization

## Requirements

- JDK 17
- Android SDK 35 (minSdk 26)
- Xcode 15+ (iOS 16+)
- Android Studio or IntelliJ with the Kotlin Multiplatform plugin

## Quick start (Compose Multiplatform)

```kotlin
val client = TeletypeClient(
    TeletypeConfig(
        teletypeExternalId = "YOUR_EXTERNAL_ID",
        mode = TeletypeMode.Dev, // or Prod
        title = "Онлайн чат",
        primaryColor = "#0084ff",
    )
)
client.init()

TeletypeProvider(client) {
    val state = rememberTeletypeState()
    if (state.opened) {
        ChatScreen(onClose = { client.hide() })
    } else {
        YourApp()
        ChatWidget()
    }
}
```

`teletypeExternalId` is the widget external id from the Teletype dashboard (the same value as `teletypeExternalId` on the web).

## Example apps

Shared Compose demo (Android + iOS) lives in [`example`](./example):

```bash
# Android
./gradlew :example:composeApp:installDebug

# iOS (first time)
cd example/iosApp && xcodegen && cd ../..
open example/iosApp/iosApp.xcodeproj
```

The demo lets you paste a channel id (or the install `<script>`), switch language, pick chat color / background / button position, and send files.

## Clusters: `Dev` vs `Prod`

| mode | apiUrl | wsUrl |
| --- | --- | --- |
| `Dev` | `https://api.teletypeapp.com/` | `https://websocket-widget.teletypeapp.com` |
| `Prod` (default) | `https://api.teletype.app/` | `https://websocket-widget.teletype.app/` |

## License

MIT
