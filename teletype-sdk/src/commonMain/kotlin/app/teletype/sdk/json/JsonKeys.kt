package app.teletype.sdk.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

val TeletypeJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
}

private fun toSnake(str: String): String =
    str
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
        .replace(Regex("[\\s-]+"), "_")
        .lowercase()

private fun toCamel(str: String): String =
    str.replace(Regex("[_-]([a-z0-9])")) { it.groupValues[1].uppercase() }

fun snakeKeys(element: JsonElement?): JsonElement {
    // Web widget `api.snakeKeys(null)` returns `''`.
    if (element == null || element is JsonNull) return JsonPrimitive("")
    return when (element) {
        is JsonArray -> JsonArray(element.map { snakeKeys(it) })
        is JsonObject -> buildJsonObject {
            element.forEach { (key, value) ->
                put(toSnake(key), if (value is JsonObject || value is JsonArray) snakeKeys(value) else value)
            }
        }
        else -> element
    }
}

fun snakeKeys(map: Map<String, Any?>): JsonObject = buildJsonObject {
    map.forEach { (key, value) ->
        put(toSnake(key), anyToJson(value).let { el ->
            if (el is JsonObject || el is JsonArray) snakeKeys(el) else el
        })
    }
}

fun camelKeys(element: JsonElement?): JsonElement {
    if (element == null || element is JsonNull) return JsonNull
    return when (element) {
        is JsonArray -> JsonArray(element.map { camelKeys(it) })
        is JsonObject -> buildJsonObject {
            element.forEach { (key, value) ->
                put(toCamel(key), if (value is JsonObject || value is JsonArray) camelKeys(value) else value)
            }
        }
        else -> element
    }
}

/** Backend sometimes sends `""` instead of `[]` for list fields. */
fun coerceWidgetConfigLists(element: JsonElement): JsonElement {
    val obj = element as? JsonObject ?: return element
    val listKeys = setOf("workRange", "holidays", "channels", "operators", "rules")
    return buildJsonObject {
        obj.forEach { (key, value) ->
            put(key, if (key in listKeys && value !is JsonArray) JsonArray(emptyList()) else value)
        }
    }
}

fun anyToJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Map<*, *> -> buildJsonObject {
        value.forEach { (k, v) ->
            if (k != null) put(k.toString(), anyToJson(v))
        }
    }
    is Iterable<*> -> JsonArray(value.map { anyToJson(it) })
    else -> JsonPrimitive(value.toString())
}

fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject

fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.content

fun JsonObject.boolean(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
        ?: (this[key] as? JsonPrimitive)?.takeIf { it.isString.not() }?.content?.toBooleanStrictOrNull()

fun serializeForm(obj: Map<String, Any?>, prefix: String = ""): String {
    val parts = mutableListOf<String>()
    obj.forEach { (p, v) ->
        val k = if (prefix.isEmpty()) p else "$prefix[$p]"
        if (v is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            parts += serializeForm(v as Map<String, Any?>, k)
        } else {
            parts += "${encode(k)}=${encode(v?.toString() ?: "")}"
        }
    }
    return parts.joinToString("&")
}

private fun encode(s: String): String = buildString {
    s.encodeToByteArray().forEach { b ->
        val c = b.toInt() and 0xFF
        when {
            c in 0x41..0x5A || c in 0x61..0x7A || c in 0x30..0x39 || c == 0x2D || c == 0x5F || c == 0x2E || c == 0x7E ->
                append(c.toChar())
            else -> append("%").append(c.toString(16).uppercase().padStart(2, '0'))
        }
    }
}

fun JsonObject.toMutableMap(): MutableMap<String, JsonElement> = toMutableMap()
