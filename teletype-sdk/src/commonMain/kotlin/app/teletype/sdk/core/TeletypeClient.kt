package app.teletype.sdk.core

import app.teletype.sdk.api.ApiClient
import app.teletype.sdk.config.APPEAL_STATUS_CLOSED
import app.teletype.sdk.config.CONFIG_LIFETIME_MS
import app.teletype.sdk.config.DEFAULT_CHAT_TITLE
import app.teletype.sdk.config.DEFAULT_MODE
import app.teletype.sdk.config.DEFAULT_PRIMARY_COLOR
import app.teletype.sdk.config.MAX_UPLOAD_BYTES
import app.teletype.sdk.config.RECEIVED_ACK_TIMEOUT_MS
import app.teletype.sdk.config.StorageKeys
import app.teletype.sdk.config.TeletypeEnvironment
import app.teletype.sdk.config.TeletypeMode
import app.teletype.sdk.config.WsEvent
import app.teletype.sdk.config.getEnvironment
import app.teletype.sdk.config.isAllowedUploadFile
import app.teletype.sdk.config.messageTypeFromFile
import app.teletype.sdk.events.EventEmitter
import app.teletype.sdk.events.TeletypeEvents
import app.teletype.sdk.i18n.TeletypeStrings
import app.teletype.sdk.i18n.TeletypeTranslations
import app.teletype.sdk.i18n.resolveStrings
import app.teletype.sdk.json.TeletypeJson
import app.teletype.sdk.json.camelKeys
import app.teletype.sdk.json.coerceWidgetConfigLists
import app.teletype.sdk.json.snakeKeys
import app.teletype.sdk.platform.currentTimeMillis
import app.teletype.sdk.socket.SocketClient
import app.teletype.sdk.storage.KeyValueStore
import app.teletype.sdk.storage.TeletypeStorage
import app.teletype.sdk.types.AdditionalPersonInfo
import app.teletype.sdk.types.ChannelMessenger
import app.teletype.sdk.types.ChatBackground
import app.teletype.sdk.types.ConversationRating
import app.teletype.sdk.types.DEFAULT_CHAT_BACKGROUND
import app.teletype.sdk.types.DateObject
import app.teletype.sdk.types.DialogClosedPayload
import app.teletype.sdk.types.DialogRatedPayload
import app.teletype.sdk.types.FormType
import app.teletype.sdk.types.LocalFile
import app.teletype.sdk.types.Message
import app.teletype.sdk.types.MessageStatus
import app.teletype.sdk.types.Operator
import app.teletype.sdk.types.UploadedAttachment
import app.teletype.sdk.types.WidgetConfigData
import app.teletype.sdk.util.guid
import app.teletype.sdk.util.isEmojiOnly
import app.teletype.sdk.util.uniqueBy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class TeletypeConfig(
    val teletypeExternalId: String,
    val mode: TeletypeMode = DEFAULT_MODE,
    val environment: TeletypeEnvironment? = null,
    val storage: KeyValueStore? = null,
    val soundEnabled: Boolean = false,
    val locale: String? = null,
    val translations: TeletypeTranslations? = null,
    val title: String? = null,
    val primaryColor: String? = null,
    val chatBackground: ChatBackground = DEFAULT_CHAT_BACKGROUND,
    val device: Map<String, String> = emptyMap(),
)

data class TeletypeState(
    val ready: Boolean = false,
    val opened: Boolean = false,
    val connection: String = "disconnected",
    val messages: List<Message> = emptyList(),
    val operators: List<Operator> = emptyList(),
    val channels: List<ChannelMessenger> = emptyList(),
    val config: WidgetConfigData? = null,
    val unreadCount: Int = 0,
    val operatorTyping: Boolean = false,
    val draft: String = "",
    val title: String = DEFAULT_CHAT_TITLE,
    val primaryColor: String = DEFAULT_PRIMARY_COLOR,
    val chatBackground: ChatBackground = DEFAULT_CHAT_BACKGROUND,
    val locale: String = "en",
    val strings: TeletypeStrings,
)

/**
 * Headless, framework-agnostic chat engine. Owns the socket + REST lifecycle
 * and all conversation state. Logic mirrors the Teletype web widget / RN SDK.
 */
class TeletypeClient(config: TeletypeConfig) {
    val externalId: String = config.teletypeExternalId
    val env: TeletypeEnvironment = getEnvironment(config.mode).let { base ->
        config.environment ?: base
    }.let { base ->
        if (config.environment == null) base else TeletypeEnvironment(
            mode = config.environment.mode.ifBlank { base.mode },
            version = config.environment.version.ifBlank { base.version },
            apiUrl = config.environment.apiUrl.ifBlank { base.apiUrl },
            wsUrl = config.environment.wsUrl.ifBlank { base.wsUrl },
            assetsUrl = config.environment.assetsUrl.ifBlank { base.assetsUrl },
            apiVersion = config.environment.apiVersion.ifBlank { base.apiVersion },
            landingUrl = config.environment.landingUrl.ifBlank { base.landingUrl },
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val emitter = EventEmitter()
    private val api = ApiClient(env, externalId)
    private val socket = SocketClient(env.wsUrl, debug = env.mode == "dev")
    private val storage = TeletypeStorage(externalId, config.storage ?: app.teletype.sdk.storage.SettingsStore())

    private var translations = config.translations
    private var localeInternal: String = "en"
    private var stringsInternal: TeletypeStrings = app.teletype.sdk.i18n.EN_STRINGS
    private var titleInternal: String =
        if (config.title != null) config.title else DEFAULT_CHAT_TITLE
    private var primaryColorInternal: String? = config.primaryColor
    private var chatBackgroundInternal: ChatBackground = config.chatBackground
    private var soundEnabled: Boolean = config.soundEnabled
    private val device: Map<String, String> = config.device

    private var readyFlag = false
    private var openedFlag = false
    private var connectionState = "disconnected"
    private var messages: List<Message> = emptyList()
    private var operators: List<Operator> = emptyList()
    private var channels: List<ChannelMessenger> = emptyList()
    private var widgetConfig: WidgetConfigData? = null
    private var unreadCount = 0
    private var operatorTyping = false
    private var draft = ""

    private var personId = ""
    private var appealIdInternal = ""
    private var personData: Map<String, Any?>? = null
    private var personAdditionalInfo = AdditionalPersonInfo()
    private var greetingSent = false
    private var messageWasSent = false
    private var banned = false
    private var pendingMessages: MutableList<Message> = mutableListOf()
    private var messagesInPending: MutableList<Message> = mutableListOf()
    private val receivedJobs = mutableMapOf<String, Job>()
    private var initialized = false
    private var listenersBound = false

    val appealId: String get() = appealIdInternal
    val locale: String get() = localeInternal
    val strings: TeletypeStrings get() = stringsInternal
    val title: String get() = titleInternal
    val primaryColor: String
        get() = primaryColorInternal ?: widgetConfig?.backgroundColor ?: DEFAULT_PRIMARY_COLOR
    val chatBackground: ChatBackground get() = chatBackgroundInternal

    private val _state = MutableStateFlow(
        run {
            val resolved = resolveStrings(config.locale, translations)
            localeInternal = resolved.first
            stringsInternal = resolved.second
            snapshot()
        },
    )
    val stateFlow: StateFlow<TeletypeState> = _state.asStateFlow()
    val state: TeletypeState get() = _state.value

    init {
        val resolved = resolveStrings(config.locale, translations)
        localeInternal = resolved.first
        stringsInternal = resolved.second
        publishState()
    }

    fun on(event: String, listener: (Any?) -> Unit): () -> Unit = emitter.on(event, listener)
    fun once(event: String, listener: (Any?) -> Unit): () -> Unit = emitter.once(event, listener)
    fun off(event: String, listener: (Any?) -> Unit) = emitter.off(event, listener)

    fun getMessages(): List<Message> = messages

    fun setLocale(locale: String? = null, translations: TeletypeTranslations? = null) {
        if (translations != null) {
            this.translations = (this.translations ?: emptyMap()) + translations
        }
        val resolved = resolveStrings(locale, this.translations)
        localeInternal = resolved.first
        stringsInternal = resolved.second
        publishState()
        emitter.emit(TeletypeEvents.LOCALE_CHANGED, localeInternal)
    }

    fun setTitle(title: String) {
        titleInternal = title
        publishState()
        emitter.emit(TeletypeEvents.APPEARANCE_CHANGED)
    }

    fun setPrimaryColor(color: String?) {
        primaryColorInternal = color
        publishState()
        emitter.emit(TeletypeEvents.APPEARANCE_CHANGED)
    }

    fun setChatBackground(background: ChatBackground) {
        chatBackgroundInternal = background
        publishState()
        emitter.emit(TeletypeEvents.APPEARANCE_CHANGED)
    }

    suspend fun init() {
        if (initialized) return
        initialized = true
        storage.hydrate(
            listOf(
                StorageKeys.PERSON_ID,
                StorageKeys.APPEAL_ID,
                StorageKeys.CONFIG,
                StorageKeys.CONFIG_SAVE_TIME,
                StorageKeys.PENDING_MESSAGES,
                StorageKeys.POPUP_STATE,
                StorageKeys.WIDGET_INTERACTED,
            ),
        )
        personId = storage.getString(StorageKeys.PERSON_ID)
        appealIdInternal = storage.getString(StorageKeys.APPEAL_ID)
        pendingMessages = decodeMessages(storage.getJson(StorageKeys.PENDING_MESSAGES)).map {
            it.copy(status = MessageStatus.Failed)
        }.toMutableList()

        try {
            applyConfig(loadConfig())
        } catch (err: Throwable) {
            emitter.emit(TeletypeEvents.ERROR, err)
        }
        if (appealIdInternal.isNotEmpty()) {
            loadHistory()
        }
        readyFlag = true
        publishState()
        emitter.emit(TeletypeEvents.READY)
        publishMessages()
        if (appealIdInternal.isNotEmpty() || pendingMessages.isNotEmpty()) {
            ensureConnected()
        }
    }

    fun ensureConnected() {
        socket.ensureCreated()
        if (!listenersBound) {
            bindSocket()
            listenersBound = true
        }
        socket.connect()
    }

    suspend fun waitUntilConnected(timeoutMs: Long = 15_000) {
        if (connectionState == "connected") return
        val done = CompletableDeferred<Unit>()
        val off = on(TeletypeEvents.CONNECTION_CHANGED) { state ->
            if (state == "connected") done.complete(Unit)
        }
        ensureConnected()
        if (connectionState == "connected") {
            off()
            return
        }
        try {
            withTimeout(timeoutMs) { done.await() }
        } finally {
            off()
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        handleSendMessage(
            Message(
                message = trimmed,
                isOwn = true,
                date = nowDate(),
                emojiOnly = isEmojiOnly(trimmed),
                messageType = app.teletype.sdk.types.MessageType.Text,
                tempId = guid(),
            ),
        )
    }

    fun sendAttachment(attachment: UploadedAttachment) {
        handleSendMessage(
            Message(
                isOwn = true,
                date = nowDate(),
                attachment = attachment.attachment,
                attachmentId = attachment.attachmentId,
                attachmentUrl = attachment.attachmentUrl,
                width = attachment.width,
                height = attachment.height,
                size = attachment.size,
                messageType = attachment.type,
                tempId = guid(),
            ),
            save = false,
        )
    }

    suspend fun uploadAttachment(
        file: LocalFile,
        onProgress: ((Int) -> Unit)? = null,
    ): UploadedAttachment {
        if (file.size != null && file.size > MAX_UPLOAD_BYTES) {
            val error = IllegalStateException("fileSizeError")
            emitter.emit(TeletypeEvents.ERROR, error)
            throw error
        }
        if (!isAllowedUploadFile(file)) {
            val error = IllegalStateException("fileFormatNotAllowed")
            emitter.emit(TeletypeEvents.ERROR, error)
            throw error
        }
        val res = try {
            api.uploadFile(file, onProgress)
        } catch (e: Throwable) {
            val error = if (e is Exception) e else IllegalStateException("uploadFailed", e)
            emitter.emit(TeletypeEvents.ERROR, error)
            throw error
        }
        val obj = res as? JsonObject ?: JsonObject(emptyMap())
        if (obj["success"]?.jsonPrimitive?.booleanOrNull == false) {
            val detail = obj["error"]?.let {
                when (it) {
                    is JsonArray -> it.joinToString(", ") { el -> el.jsonPrimitive.contentOrNull ?: el.toString() }
                    is JsonPrimitive -> it.content
                    else -> "uploadFailed"
                }
            } ?: "uploadFailed"
            val error = IllegalStateException(detail)
            emitter.emit(TeletypeEvents.ERROR, error)
            throw error
        }
        val data = (obj["data"] as? JsonObject) ?: JsonObject(emptyMap())
        val attachmentId = data.str("attachmentId") ?: data.str("attachment_id").orEmpty()
        val attachmentUrl = data.str("attachment") ?: data.str("attachmentUrl") ?: file.uri
        return UploadedAttachment(
            attachment = file.name,
            attachmentId = attachmentId,
            attachmentUrl = attachmentUrl,
            type = messageTypeFromFile(file),
            size = file.size,
            width = file.width,
            height = file.height,
        )
    }

    suspend fun uploadFile(file: LocalFile) {
        if (file.size != null && file.size > MAX_UPLOAD_BYTES) {
            addAttachmentError(file, "fileSizeError")
            val error = IllegalStateException("fileSizeError")
            emitter.emit(TeletypeEvents.ERROR, error)
            throw error
        }
        if (!isAllowedUploadFile(file)) {
            addAttachmentError(file, "fileFormatNotAllowed")
            val error = IllegalStateException("fileFormatNotAllowed")
            emitter.emit(TeletypeEvents.ERROR, error)
            throw error
        }
        val localId = "${file.name}_${file.size ?: 0}_${currentTimeMillis()}_${guid().take(8)}"
        val preview = Message(
            message = "",
            messageType = messageTypeFromFile(file),
            progress = 0,
            attachmentId = localId,
            tempId = guid(),
            isOwn = true,
            attachment = file.name,
            attachmentPreviewUrl = file.previewUri
                ?: if (messageTypeFromFile(file) == app.teletype.sdk.types.MessageType.Image) file.uri else null,
            attachmentUrl = file.uri,
            date = nowDate(),
            size = file.size,
            width = file.width,
            height = file.height,
            status = MessageStatus.Sending,
        )
        messages = messages + preview
        setMessages(messages)
        try {
            val uploaded = uploadAttachment(file) { pct -> patchAttachment(localId, progress = pct) }
            patchAttachment(
                localId,
                progress = 100,
                attachmentUrl = uploaded.attachmentUrl,
                attachmentIdNew = uploaded.attachmentId,
            )
            sendAttachment(uploaded)
        } catch (e: Throwable) {
            val code = e.message ?: "uploadFailed"
            patchAttachment(localId, errorMessage = code, progress = 100, status = MessageStatus.Failed)
            throw e
        }
    }

    fun retryMessage(tempId: String) {
        val message = messages.find { it.tempId == tempId } ?: return
        val next = message.copy(status = MessageStatus.Sending, errorMessage = null)
        messages = messages.map { if (it.tempId == tempId) next else it }
        setMessages(messages)
        handleSendMessage(next, save = false)
    }

    fun setPersonData(info: AdditionalPersonInfo) {
        personAdditionalInfo = AdditionalPersonInfo(
            name = info.name ?: personAdditionalInfo.name,
            email = info.email ?: personAdditionalInfo.email,
            avatar = info.avatar ?: personAdditionalInfo.avatar,
            phone = info.phone ?: personAdditionalInfo.phone,
            payload = personAdditionalInfo.payload + info.payload,
        )
        if (personId.isNotEmpty()) {
            scope.launch { sendPersonData() }
        }
    }

    fun greetingMessages(messages: List<String>, fromBot: Boolean = true) {
        if (greetingSent) return
        greetingSent = true
        val operatorAvatar = if (!fromBot) operators.firstOrNull()?.avatar else null
        messages.forEach { text ->
            handleIncomingMessage(
                Message(
                    message = text,
                    tempId = guid(),
                    serviceMessage = true,
                    avatar = operatorAvatar,
                    from = app.teletype.sdk.types.MessageFrom(avatar = operatorAvatar),
                ),
                unread = false,
            )
        }
    }

    fun setMessage(text: String) {
        draft = text
        publishState()
        emitter.emit(TeletypeEvents.DRAFT_CHANGED, text)
    }

    fun show() = setOpened(true)
    fun hide() = setOpened(false)
    fun toggle() = setOpened(!openedFlag)

    fun rateAppeal(value: ConversationRating, formTempId: String? = null) {
        socket.emit(
            WsEvent.PERSON_RATE_APPEAL,
            snakeKeys(mapOf("appealId" to appealIdInternal, "data" to mapOf("value" to value.wire))),
        )
        if (formTempId != null) {
            messages = messages.map {
                if (it.tempId == formTempId) it.copy(ratingValue = value) else it
            }
            setMessages(messages)
        }
        emitter.emit(TeletypeEvents.DIALOG_RATED, DialogRatedPayload(formTempId.orEmpty(), value.wire))
    }

    fun clickChannel(channel: ChannelMessenger) {
        emitter.emit(TeletypeEvents.CHANNEL_CLICKED, channel)
        scope.launch {
            runCatching {
                val ip = api.getIp()
                socket.emit(
                    WsEvent.PERSON_CLICK_WIDGET_CHANNEL,
                    snakeKeys(
                        mapOf(
                            "widgetId" to externalId,
                            "personId" to personId.ifEmpty { "" },
                            "appealId" to appealIdInternal.ifEmpty { "" },
                            "widgetChannelId" to (channel.id ?: ""),
                            "data" to mapOf("value" to channel.name, "ipAddress" to ip.jsonObject.str("data")),
                        ),
                    ),
                )
            }
        }
    }

    fun markSeen() {
        if (appealIdInternal.isNotEmpty()) {
            socket.emit(WsEvent.SEEN, snakeKeys(mapOf("appealId" to appealIdInternal)))
        }
    }

    fun startTyping(message: String = "") {
        if (socket.connected) {
            socket.emit(
                WsEvent.PERSON_TYPING,
                snakeKeys(mapOf("appealId" to appealIdInternal, "message" to message)),
            )
        }
    }

    fun stopTyping() {
        if (socket.connected) {
            socket.emit(
                WsEvent.PERSON_STOP_TYPING,
                snakeKeys(mapOf("appealId" to appealIdInternal)),
            )
        }
    }

    fun notifyLocationChange(location: String) {
        scope.launch { runCatching { api.notifyLocationChange(location) } }
    }

    fun destroy() {
        receivedJobs.values.forEach { it.cancel() }
        receivedJobs.clear()
        socket.off()
        socket.disconnect()
        emitter.removeAllListeners()
        initialized = false
        listenersBound = false
    }

    private fun snapshot(): TeletypeState = TeletypeState(
        ready = readyFlag,
        opened = openedFlag,
        connection = connectionState,
        messages = messages,
        operators = operators,
        channels = channels,
        config = widgetConfig,
        unreadCount = unreadCount,
        operatorTyping = operatorTyping,
        draft = draft,
        title = titleInternal,
        primaryColor = primaryColor,
        chatBackground = chatBackgroundInternal,
        locale = localeInternal,
        strings = stringsInternal,
    )

    private fun publishState() {
        _state.value = snapshot()
    }

    private suspend fun loadConfig(): JsonElement {
        val saveTime = storage.getLong(StorageKeys.CONFIG_SAVE_TIME, 0)
        val cached = storage.getJson(StorageKeys.CONFIG)
        val expired = currentTimeMillis() - saveTime > CONFIG_LIFETIME_MS
        if (cached != null && !expired) return cached
        val fresh = api.getConfig()
        if (isUsableConfig(fresh)) {
            storage.set(StorageKeys.CONFIG, fresh)
            storage.set(StorageKeys.CONFIG_SAVE_TIME, currentTimeMillis())
        }
        return fresh
    }

    private fun applyConfig(response: JsonElement) {
        if (!isUsableConfig(response)) return
        val dataEl = (response as JsonObject)["data"] ?: return
        val camel = coerceWidgetConfigLists(camelKeys(dataEl))
        val data = runCatching {
            TeletypeJson.decodeFromJsonElement(WidgetConfigData.serializer(), camel)
        }.getOrElse {
            val obj = camel as? JsonObject
            WidgetConfigData(
                name = obj?.str("name").orEmpty(),
                projectName = obj?.str("projectName").orEmpty(),
                projectId = obj?.str("projectId").orEmpty(),
                language = obj?.str("language"),
                backgroundColor = obj?.str("backgroundColor"),
                backgroundImage = obj?.str("backgroundImage"),
                position = obj?.str("position"),
            )
        }
        widgetConfig = data
        operators = data.operators.filter { it.name.isNotBlank() }
        channels = data.channels
        if (data.soundEnabled != null) soundEnabled = data.soundEnabled
        publishState()
        emitter.emit(TeletypeEvents.CONFIG, data)
    }

    private suspend fun loadHistory() {
        try {
            val res = api.getHistory(appealIdInternal)
            val historyEl = (res as? JsonObject)?.get("data")
            val history = decodeMessages(historyEl).map { m ->
                m.copy(
                    unread = if (m.seen) false else m.unread,
                    emojiOnly = isEmojiOnly((m.message ?: "").trim()),
                )
            }
            setMessages(history.reversed() + pendingMessages, fromHistory = true)
        } catch (err: Throwable) {
            emitter.emit(TeletypeEvents.ERROR, err)
        }
    }

    private fun bindSocket() {
        socket.off()
        socket.on(WsEvent.CONNECT) {
            connectionState = "connected"
            publishState()
            emitter.emit(TeletypeEvents.CONNECTION_CHANGED, "connected")
            handleConnect()
        }
        socket.on(WsEvent.DISCONNECT) {
            connectionState = "disconnected"
            publishState()
            emitter.emit(TeletypeEvents.CONNECTION_CHANGED, "disconnected")
        }
        socket.on(WsEvent.INIT_DATA_TO_PERSON) { raw ->
            val data = raw as? JsonObject ?: return@on
            data.str("person_id")?.let { id ->
                personId = id
                storage.set(StorageKeys.PERSON_ID, personId)
                personData?.let { setUserData(it) }
            }
            data.str("appeal_id")?.let { id ->
                if (appealIdInternal.isEmpty() && messagesInPending.isNotEmpty()) {
                    messagesInPending.forEach { emitMessage(it) }
                    messagesInPending.clear()
                }
                setAppealId(id)
            }
        }
        socket.on(WsEvent.MESSAGE_FROM_CLIENT) { raw ->
            val data = decodeMessage(camelKeys(raw ?: JsonNull))
            handleIncomingMessage(data)
            emitter.emit(TeletypeEvents.MESSAGE_RECEIVED, data)
        }
        socket.on(WsEvent.OPERATOR_TYPING) {
            setOperatorTyping(true)
        }
        socket.on(WsEvent.OPERATOR_STOP_TYPING) {
            setOperatorTyping(false)
        }
        socket.on(WsEvent.OPERATOR_STATUS_CHANGE) { raw ->
            val data = raw as? JsonObject ?: return@on
            val clientId = data.str("client_id") ?: return@on
            val online = data["online"]?.jsonPrimitive?.booleanOrNull ?: false
            operators = operators.map { if (it.id == clientId) it.copy(isOnline = online) else it }
            publishState()
        }
        socket.on(WsEvent.APPEAL_STATUS_CHANGE) { raw ->
            val data = raw as? JsonObject ?: return@on
            val status = data["status"]?.jsonPrimitive?.intOrNull
            if (status == APPEAL_STATUS_CLOSED && !banned) {
                createRatingForm()
                emitter.emit(TeletypeEvents.DIALOG_CLOSED, DialogClosedPayload(status))
            }
        }
        socket.on(WsEvent.RECEIVED) { raw ->
            handleReceived(raw as? JsonObject ?: return@on)
        }
        socket.on(WsEvent.SEEN) { raw ->
            handleSeen(raw as? JsonObject ?: return@on)
        }
    }

    private fun handleConnect() {
        if (personId.isNotEmpty()) {
            scope.launch {
                delay(1_000)
                socket.emit(
                    WsEvent.AUTH_PERSON,
                    snakeKeys(mapOf("widgetId" to externalId, "id" to personId)),
                )
            }
        }
    }

    private fun handleIncomingMessage(data: Message, unread: Boolean = true) {
        setOperatorTyping(false)
        val dated = data.copy(
            date = data.date ?: nowDate(),
            unread = unread && !openedFlag,
            isOwn = false,
            emojiOnly = data.emojiOnly || isEmojiOnly((data.message ?: "").trim()),
            message = data.originalMessage ?: data.message,
        )
        messages = messages + dated
        setMessages(messages)
        if (!dated.appealId.isNullOrEmpty() && dated.appealId != appealIdInternal) {
            setAppealId(dated.appealId)
            emitter.emit(TeletypeEvents.APPEAL_STARTED, dated)
        }
    }

    private fun handleReceived(data: JsonObject) {
        banned = data["banned"]?.jsonPrimitive?.booleanOrNull == true
        val tempId = data.str("temp_id") ?: data.str("tempId") ?: return
        val newId = data.str("id")
        val appealId = data.str("appeal_id") ?: data.str("appealId")
        receivedJobs.remove(tempId)?.cancel()
        if (!appealId.isNullOrEmpty() && appealIdInternal != appealId) {
            setAppealId(appealId)
        }
        messages = messages.map { m ->
            if (!m.fromInvite && m.tempId == tempId) {
                m.copy(
                    id = newId ?: m.id,
                    tempId = newId ?: m.tempId,
                    status = MessageStatus.Sent,
                    appealId = appealIdInternal,
                    date = nowDate(),
                )
            } else m
        }
        removePending(tempId)
        setMessages(messages)
    }

    private fun handleSeen(data: JsonObject) {
        val ids = (data["ids"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        val appeal = data.str("appeal_id")
        messages = messages.map { m ->
            val byId = m.id in ids || m.tempId in ids
            val byAppeal = ids.isEmpty() && !appeal.isNullOrEmpty() && m.appealId == appeal
            if (byId || byAppeal) m.copy(seen = true, unread = false) else m
        }
        setMessages(messages)
    }

    private fun handleSendMessage(messageIn: Message, save: Boolean = true) {
        ensureConnected()
        if (!messageWasSent) {
            messageWasSent = true
            scope.launch { sendPersonData() }
        }
        var message = messageIn.copy(
            status = if (banned) MessageStatus.Sent else MessageStatus.Sending,
            tempId = guid(),
        )
        val userMessages = messages.filter {
            it.isOwn && it.errorMessage == null && it.attachment == null &&
                it.status != MessageStatus.Failed && it.tempId != message.tempId
        }
        val attachmentMessages = messages.filter {
            it.isOwn && it.errorMessage == null && it.attachment != null &&
                it.status != MessageStatus.Failed && it.tempId != message.tempId
        }
        val last = messages.lastOrNull()
        val hasRating = last?.formType == FormType.Rating
        val isFirstMessage = appealIdInternal.isEmpty() && (
            (userMessages.isEmpty() && attachmentMessages.isEmpty()) ||
                hasRating ||
                (!message.attachment.isNullOrEmpty() &&
                    attachmentMessages.isNotEmpty() &&
                    message.attachment == attachmentMessages[0].attachment)
            )

        if (message.messageType != null &&
            message.messageType != app.teletype.sdk.types.MessageType.Text &&
            !message.attachmentId.isNullOrEmpty()
        ) {
            messages = messages.map {
                if (it.attachmentId == message.attachmentId) it.copy(tempId = message.tempId) else it
            }
        }
        if (save) {
            messages = messages + message
        }
        setMessages(messages)
        pushPending(message)
        if (isFirstMessage || appealIdInternal.isNotEmpty()) {
            emitMessage(message)
        } else {
            messagesInPending += message
        }
        if (isFirstMessage) emitter.emit(TeletypeEvents.DIALOG_STARTED, message)
        emitter.emit(TeletypeEvents.MESSAGE_SENT, message)
    }

    private fun emitMessage(message: Message) {
        ensureConnected()
        val payload = messageToPayload(message)
        socket.emit(
            WsEvent.MESSAGE_FROM_PERSON,
            snakeKeys(
                mapOf(
                    "external_id" to externalId,
                    "message" to payload,
                    "person_id" to (personId.ifEmpty { storage.getString(StorageKeys.PERSON_ID) }),
                    "appeal_id" to appealIdInternal,
                ),
            ),
        )
        armReceivedTimeout(message)
    }

    private fun armReceivedTimeout(message: Message) {
        if (banned || message.tempId.isEmpty()) return
        receivedJobs.remove(message.tempId)?.cancel()
        receivedJobs[message.tempId] = scope.launch {
            delay(RECEIVED_ACK_TIMEOUT_MS)
            runCatching {
                api.reportError(
                    mapOf(
                        "error" to "Did not receive \"received\" event from websockets",
                        "widgetId" to externalId,
                        "appealId" to appealIdInternal,
                        "personId" to personId,
                    ),
                )
            }
            messages = messages.map {
                if (it.tempId == message.tempId) it.copy(status = MessageStatus.Failed) else it
            }
            setMessages(messages)
        }
    }

    private fun setOpened(value: Boolean) {
        if (openedFlag == value) return
        openedFlag = value
        storage.set(StorageKeys.POPUP_STATE, value)
        publishState()
        emitter.emit(TeletypeEvents.OPEN_CHANGED, value)
        if (value) {
            messages = messages.map {
                if (!it.isOwn && it.unread) it.copy(unread = false, seen = true) else it
            }
            setMessages(messages)
            ensureConnected()
            storage.set(StorageKeys.WIDGET_INTERACTED, true)
            scope.launch {
                runCatching {
                    val ip = api.getIp()
                    socket.emit(
                        WsEvent.PERSON_OPENED_WIDGET,
                        snakeKeys(
                            mapOf(
                                "channel_id" to externalId,
                                "ipAddress" to ip.jsonObject.str("data"),
                                "personId" to personId.ifEmpty { "" },
                                "appealId" to appealIdInternal.ifEmpty { "" },
                                "data" to emptyMap<String, Any?>(),
                            ),
                        ),
                    )
                }
            }
            markSeen()
            emitter.emit(TeletypeEvents.OPENED)
        } else {
            emitter.emit(TeletypeEvents.CLOSED)
        }
    }

    private suspend fun sendPersonData() {
        val ip = runCatching { api.getIp() }.getOrNull()
        val info = device + mapOf("ipAddress" to (ip?.jsonObject?.str("data") ?: ""))
        val extra = buildMap {
            personAdditionalInfo.name?.let { put("name", it) }
            personAdditionalInfo.email?.let { put("email", it) }
            personAdditionalInfo.phone?.let { put("phone", it) }
            personAdditionalInfo.avatar?.let { put("avatar", it) }
            if (personAdditionalInfo.payload.isNotEmpty()) put("payload", personAdditionalInfo.payload)
        }
        socket.emit(
            WsEvent.SET_PERSON_DATA,
            snakeKeys(
                mapOf(
                    "widgetId" to externalId,
                    "personId" to personId,
                    "data" to (info + extra),
                ),
            ),
        )
    }

    private fun setUserData(data: Map<String, Any?>) {
        if (personId.isNotEmpty()) {
            socket.emit(WsEvent.SET_USER_DATA, snakeKeys(mapOf("personId" to personId, "data" to data)))
        } else {
            personData = data
        }
    }

    private fun createRatingForm() {
        handleIncomingMessage(
            Message(tempId = guid(), formType = FormType.Rating, serviceMessage = true),
            unread = false,
        )
    }

    private fun setAppealId(appealId: String) {
        appealIdInternal = appealId
        storage.set(StorageKeys.APPEAL_ID, appealId)
    }

    private fun setOperatorTyping(value: Boolean) {
        if (operatorTyping != value) {
            operatorTyping = value
            publishState()
            emitter.emit(TeletypeEvents.OPERATOR_TYPING, value)
        }
    }

    private fun setMessages(next: List<Message>, fromHistory: Boolean = false) {
        val sent = uniqueBy(
            next.filter { (!it.id.isNullOrEmpty() || !it.isOwn) && !it.serviceMessage },
        ) { it.id ?: it.tempId }
        val pending = uniqueBy(
            next.filter { it.id.isNullOrEmpty() && it.formType != FormType.ContactButtons },
        ) { it.tempId }
        val merged = (sent + pending).sortedBy { it.date?.date ?: "" }
        var unread = 0
        merged.forEach { if (!it.isOwn && it.unread) unread++ }
        messages = merged
        if (unreadCount != unread) {
            unreadCount = unread
            emitter.emit(TeletypeEvents.UNREAD_COUNT_CHANGED, unread)
        }
        publishState()
        publishMessages()
    }

    private fun publishMessages() {
        emitter.emit(TeletypeEvents.MESSAGES_CHANGED, messages)
    }

    private fun pushPending(message: Message) {
        pendingMessages += message
        savePending()
    }

    private fun removePending(tempId: String) {
        val idx = pendingMessages.indexOfFirst { !it.fromInvite && it.tempId == tempId }
        if (idx >= 0) {
            pendingMessages.removeAt(idx)
            savePending()
        }
    }

    private fun savePending() {
        storage.set(
            StorageKeys.PENDING_MESSAGES,
            pendingMessages.filter { it.messageType == app.teletype.sdk.types.MessageType.Text },
        )
    }

    private fun addAttachmentError(file: LocalFile, errorMessage: String) {
        val message = Message(
            message = "",
            messageType = messageTypeFromFile(file),
            progress = 100,
            attachmentId = "${file.name}${file.size ?: 0}",
            tempId = guid(),
            isOwn = true,
            attachment = file.name,
            attachmentPreviewUrl = file.uri,
            date = nowDate(),
            size = file.size,
            errorMessage = errorMessage,
            status = MessageStatus.Failed,
        )
        messages = messages + message
        setMessages(messages)
    }

    private fun patchAttachment(
        attachmentId: String,
        progress: Int? = null,
        attachmentUrl: String? = null,
        errorMessage: String? = null,
        status: MessageStatus? = null,
        attachmentIdNew: String? = null,
    ) {
        messages = messages.map { m ->
            if (m.attachmentId == attachmentId) {
                m.copy(
                    progress = progress ?: m.progress,
                    attachmentUrl = attachmentUrl ?: m.attachmentUrl,
                    errorMessage = errorMessage ?: m.errorMessage,
                    status = status ?: m.status,
                    attachmentId = attachmentIdNew ?: m.attachmentId,
                )
            } else m
        }
        setMessages(messages)
    }
}

private fun nowDate(): DateObject = DateObject(
    date = kotlinx.datetime.Clock.System.now().toString(),
    timezone = "UTC",
)

private fun isUsableConfig(response: JsonElement?): Boolean {
    val obj = response as? JsonObject ?: return false
    if (obj["success"]?.jsonPrimitive?.booleanOrNull == false) return false
    val data = obj["data"] as? JsonObject ?: return false
    return !data.str("projectId").isNullOrEmpty() || !data.str("name").isNullOrEmpty()
}

private fun decodeMessages(element: JsonElement?): List<Message> {
    val arr = when (element) {
        is JsonArray -> element
        is JsonObject -> (element["data"] as? JsonArray) ?: return emptyList()
        else -> return emptyList()
    }
    return arr.mapNotNull { runCatching { decodeMessage(it) }.getOrNull() }
}

private fun decodeMessage(element: JsonElement): Message {
    val camel = camelKeys(element)
    return TeletypeJson.decodeFromJsonElement(Message.serializer(), camel)
}

private fun messageToPayload(message: Message): Map<String, Any?> = buildMap {
    put("tempId", message.tempId)
    message.id?.let { put("id", it) }
    message.appealId?.let { put("appealId", it) }
    message.message?.let { put("message", it) }
    message.attachment?.let { put("attachment", it) }
    message.attachmentUrl?.let { put("attachmentUrl", it) }
    message.attachmentId?.let { put("attachmentId", it) }
    put("isOwn", message.isOwn)
    message.date?.let { put("date", mapOf("date" to it.date, "timezone" to it.timezone)) }
    message.messageType?.let { put("messageType", it.name.lowercase()) }
    message.width?.let { put("width", it) }
    message.height?.let { put("height", it) }
    message.size?.let { put("size", it) }
}

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.strOrEmpty(key: String): String = str(key).orEmpty()
