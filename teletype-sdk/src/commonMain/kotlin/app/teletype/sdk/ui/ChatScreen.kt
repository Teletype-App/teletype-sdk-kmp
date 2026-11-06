package app.teletype.sdk.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.teletype.sdk.i18n.formatRelativeTime
import app.teletype.sdk.platform.openUrl
import app.teletype.sdk.resources.Res
import app.teletype.sdk.resources.bg_circles
import app.teletype.sdk.resources.bg_doodle
import app.teletype.sdk.resources.bg_scribbles
import app.teletype.sdk.types.ChatBackground
import app.teletype.sdk.types.ConversationRating
import app.teletype.sdk.types.FormType
import app.teletype.sdk.types.LocalFile
import app.teletype.sdk.types.Message
import app.teletype.sdk.types.MessageStatus
import app.teletype.sdk.types.MessageType
import app.teletype.sdk.util.extractUrls
import app.teletype.sdk.util.formatBytes
import app.teletype.sdk.util.getExtension
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    showClose: Boolean = true,
    onClose: (() -> Unit)? = null,
    placeholder: String? = null,
    title: String? = null,
    onAttachmentPress: (suspend () -> LocalFile?)? = null,
) {
    val client = rememberTeletypeClient()
    val state = rememberTeletypeState()
    val theme = mergeTheme(primaryFromConfig = state.primaryColor)
    var pendingFile by remember { mutableStateOf<LocalFile?>(null) }
    var mediaPreview by remember { mutableStateOf<Pair<String, String>?>(null) }
    val headerTitle = title ?: state.title
    val composerPlaceholder = placeholder ?: state.strings.composerPlaceholder

    Box(modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.primaryColor)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showClose) {
                Text(
                    text = "‹",
                    color = theme.headerTextColor,
                    fontSize = 32.sp,
                    modifier = Modifier
                        .clickable { onClose?.invoke() ?: client.hide() }
                        .padding(horizontal = 8.dp),
                )
            }
            Text(
                text = headerTitle,
                color = theme.headerTextColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }

        MessageList(
            messages = state.messages,
            theme = theme,
            language = state.locale,
            strings = state.strings,
            chatBackground = state.chatBackground,
            operatorTyping = state.operatorTyping,
            onRate = { value, tempId -> client.rateAppeal(value, tempId) },
            onRetry = { client.retryMessage(it) },
            onOpenMedia = { url, kind -> mediaPreview = url to kind },
            modifier = Modifier.weight(1f),
        )

        Composer(
            theme = theme,
            placeholder = composerPlaceholder,
            value = state.draft,
            onChangeText = { client.setMessage(it) },
            onSend = {
                client.sendMessage(it)
                client.setMessage("")
            },
            onTyping = { client.startTyping(it) },
            onStopTyping = { client.stopTyping() },
            onAttachmentPress = onAttachmentPress?.let { picker ->
                {
                    val file = picker()
                    if (file != null) pendingFile = file
                }
            },
        )
    }

    pendingFile?.let { file ->
        ModalSendFile(
            file = file,
            theme = theme,
            strings = state.strings,
            onClose = { pendingFile = null },
            onSend = { picked ->
                pendingFile = null
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                    runCatching { client.uploadFile(picked) }
                }
            },
        )
    }

    mediaPreview?.let { (url, kind) ->
        MediaPreview(url = url, kind = kind, onClose = { mediaPreview = null })
    }
    }
}

@Composable
private fun Composer(
    theme: TeletypeTheme,
    placeholder: String,
    value: String,
    onChangeText: (String) -> Unit,
    onSend: (String) -> Unit,
    onTyping: (String) -> Unit,
    onStopTyping: () -> Unit,
    onAttachmentPress: (suspend () -> Unit)?,
) {
    val canSend = value.trim().isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.inputBackground)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicTextField(
            value = value,
            onValueChange = {
                onChangeText(it)
                if (it.trim().isNotEmpty()) onTyping(it) else onStopTyping()
            },
            textStyle = TextStyle(color = theme.inputTextColor, fontSize = 16.sp),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 42.dp, max = 120.dp)
                .padding(8.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, color = theme.mutedColor, fontSize = 16.sp)
                    }
                    inner()
                }
            },
        )
        Text(
            text = "📎",
            fontSize = 22.sp,
            color = theme.mutedColor,
            modifier = Modifier
                .padding(6.dp)
                .clickable(enabled = onAttachmentPress != null) {
                    if (onAttachmentPress != null) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            onAttachmentPress()
                        }
                    }
                },
        )
        if (canSend) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(theme.primaryColor)
                    .clickable {
                        onSend(value.trim())
                        onStopTyping()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("➤", color = theme.onPrimaryColor, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun MessageList(
    messages: List<Message>,
    theme: TeletypeTheme,
    language: String,
    strings: app.teletype.sdk.i18n.TeletypeStrings,
    chatBackground: ChatBackground,
    operatorTyping: Boolean,
    onRate: (ConversationRating, String) -> Unit,
    onRetry: (String) -> Unit,
    onOpenMedia: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    val bgPainter = when (chatBackground) {
        ChatBackground.Doodle -> painterResource(Res.drawable.bg_doodle)
        ChatBackground.Circles -> painterResource(Res.drawable.bg_circles)
        ChatBackground.Scribbles -> painterResource(Res.drawable.bg_scribbles)
        ChatBackground.White -> null
    }
    Box(modifier) {
        if (bgPainter != null) {
            androidx.compose.foundation.Image(
                painter = bgPainter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(messages, key = { it.tempId.ifEmpty { it.id ?: it.hashCode().toString() } }) { message ->
                MessageBubble(
                    message = message,
                    theme = theme,
                    strings = strings,
                    language = language,
                    onRate = onRate,
                    onRetry = onRetry,
                    onOpenMedia = onOpenMedia,
                )
            }
            if (operatorTyping) {
                item("typing") { TypingIndicator(theme) }
            }
        }
    }
}

@Composable
private fun TypingIndicator(theme: TeletypeTheme) {
    val t = rememberInfiniteTransition()
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(theme.bubbleRadius.dp))
            .background(theme.operatorBubbleColor)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) { i ->
            val alpha by t.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(400, delayMillis = i * 150), RepeatMode.Reverse),
            )
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(theme.mutedColor.copy(alpha = alpha)),
            )
        }
    }
}

private val RATINGS = listOf(
    ConversationRating.Unsatisfactory to "😡",
    ConversationRating.Bad to "🙁",
    ConversationRating.Satisfactory to "😐",
    ConversationRating.Good to "🙂",
    ConversationRating.Excellent to "😍",
)

@Composable
private fun MessageBubble(
    message: Message,
    theme: TeletypeTheme,
    strings: app.teletype.sdk.i18n.TeletypeStrings,
    language: String,
    onRate: (ConversationRating, String) -> Unit,
    onRetry: (String) -> Unit,
    onOpenMedia: (String, String) -> Unit,
) {
    if (message.formType == FormType.Rating) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(theme.bubbleRadius.dp))
                .background(theme.operatorBubbleColor)
                .padding(12.dp),
        ) {
            Text(strings.ratingTitle, color = theme.operatorTextColor, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RATINGS.forEach { (value, emoji) ->
                    val selected = message.ratingValue == value
                    Text(
                        text = emoji,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) theme.primaryColor else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable(enabled = message.ratingValue == null) { onRate(value, message.tempId) }
                            .padding(6.dp),
                    )
                }
            }
        }
        return
    }

    val isOwn = message.isOwn
    val kind = resolveMediaKind(message)
    val bubbleColor = if (isOwn) theme.ownBubbleColor else theme.operatorBubbleColor
    val textColor = if (isOwn) theme.ownTextColor else theme.operatorTextColor
    val align = if (isOwn) Alignment.End else Alignment.Start
    val time = formatRelativeTime(message.date?.date, language, strings)
    val status = statusLabel(message, isOwn, time, strings)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = align,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(theme.bubbleRadius.dp))
                .background(if (kind == "image" || kind == "video" || message.emojiOnly) androidx.compose.ui.graphics.Color.Transparent else bubbleColor)
                .clickable(enabled = isOwn && message.status == MessageStatus.Failed) { onRetry(message.tempId) }
                .padding(if (kind == "image" || kind == "video") 0.dp else 10.dp),
        ) {
            when (kind) {
                "image" -> AsyncImage(
                    model = message.attachmentPreviewUrl ?: message.attachmentUrl,
                    contentDescription = message.attachment,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            (message.attachmentUrl ?: message.attachmentPreviewUrl)?.let { onOpenMedia(it, "image") }
                        },
                    contentScale = ContentScale.Crop,
                )
                "video" -> Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(androidx.compose.ui.graphics.Color.Black)
                            .clickable { message.attachmentUrl?.let { onOpenMedia(it, "video") } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("▶ ${strings.mediaVideo}", color = androidx.compose.ui.graphics.Color.White)
                    }
                }
                "audio" -> Text("♪ ${message.attachment ?: strings.mediaAudio}", color = textColor)
                "file" -> Text(
                    "📄 ${message.attachment ?: strings.attachmentFallback}  ${formatBytes(message.size, labels = strings)}",
                    color = textColor,
                    modifier = Modifier.clickable { message.attachmentUrl?.let { openUrl(it) } },
                )
                else -> {
                    val text = message.message.orEmpty()
                    val urls = extractUrls(text)
                    if (urls.isEmpty()) {
                        Text(text, color = textColor, fontSize = if (message.emojiOnly) 32.sp else 16.sp)
                    } else {
                        Text(
                            text = text,
                            color = textColor,
                            modifier = Modifier.clickable { openUrl(urls.first()) },
                        )
                    }
                }
            }
        }
        Text(
            text = status,
            color = theme.mutedColor,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
        )
    }
}

private fun statusLabel(
    message: Message,
    isOwn: Boolean,
    time: String,
    strings: app.teletype.sdk.i18n.TeletypeStrings,
): String {
    if (!isOwn) return time
    when (message.errorMessage) {
        "fileSizeError" -> return strings.fileSizeError
        "fileFormatNotAllowed" -> return strings.fileFormatNotAllowed
        null -> Unit
        else -> return strings.uploadFailed
    }
    val progress = message.progress
    if (progress != null && progress < 100) return "${strings.uploading} ($progress%)"
    return when (message.status) {
        MessageStatus.Sending -> strings.messageSending
        MessageStatus.Failed -> strings.messageFailed
        else -> {
            val state = if (message.seen) strings.messageSeen else strings.messageDelivered
            if (time.isNotEmpty()) "$time. $state" else state
        }
    }
}

private fun resolveMediaKind(message: Message): String? {
    when (message.messageType) {
        MessageType.Image, MessageType.Gif -> return "image"
        MessageType.Video -> return "video"
        MessageType.Audio -> return "audio"
        MessageType.Attachment -> return "file"
        else -> Unit
    }
    val name = message.attachment ?: message.attachmentUrl ?: return null
    val ext = getExtension(name)
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp" -> "image"
        "mp4", "mov", "m4v", "webm", "mkv", "avi", "3gp" -> "video"
        "mp3", "m4a", "aac", "wav", "ogg", "flac", "opus", "amr", "caf" -> "audio"
        else -> if (!message.attachment.isNullOrEmpty()) "file" else null
    }
}

@Composable
private fun ModalSendFile(
    file: LocalFile,
    theme: TeletypeTheme,
    strings: app.teletype.sdk.i18n.TeletypeStrings,
    onClose: () -> Unit,
    onSend: (LocalFile) -> Unit,
) {
    val kind = app.teletype.sdk.config.messageTypeFromFile(file)
    val title = when (kind) {
        MessageType.Image -> strings.sendImage
        MessageType.Video -> strings.sendVideo
        MessageType.Audio -> strings.sendAudio
        else -> strings.sendFile
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(theme.inputBackground)
                .clickable(enabled = false) {}
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = theme.inputTextColor)
            Spacer(Modifier.height(12.dp))
            if (kind == MessageType.Image) {
                AsyncImage(
                    model = file.uri,
                    contentDescription = file.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(file.name, color = theme.inputTextColor)
                Text(formatBytes(file.size, labels = strings), color = theme.mutedColor, fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.borderColor)
                        .clickable(onClick = onClose)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(strings.cancel, color = theme.inputTextColor) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.primaryColor)
                        .clickable { onSend(file) }
                        .padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(strings.send, color = theme.onPrimaryColor, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun MediaPreview(url: String, kind: String, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .clickable(onClick = onClose)
            .statusBarsPadding(),
    ) {
        if (kind == "video") {
            PlatformVideoPlayer(
                url = url,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 56.dp),
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 56.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            text = "✕",
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 22.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clickable(onClick = onClose)
                .padding(16.dp),
        )
    }
}
