package app.teletype.sdk.ui

import androidx.compose.ui.graphics.Color

data class TeletypeTheme(
    val primaryColor: Color = Color(0xFF0084FF),
    val onPrimaryColor: Color = Color.White,
    val backgroundColor: Color = Color(0xFFF4F6F8),
    val headerTextColor: Color = Color.White,
    val headerSubTextColor: Color = Color.White.copy(alpha = 0.9f),
    val ownBubbleColor: Color = Color(0xFF0084FF),
    val ownTextColor: Color = Color.White,
    val operatorBubbleColor: Color = Color(0xFFECEEF1),
    val operatorTextColor: Color = Color(0xFF1A1A1A),
    val mutedColor: Color = Color(0xFF8A949E),
    val borderColor: Color = Color(0xFFE3E8EE),
    val inputBackground: Color = Color.White,
    val inputTextColor: Color = Color(0xFF1A1A1A),
    val badgeColor: Color = Color(0xFFFF3B30),
    val badgeTextColor: Color = Color.White,
    val bubbleRadius: Int = 20,
    val buttonSize: Int = 60,
)

val DefaultTheme = TeletypeTheme()

fun parseHexColor(hex: String, fallback: Color = Color(0xFF0084FF)): Color {
    val clean = hex.trim().removePrefix("#")
    val value = when (clean.length) {
        6 -> clean.toLongOrNull(16)
        8 -> clean.toLongOrNull(16)
        3 -> clean.map { "$it$it" }.joinToString("").toLongOrNull(16)
        else -> null
    } ?: return fallback
    return if (clean.length == 8) Color(value.toInt()) else Color(0xFF000000.toInt() or value.toInt())
}

fun mergeTheme(overrides: TeletypeTheme? = null, primaryFromConfig: String? = null): TeletypeTheme {
    var base = DefaultTheme
    if (!primaryFromConfig.isNullOrBlank()) {
        val color = parseHexColor(primaryFromConfig)
        base = base.copy(primaryColor = color, ownBubbleColor = color)
    }
    return overrides ?: base
}

val CHAT_BUTTON_SCALES = listOf(70, 80, 90, 100)
typealias ChatButtonScale = Int
typealias ChatButtonPosition = String
