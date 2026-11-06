package app.teletype.sdk.socket

import app.teletype.sdk.json.TeletypeJson
import app.teletype.sdk.platform.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlin.concurrent.Volatile
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * socket.io v2 / engine.io v3 client used by the Teletype widget backend.
 * Starts with HTTP polling (required on some Android emulators) and upgrades
 * to websocket when possible.
 */
class SocketClient(
    private val url: String,
    private val debug: Boolean = false,
    private val http: HttpClient = createHttpClient(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val listeners = mutableMapOf<String, MutableList<(JsonElement?) -> Unit>>()

    @Volatile var connected: Boolean = false
        private set

    private var sid: String? = null
    private var pingInterval = 25_000L
    private var pingTimeout = 5_000L
    private var ws: WebSocketSession? = null
    private var loopJob: Job? = null
    private var pingJob: Job? = null
    private var usingWebsocket = false
    private var started = false

    fun on(event: String, cb: (JsonElement?) -> Unit) {
        listeners.getOrPut(event) { mutableListOf() }.add(cb)
    }

    fun off(event: String? = null) {
        if (event == null) listeners.clear() else listeners.remove(event)
    }

    fun ensureCreated() {
        // no-op: the HTTP client is created in the constructor, matching the
        // RN SDK's "create socket before connect so listeners can bind first".
    }

    fun connect() {
        if (started && connected) return
        started = true
        loopJob?.cancel()
        loopJob = scope.launch {
            try {
                handshakePolling()
                tryUpgrade()
                if (usingWebsocket) {
                    readWebsocket()
                } else {
                    pollLoop()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (debug) println("[teletype][ws] handshake failed $e")
                setDisconnected()
            }
        }
    }

    fun disconnect() {
        started = false
        loopJob?.cancel()
        pingJob?.cancel()
        scope.launch { runCatching { ws?.close() } }
        ws = null
        setDisconnected()
    }

    fun emit(event: String, payload: JsonElement? = null) {
        if (debug) println("[teletype][ws] ⬆ $event $payload")
        val packet = encodeEvent(event, payload)
        scope.launch {
            try {
                sendEngineMessage(packet)
            } catch (e: Throwable) {
                if (debug) println("[teletype][ws] emit failed $e")
            }
        }
    }

    private fun setConnected() {
        if (connected) return
        connected = true
        dispatch("connect", null)
    }

    private fun setDisconnected() {
        val was = connected
        connected = false
        usingWebsocket = false
        if (was) dispatch("disconnect", null)
    }

    private fun dispatch(event: String, payload: JsonElement?) {
        if (debug && event != "connect" && event != "disconnect") {
            println("[teletype][ws] ⬇ $event ${payload ?: "(no payload)"}")
        } else if (debug) {
            println("[teletype][ws] • $event")
        }
        listeners[event]?.toList()?.forEach { listener ->
            try {
                listener(payload)
            } catch (e: Throwable) {
                println("[teletype] event listener threw $e")
            }
        }
    }

    private fun encodeEvent(event: String, payload: JsonElement?): String {
        val arr = buildJsonArray {
            add(JsonPrimitive(event))
            if (payload != null && payload !is JsonNull) add(payload)
        }
        // engine.io message (4) + socket.io EVENT (2)
        return "42$arr"
    }

    private fun handleEnginePacket(packet: String) {
        if (packet.isEmpty()) return
        when (packet[0]) {
            '0' -> { /* open already handled */ }
            '1' -> setDisconnected()
            '2' -> scope.launch { sendRaw("3") } // ping → pong
            '3' -> { /* pong */ }
            '4' -> handleSocketIo(packet.substring(1))
            '6' -> { /* noop */ }
        }
    }

    private fun handleSocketIo(packet: String) {
        if (packet.isEmpty()) return
        when (packet[0]) {
            '0' -> setConnected() // namespace connected
            '1' -> setDisconnected()
            '2' -> {
                val json = packet.substring(1)
                val parsed = runCatching { TeletypeJson.parseToJsonElement(json).jsonArray }.getOrNull()
                    ?: return
                val name = parsed.getOrNull(0)?.jsonPrimitive?.content ?: return
                val data = parsed.getOrNull(1)
                dispatch(name, data)
            }
            '4' -> {
                if (debug) println("[teletype][ws] error $packet")
            }
        }
    }

    private suspend fun handshakePolling() {
        val base = httpBase()
        val text = http.get("$base/socket.io/") {
            parameter("EIO", 3)
            parameter("transport", "polling")
        }.bodyAsText()
        val packets = decodePayload(text)
        val open = packets.firstOrNull { it.startsWith("0") } ?: error("engine.io open missing")
        val openJson = TeletypeJson.parseToJsonElement(open.substring(1))
        val obj = openJson as kotlinx.serialization.json.JsonObject
        sid = obj["sid"]?.jsonPrimitive?.content ?: error("engine.io sid missing")
        pingInterval = obj["pingInterval"]?.jsonPrimitive?.content?.toLongOrNull() ?: 25_000L
        pingTimeout = obj["pingTimeout"]?.jsonPrimitive?.content?.toLongOrNull() ?: 5_000L
        packets.drop(1).forEach { handleEnginePacket(it) }
        // Connect default namespace
        sendEngineMessage("40")
        startPing()
    }

    private suspend fun tryUpgrade() {
        val currentSid = sid ?: return
        val wsUrl = wsBase() + "/socket.io/?EIO=3&transport=websocket&sid=$currentSid"
        try {
            val session = http.webSocketSession(wsUrl)
            session.send(Frame.Text("2probe"))
            val probe = (session.incoming.receive() as? Frame.Text)?.readText()
            if (probe != "3probe") {
                session.close()
                return
            }
            session.send(Frame.Text("5")) // upgrade
            ws = session
            usingWebsocket = true
            if (debug) println("[teletype][ws] upgraded to websocket")
        } catch (e: Throwable) {
            if (debug) println("[teletype][ws] websocket upgrade skipped: $e")
            usingWebsocket = false
            ws = null
        }
    }

    private suspend fun readWebsocket() {
        val session = ws ?: return
        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    handleEnginePacket(frame.readText())
                }
            }
        } finally {
            setDisconnected()
        }
    }

    private suspend fun pollLoop() {
        val currentSid = sid ?: return
        val base = httpBase()
        while (scope.isActive && started && !usingWebsocket) {
            try {
                val text = http.get("$base/socket.io/") {
                    parameter("EIO", 3)
                    parameter("transport", "polling")
                    parameter("sid", currentSid)
                }.bodyAsText()
                decodePayload(text).forEach { handleEnginePacket(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (debug) println("[teletype][ws] poll error $e")
                delay(1_000)
            }
        }
    }

    private fun startPing() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive && started) {
                delay(pingInterval)
                runCatching { sendRaw("2") }
            }
        }
    }

    private suspend fun sendEngineMessage(socketIoPacket: String) {
        sendRaw(socketIoPacket)
    }

    private suspend fun sendRaw(packet: String) {
        val session = ws
        if (usingWebsocket && session != null) {
            session.send(Frame.Text(packet))
            return
        }
        val currentSid = sid ?: return
        val body = encodePayload(listOf(packet))
        http.post("${httpBase()}/socket.io/") {
            parameter("EIO", 3)
            parameter("transport", "polling")
            parameter("sid", currentSid)
            contentType(ContentType.Text.Plain)
            setBody(body)
        }
    }

    private fun httpBase(): String = url.trimEnd('/')

    private fun wsBase(): String {
        val trimmed = url.trimEnd('/')
        return when {
            trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
            trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
            trimmed.startsWith("wss://") || trimmed.startsWith("ws://") -> trimmed
            else -> "wss://$trimmed"
        }
    }
}

/**
 * engine.io v3 xhr payload: concatenated `<length>:<packet>` strings.
 */
internal fun decodePayload(raw: String): List<String> {
    if (raw.isEmpty()) return emptyList()
    // Websocket-style (no length prefix) — a single packet.
    if (raw.first().isDigit() && (raw.length == 1 || raw.getOrNull(1)?.isDigit() != true || raw.startsWith("0{"))) {
        if (raw.startsWith("0{") || raw.length < 3 || raw[1] != ':') {
            // Could still be length-prefixed (`96:0{...}`). Fall through if we see `:`.
            val colon = raw.indexOf(':')
            if (colon <= 0 || raw.substring(0, colon).toIntOrNull() == null) {
                return listOf(raw)
            }
        }
    }
    val out = mutableListOf<String>()
    var i = 0
    while (i < raw.length) {
        val colon = raw.indexOf(':', i)
        if (colon < 0) break
        val len = raw.substring(i, colon).toIntOrNull() ?: break
        val start = colon + 1
        val end = (start + len).coerceAtMost(raw.length)
        out += raw.substring(start, end)
        i = end
    }
    return out.ifEmpty { listOf(raw) }
}

internal fun encodePayload(packets: List<String>): String =
    packets.joinToString("") { "${it.length}:${it}" }
