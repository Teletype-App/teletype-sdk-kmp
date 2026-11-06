package app.teletype.sdk.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.teletype.sdk.resources.Res
import app.teletype.sdk.resources.chat_button
import org.jetbrains.compose.resources.painterResource

@Composable
fun ChatButton(
    modifier: Modifier = Modifier,
    position: String = "right",
    scale: Int = 100,
    bottom: Int = 24,
    side: Int = 20,
    onPress: (() -> Unit)? = null,
) {
    val client = rememberTeletypeClient()
    val state = rememberTeletypeState()
    val theme = mergeTheme(primaryFromConfig = state.primaryColor)
    val factor = (if (scale in CHAT_BUTTON_SCALES) scale else 100) / 100f
    val size = theme.buttonSize * factor
    val align = if (position == "left") Alignment.BottomStart else Alignment.BottomEnd

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(start = side.dp, end = side.dp, bottom = bottom.dp),
        contentAlignment = align,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(size.dp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(theme.primaryColor)
                    .clickable { onPress?.invoke() ?: client.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.chat_button),
                    contentDescription = "Chat",
                    modifier = Modifier.size((38 * factor).dp, (41 * factor).dp),
                    contentScale = ContentScale.Fit,
                )
            }
            if (state.unreadCount > 0 && !state.opened) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .background(theme.badgeColor, CircleShape)
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.unreadCount > 99) "99+" else state.unreadCount.toString(),
                        color = theme.badgeTextColor,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun ChatWidget(
    hideButton: Boolean = false,
    position: String = "right",
    scale: Int = 100,
    bottom: Int = 24,
    side: Int = 20,
) {
    if (hideButton) return
    ChatButton(position = position, scale = scale, bottom = bottom, side = side)
}
