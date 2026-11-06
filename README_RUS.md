# teletype-sdk-kmp

Kotlin Multiplatform SDK для встраивания онлайн-чата [Teletype](https://teletype.app) в приложения Android и iOS.

Использует тот же протокол, что и веб-виджет Teletype (`/widget`) — REST + `socket.io` v2 — и рисует **нативный** UI на Compose Multiplatform (без WebView).

Публичный API совпадает с `teletype-react-native-sdk` и веб-методами `_teletypeWidget.*`.

## Быстрый старт

```kotlin
val client = TeletypeClient(
    TeletypeConfig(
        teletypeExternalId = "YOUR_EXTERNAL_ID",
        mode = TeletypeMode.Dev,
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

## Пример

Общее Compose-демо для Android и iOS лежит в [`example`](./example):

```bash
./gradlew :example:composeApp:installDebug
cd example/iosApp && xcodegen
open example/iosApp/iosApp.xcodeproj
```

## Лицензия

MIT
