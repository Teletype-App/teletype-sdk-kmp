package app.teletype.sdk.example

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.teletype.sdk.config.TeletypeMode
import app.teletype.sdk.config.extractTeletypeExternalId
import app.teletype.sdk.config.isTeletypeExternalId
import app.teletype.sdk.config.looksLikeEmbedSnippet
import app.teletype.sdk.core.TeletypeClient
import app.teletype.sdk.core.TeletypeConfig
import app.teletype.sdk.i18n.getSystemLanguage
import app.teletype.sdk.storage.SettingsStore
import app.teletype.sdk.types.CHAT_BACKGROUND_PICKER
import app.teletype.sdk.types.ChatBackground
import app.teletype.sdk.types.LocalFile
import app.teletype.sdk.types.parseChatBackground
import app.teletype.sdk.ui.CHAT_BUTTON_SCALES
import app.teletype.sdk.ui.ChatScreen
import app.teletype.sdk.ui.ChatWidget
import app.teletype.sdk.ui.TeletypeProvider
import app.teletype.sdk.ui.rememberTeletypeClient
import app.teletype.sdk.ui.rememberTeletypeState
import app.teletype.sdk.ChatBackgroundThumbs
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

private val LOCALE_ORDER = listOf("en", "ru")
private val CHAT_COLORS = listOf("#0084ff", "#34c759", "#ff3b30", "#7c3aed", "#ffcc00")
private const val STORAGE_KEY = "teletype_demo_external_id"
private const val STORAGE_KEY_TITLE = "teletype_demo_chat_title"
private const val STORAGE_KEY_COLOR = "teletype_demo_primary_color"
private const val STORAGE_KEY_BG = "teletype_demo_chat_background"
private const val STORAGE_KEY_BTN_POS = "teletype_demo_button_position"
private const val STORAGE_KEY_BTN_SCALE = "teletype_demo_button_scale"
private const val EXTERNAL_ID = ""

private enum class Session { Boot, Setup, Connecting, Ready }
private enum class SetupError { Format, Snippet, Channel, Connection }

@Composable
fun App() {
    val settings = remember { SettingsStore() }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(Session.Boot) }
    var externalId by remember { mutableStateOf("") }
    var draftId by remember { mutableStateOf("") }
    var setupError by remember { mutableStateOf<SetupError?>(null) }
    var uiLocale by remember { mutableStateOf(detectDeviceLanguage()) }
    var chatTitle by remember { mutableStateOf("") }
    var primaryColor by remember { mutableStateOf("#0084ff") }
    var chatBackground by remember { mutableStateOf(ChatBackground.White) }
    var buttonPosition by remember { mutableStateOf("right") }
    var buttonScale by remember { mutableStateOf(100) }
    var channelClient by remember { mutableStateOf<TeletypeClient?>(null) }
    val t = getDemoStrings(uiLocale)
    val resolvedTitle = chatTitle.trim().ifEmpty { t.chatTitle }

    fun nextLocale(current: String) =
        LOCALE_ORDER[(LOCALE_ORDER.indexOf(current).let { if (it < 0) 0 else it } + 1) % LOCALE_ORDER.size]

    fun dropClient() {
        channelClient?.destroy()
        channelClient = null
    }

    fun persist(key: String, value: String) {
        scope.launch { settings.setItem(key, value) }
    }

    suspend fun verifyAndEnter(id: String) {
        setupError = null
        session = Session.Connecting
        dropClient()
        val client = TeletypeClient(
            TeletypeConfig(
                teletypeExternalId = id,
                mode = TeletypeMode.Dev,
                soundEnabled = true,
                locale = uiLocale,
                translations = SDK_TRANSLATIONS,
                title = resolvedTitle,
                primaryColor = primaryColor,
                chatBackground = chatBackground,
            ),
        )
        try {
            client.init()
            if (client.state.config == null) error("channel")
            client.waitUntilConnected(15_000)
            channelClient = client
            runCatching { settings.setItem(STORAGE_KEY, id) }
            externalId = id
            session = Session.Ready
        } catch (err: Throwable) {
            client.destroy()
            setupError = if (err.message == "channel") SetupError.Channel else SetupError.Connection
            draftId = id
            externalId = ""
            session = Session.Setup
        }
    }

    LaunchedEffect(Unit) {
        var storedId = EXTERNAL_ID
        runCatching {
            if (storedId.isEmpty()) storedId = settings.getItem(STORAGE_KEY).orEmpty()
            settings.getItem(STORAGE_KEY_TITLE)?.let { chatTitle = it }
            settings.getItem(STORAGE_KEY_COLOR)?.let { primaryColor = it }
            parseChatBackground(settings.getItem(STORAGE_KEY_BG))?.let { chatBackground = it }
            settings.getItem(STORAGE_KEY_BTN_POS)?.let { if (it == "left" || it == "right") buttonPosition = it }
            settings.getItem(STORAGE_KEY_BTN_SCALE)?.toIntOrNull()?.let { if (it in CHAT_BUTTON_SCALES) buttonScale = it }
        }
        val parsed = extractTeletypeExternalId(storedId)
        if (parsed.isEmpty()) {
            draftId = storedId
            session = Session.Setup
            return@LaunchedEffect
        }
        if (!isTeletypeExternalId(parsed)) {
            draftId = storedId
            setupError = SetupError.Format
            session = Session.Setup
            return@LaunchedEffect
        }
        draftId = parsed
        verifyAndEnter(parsed)
    }

    when {
        session == Session.Boot || session == Session.Connecting -> ConnectingScreen(uiLocale)
        session != Session.Ready || channelClient == null -> SetupScreen(
            locale = uiLocale,
            value = draftId,
            error = setupError,
            onChangeValue = {
                draftId = it
                setupError = null
            },
            onCycleLocale = { uiLocale = nextLocale(uiLocale) },
            onConnect = { raw ->
                val parsed = extractTeletypeExternalId(raw)
                if (parsed.isEmpty()) {
                    setupError = if (looksLikeEmbedSnippet(raw)) SetupError.Snippet else SetupError.Format
                    return@SetupScreen
                }
                if (!isTeletypeExternalId(parsed)) {
                    setupError = SetupError.Format
                    return@SetupScreen
                }
                draftId = parsed
                scope.launch { verifyAndEnter(parsed) }
            },
        )
        else -> TeletypeProvider(client = channelClient!!) {
            DemoRoot(
                locale = uiLocale,
                onCycleLocale = {
                    val next = nextLocale(uiLocale)
                    uiLocale = next
                    channelClient?.setLocale(next, SDK_TRANSLATIONS)
                },
                onClearCache = {
                    scope.launch {
                        settings.removeMatching { it.startsWith("teletype-") || it == STORAGE_KEY }
                        dropClient()
                        externalId = ""
                        draftId = ""
                        setupError = null
                        session = Session.Setup
                    }
                },
                chatTitle = resolvedTitle,
                onChangeTitle = {
                    chatTitle = it
                    persist(STORAGE_KEY_TITLE, it)
                    channelClient?.setTitle(it.ifBlank { t.chatTitle })
                },
                primaryColor = primaryColor,
                onChangeColor = {
                    primaryColor = it
                    persist(STORAGE_KEY_COLOR, it)
                    channelClient?.setPrimaryColor(it)
                },
                chatBackground = chatBackground,
                onChangeBackground = {
                    chatBackground = it
                    persist(STORAGE_KEY_BG, it.name)
                    channelClient?.setChatBackground(it)
                },
                buttonPosition = buttonPosition,
                onChangePosition = {
                    buttonPosition = it
                    persist(STORAGE_KEY_BTN_POS, it)
                },
                buttonScale = buttonScale,
                onChangeScale = {
                    buttonScale = it
                    persist(STORAGE_KEY_BTN_SCALE, it.toString())
                },
            )
        }
    }
}

@Composable
private fun DemoRoot(
    locale: String,
    onCycleLocale: () -> Unit,
    onClearCache: () -> Unit,
    chatTitle: String,
    onChangeTitle: (String) -> Unit,
    primaryColor: String,
    onChangeColor: (String) -> Unit,
    chatBackground: ChatBackground,
    onChangeBackground: (ChatBackground) -> Unit,
    buttonPosition: String,
    onChangePosition: (String) -> Unit,
    buttonScale: Int,
    onChangeScale: (Int) -> Unit,
) {
    val client = rememberTeletypeClient()
    val state = rememberTeletypeState()
    val t = getDemoStrings(locale)
    val picker = rememberFilePickerLauncher(type = FileKitType.File()) { file: PlatformFile? ->
        pendingPicked.value = file
        pickerSettled.value = true
    }
    var pending by pendingPicked

    if (state.opened) {
        ChatScreen(
            onClose = { client.hide() },
            onAttachmentPress = {
                pending = null
                pickerSettled.value = false
                picker.launch()
                while (!pickerSettled.value) {
                    kotlinx.coroutines.delay(50)
                }
                val file = pending ?: return@ChatScreen null
                pending = null
                LocalFile(
                    uri = file.path,
                    name = file.name,
                    type = mimeFromName(file.name),
                    size = runCatching { file.size() }.getOrNull(),
                )
            },
        )
        return
    }

    Box(Modifier.fillMaxSize().background(Color.White).imePadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .padding(top = 36.dp, bottom = 128.dp),
        ) {
            Text(t.screenTitle, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF111111))
            Text(
                t.screenSubtitle.replace("{unread}", state.unreadCount.toString()),
                fontSize = 15.sp,
                color = Color(0xFF666666),
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
            )
            AppearanceFields(
                locale = locale,
                chatTitle = chatTitle,
                onChangeTitle = onChangeTitle,
                primaryColor = primaryColor,
                onChangeColor = onChangeColor,
                chatBackground = chatBackground,
                onChangeBackground = onChangeBackground,
                buttonPosition = buttonPosition,
                onChangePosition = onChangePosition,
                buttonScale = buttonScale,
                onChangeScale = onChangeScale,
            )
            DemoButton(t.openChat, Color(0xFF0084FF), onClick = { client.show() })
            DemoButton(t.clearCache, Color(0xFFE5352B), onClick = onClearCache)
            OutlineButton(t.languageButton.replace("{lang}", locale.uppercase()), onCycleLocale)
        }
        ChatWidget(position = buttonPosition, scale = buttonScale, bottom = 40)
    }
}

private val pendingPicked = mutableStateOf<PlatformFile?>(null)
private val pickerSettled = mutableStateOf(false)

@Composable
private fun AppearanceFields(
    locale: String,
    chatTitle: String,
    onChangeTitle: (String) -> Unit,
    primaryColor: String,
    onChangeColor: (String) -> Unit,
    chatBackground: ChatBackground,
    onChangeBackground: (ChatBackground) -> Unit,
    buttonPosition: String,
    onChangePosition: (String) -> Unit,
    buttonScale: Int,
    onChangeScale: (Int) -> Unit,
) {
    val t = getDemoStrings(locale)
    FieldLabel(t.chatTitleLabel)
    DemoInput(chatTitle, t.chatTitlePlaceholder, onChangeTitle)
    FieldLabel(t.chatColorLabel)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 20.dp)) {
        CHAT_COLORS.forEach { color ->
            val selected = color.equals(primaryColor, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(parseColor(color))
                    .clickable { onChangeColor(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Text("✓", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    FieldLabel(t.chatBackgroundLabel)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 20.dp)) {
        CHAT_BACKGROUND_PICKER.forEach { id ->
            val selected = id == chatBackground
            val thumb = when (id) {
                ChatBackground.Doodle -> ChatBackgroundThumbs.doodle
                ChatBackground.Circles -> ChatBackgroundThumbs.circles
                ChatBackground.Scribbles -> ChatBackgroundThumbs.scribbles
                ChatBackground.White -> null
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.5.dp, if (selected) Color(0xFF0084FF) else Color(0xFFD0D5DA), RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .clickable { onChangeBackground(id) },
            ) {
                if (thumb != null) {
                    Image(
                        painter = painterResource(thumb),
                        contentDescription = id.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
    FieldLabel(t.buttonPositionLabel)
    DemoSelect(
        title = t.buttonPositionLabel,
        value = if (buttonPosition == "left") t.buttonPositionLeft else t.buttonPositionRight,
        options = listOf(
            t.buttonPositionRight to "right",
            t.buttonPositionLeft to "left",
        ),
        onChange = onChangePosition,
    )
    FieldLabel(t.buttonScaleLabel)
    DemoSelect(
        title = t.buttonScaleLabel,
        value = "$buttonScale%",
        options = CHAT_BUTTON_SCALES.reversed().map { "$it%" to it },
        onChange = onChangeScale,
    )
}

@Composable
private fun <T> DemoSelect(
    title: String,
    value: String,
    options: List<Pair<String, T>>,
    onChange: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .height(48.dp)
            .border(1.dp, Color(0xFFD0D5DA), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable { open = true }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(value, fontSize = 15.sp, color = Color(0xFF111111), modifier = Modifier.weight(1f))
            Text("▼", fontSize = 10.sp, color = Color(0xFF111111))
        }
    }
    if (open) {
        Dialog(
            onDismissRequest = { open = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { open = false },
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .widthIn(max = 340.dp)
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111111),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                    )
                    options.forEach { (label, item) ->
                        val active = label == value
                        Text(
                            label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (active) Color(0xFFF3F4F6) else Color.Transparent)
                                .clickable {
                                    onChange(item)
                                    open = false
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            fontSize = 16.sp,
                            color = Color(0xFF111111),
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(
    locale: String,
    value: String,
    error: SetupError?,
    onChangeValue: (String) -> Unit,
    onCycleLocale: () -> Unit,
    onConnect: (String) -> Unit,
) {
    val t = getDemoStrings(locale)
    val parsedId = extractTeletypeExternalId(value)
    val idLooksValid = isTeletypeExternalId(parsedId)
    val canSubmit = value.trim().isNotEmpty()
    val errorText = when (error) {
        SetupError.Snippet -> t.snippetMissingId
        SetupError.Channel -> t.invalidChannel
        SetupError.Connection -> t.connectionFailed
        SetupError.Format -> t.invalidExternalId
        null -> ""
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .padding(top = 36.dp),
    ) {
        Text(t.screenTitle, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF111111))
        Text(t.setupTitle, fontSize = 15.sp, color = Color(0xFF666666), modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
        Text(t.setupHint, fontSize = 14.sp, color = Color(0xFF888888), modifier = Modifier.padding(bottom = 20.dp))
        OutlineButton(t.languageButton.replace("{lang}", locale.uppercase()), onCycleLocale)
        DemoInput(
            value = value,
            placeholder = t.externalIdPlaceholder,
            onChange = onChangeValue,
            minHeight = 125,
            error = error != null,
        )
        if (idLooksValid) {
            Text(t.parsedId.replace("{id}", parsedId), fontSize = 13.sp, color = Color(0xFF5F6368), modifier = Modifier.padding(bottom = 12.dp))
        }
        if (errorText.isNotEmpty()) {
            Text(errorText, color = Color(0xFFE5352B), fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
        }
        DemoButton(t.connect, Color(0xFF0084FF), enabled = canSubmit, onClick = { onConnect(value) })
    }
}

@Composable
private fun ConnectingScreen(locale: String) {
    val t = getDemoStrings(locale)
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFF0084FF))
            Spacer(Modifier.height(16.dp))
            Text(t.connecting, fontSize = 16.sp, color = Color(0xFF666666))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111), modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun DemoInput(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    minHeight: Int = 48,
    error: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(minHeight.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (error) Color(0xFFE5352B) else Color(0xFFD0D5DA), RoundedCornerShape(12.dp))
            .padding(14.dp)
            .padding(bottom = 16.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(color = Color(0xFF111111), fontSize = 15.sp),
            modifier = Modifier.fillMaxSize(),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, color = Color(0xFF9AA0A6), fontSize = 15.sp)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun DemoButton(text: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OutlineButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF0084FF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFF0084FF), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun detectDeviceLanguage(): String {
    val system = getSystemLanguage()
    return if (system == "ru") "ru" else "en"
}

private fun parseColor(hex: String): Color {
    val clean = hex.removePrefix("#")
    val value = clean.toLongOrNull(16) ?: return Color(0xFF0084FF)
    return Color(0xFF000000.toInt() or value.toInt())
}

private fun mimeFromName(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }
}
