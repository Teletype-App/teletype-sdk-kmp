package app.teletype.sdk.storage

import app.teletype.sdk.json.TeletypeJson
import app.teletype.sdk.json.anyToJson
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

interface KeyValueStore {
    suspend fun getItem(key: String): String?
    suspend fun setItem(key: String, value: String)
    suspend fun removeItem(key: String)
}

class MemoryStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override suspend fun getItem(key: String): String? = map[key]
    override suspend fun setItem(key: String, value: String) {
        map[key] = value
    }
    override suspend fun removeItem(key: String) {
        map.remove(key)
    }
}

class SettingsStore private constructor(private val settings: Settings) : KeyValueStore {
    constructor() : this(Settings())
    override suspend fun getItem(key: String): String? =
        if (settings.hasKey(key)) settings.getString(key, "") else null

    override suspend fun setItem(key: String, value: String) {
        settings.putString(key, value)
    }

    override suspend fun removeItem(key: String) {
        settings.remove(key)
    }

    fun allKeys(): Set<String> = settings.keys

    fun removeMatching(predicate: (String) -> Boolean) {
        settings.keys.filter(predicate).forEach { settings.remove(it) }
    }
}

/**
 * Persistence abstraction. Mirrors the web widget's namespaced localStorage
 * (`teletype-{apiId}-{key}`).
 */
class TeletypeStorage(
    private val apiId: String,
    private val backend: KeyValueStore = SettingsStore(),
) {
    private val cache = mutableMapOf<String, Any?>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun fullKey(key: String) = "teletype-$apiId-$key"

    suspend fun hydrate(keys: List<String>) {
        keys.forEach { key ->
            cache[key] = parse(backend.getItem(fullKey(key)))
        }
    }

    private fun parse(raw: String?): Any? {
        if (raw == null) return null
        return try {
            TeletypeJson.parseToJsonElement(raw)
        } catch (_: Throwable) {
            raw
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String, defaultValue: T? = null): T? {
        val value = cache[key] ?: return defaultValue
        return (value as? T) ?: defaultValue
    }

    fun getJson(key: String): JsonElement? = cache[key] as? JsonElement

    fun getString(key: String, defaultValue: String = ""): String {
        val value = cache[key] ?: return defaultValue
        return when (value) {
            is JsonPrimitive -> value.content
            is String -> value
            else -> defaultValue
        }
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        val value = cache[key] ?: return defaultValue
        return when (value) {
            is JsonPrimitive -> value.content.toLongOrNull() ?: defaultValue
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: defaultValue
            else -> defaultValue
        }
    }

    fun set(key: String, value: Any?) {
        cache[key] = value
        val encoded = when (value) {
            null -> "null"
            is JsonElement -> value.toString()
            is String -> JsonPrimitive(value).toString()
            is Number, is Boolean -> JsonPrimitive(value.toString()).toString().let {
                if (value is Number || value is Boolean) value.toString() else it
            }
            else -> TeletypeJson.encodeToString(JsonElement.serializer(), anyToJson(value))
        }
        val stored = when (value) {
            is Number, is Boolean -> value.toString()
            is String -> JsonPrimitive(value).toString()
            is JsonElement -> value.toString()
            null -> "null"
            else -> TeletypeJson.encodeToString(JsonElement.serializer(), anyToJson(value))
        }
        scope.launch { backend.setItem(fullKey(key), stored) }
    }

    fun remove(key: String) {
        cache.remove(key)
        scope.launch { backend.removeItem(fullKey(key)) }
    }
}
