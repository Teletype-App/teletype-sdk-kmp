package app.teletype.sdk.events

/**
 * Minimal event emitter. Framework-agnostic so the headless core can be used
 * without Compose.
 */
class EventEmitter {
    private val listeners = mutableMapOf<String, MutableSet<(Any?) -> Unit>>()

    fun on(event: String, listener: (Any?) -> Unit): () -> Unit {
        val set = listeners.getOrPut(event) { mutableSetOf() }
        set.add(listener)
        return { off(event, listener) }
    }

    fun once(event: String, listener: (Any?) -> Unit): () -> Unit {
        lateinit var wrapped: (Any?) -> Unit
        wrapped = { payload ->
            off(event, wrapped)
            listener(payload)
        }
        return on(event, wrapped)
    }

    fun off(event: String, listener: (Any?) -> Unit) {
        listeners[event]?.remove(listener)
    }

    fun emit(event: String, payload: Any? = null) {
        listeners[event]?.toList()?.forEach { listener ->
            try {
                listener(payload)
            } catch (err: Throwable) {
                println("[teletype] event listener threw $err")
            }
        }
    }

    fun removeAllListeners() {
        listeners.clear()
    }
}

object TeletypeEvents {
    const val READY = "ready"
    const val OPENED = "opened"
    const val CLOSED = "closed"
    const val DIALOG_STARTED = "dialogStarted"
    const val APPEAL_STARTED = "appealStarted"
    const val MESSAGE_SENT = "messageSent"
    const val MESSAGE_RECEIVED = "messageReceived"
    const val DIALOG_CLOSED = "dialogClosed"
    const val DIALOG_RATED = "dialogRated"
    const val CHANNEL_CLICKED = "channelClicked"
    const val MESSAGES_CHANGED = "messagesChanged"
    const val UNREAD_COUNT_CHANGED = "unreadCountChanged"
    const val OPERATOR_TYPING = "operatorTyping"
    const val CONNECTION_CHANGED = "connectionChanged"
    const val DRAFT_CHANGED = "draftChanged"
    const val OPEN_CHANGED = "openChanged"
    const val CONFIG = "config"
    const val LOCALE_CHANGED = "localeChanged"
    const val APPEARANCE_CHANGED = "appearanceChanged"
    const val ERROR = "error"
}
