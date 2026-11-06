package app.teletype.sdk

import app.teletype.sdk.config.DEFAULT_CHAT_TITLE
import app.teletype.sdk.config.DEFAULT_PRIMARY_COLOR
import app.teletype.sdk.config.DEV_ENVIRONMENT
import app.teletype.sdk.config.DEFAULT_ENVIRONMENT
import app.teletype.sdk.config.DEFAULT_MODE
import app.teletype.sdk.config.PROD_ENVIRONMENT
import app.teletype.sdk.config.TeletypeEnvironment
import app.teletype.sdk.config.TeletypeMode
import app.teletype.sdk.config.WsEvent
import app.teletype.sdk.config.extractTeletypeExternalId
import app.teletype.sdk.config.getEnvironment
import app.teletype.sdk.config.isTeletypeExternalId
import app.teletype.sdk.config.looksLikeEmbedSnippet
import app.teletype.sdk.core.TeletypeClient
import app.teletype.sdk.core.TeletypeConfig
import app.teletype.sdk.core.TeletypeState
import app.teletype.sdk.events.TeletypeEvents
import app.teletype.sdk.i18n.DEFAULT_LOCALE
import app.teletype.sdk.i18n.EN_STRINGS
import app.teletype.sdk.i18n.RU_STRINGS
import app.teletype.sdk.i18n.TeletypeStrings
import app.teletype.sdk.i18n.TeletypeTranslations
import app.teletype.sdk.i18n.formatRelativeTime
import app.teletype.sdk.i18n.getSystemLanguage
import app.teletype.sdk.i18n.normalizeLanguage
import app.teletype.sdk.i18n.resolveStrings
import app.teletype.sdk.storage.KeyValueStore
import app.teletype.sdk.storage.MemoryStore
import app.teletype.sdk.storage.SettingsStore
import app.teletype.sdk.types.CHAT_BACKGROUND_PICKER
import app.teletype.sdk.types.CHAT_BACKGROUNDS
import app.teletype.sdk.types.ChatBackground
import app.teletype.sdk.types.DEFAULT_CHAT_BACKGROUND
import app.teletype.sdk.types.LocalFile
import app.teletype.sdk.types.isChatBackground
import app.teletype.sdk.types.parseChatBackground
import app.teletype.sdk.ui.CHAT_BUTTON_SCALES
import app.teletype.sdk.ui.ChatButton
import app.teletype.sdk.ui.ChatScreen
import app.teletype.sdk.ui.ChatWidget
import app.teletype.sdk.ui.TeletypeProvider
import app.teletype.sdk.ui.TeletypeTheme
import app.teletype.sdk.ui.rememberTeletypeClient
import app.teletype.sdk.ui.rememberTeletypeState
import app.teletype.sdk.util.formatBytes
import app.teletype.sdk.resources.Res
import app.teletype.sdk.resources.bg_circles
import app.teletype.sdk.resources.bg_circles_thumb
import app.teletype.sdk.resources.bg_doodle
import app.teletype.sdk.resources.bg_doodle_thumb
import app.teletype.sdk.resources.bg_scribbles
import app.teletype.sdk.resources.bg_scribbles_thumb

object ChatBackgroundThumbs {
    val doodle = Res.drawable.bg_doodle_thumb
    val circles = Res.drawable.bg_circles_thumb
    val scribbles = Res.drawable.bg_scribbles_thumb
}

object ChatBackgroundSources {
    val doodle = Res.drawable.bg_doodle
    val circles = Res.drawable.bg_circles
    val scribbles = Res.drawable.bg_scribbles
}
