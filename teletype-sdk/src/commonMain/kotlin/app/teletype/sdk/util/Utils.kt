package app.teletype.sdk.util

import app.teletype.sdk.i18n.TeletypeStrings
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

fun guid(): String {
    val chars = "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx"
    return buildString(chars.length) {
        chars.forEach { c ->
            when (c) {
                'x' -> append(Random.nextInt(16).toString(16))
                'y' -> append(((Random.nextInt(16) and 0x3) or 0x8).toString(16))
                else -> append(c)
            }
        }
    }
}

fun makeId(length: Int = 10): String {
    val set = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return buildString(length + 1) {
        repeat(length + 1) { append(set[Random.nextInt(set.length)]) }
    }
}

private val EMOJI_RANGE = Regex(
    "[\\uD83C\\uDF00-\\uD83E\\uDDFF\\u2600-\\u27BF\\uD83C\\uDDE6-\\uD83C\\uDDFF]",
)

fun isEmojiOnly(text: String?): Boolean {
    if (text.isNullOrEmpty()) return false
    val stripped = text
        .replace("\uFE0F", "")
        .replace("\u200D", "")
        .replace(EMOJI_RANGE, "")
        .trim()
    return stripped.isEmpty() && text.trim().isNotEmpty()
}

fun formatBytes(bytes: Long?, decimals: Int = 2, labels: TeletypeStrings? = null): String {
    val sizes = if (labels != null) {
        listOf(labels.Bt, labels.Kb, labels.Mb, labels.Gb, labels.Tb, labels.Pb, labels.Eb, labels.Zb, labels.Yb)
    } else {
        listOf("BT", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
    }
    val value = bytes ?: 0L
    if (value == 0L) return "0 ${sizes[0]}"
    val k = 1024.0
    val dm = if (decimals < 0) 0 else decimals
    val i = floor(ln(value.toDouble()) / ln(k)).toInt().coerceIn(0, sizes.lastIndex)
    val n = value / k.pow(i)
    val formatted = if (dm == 0) n.toLong().toString() else ((n * 10.0.pow(dm)).toLong() / 10.0.pow(dm)).toString()
    return "$formatted ${sizes[i]}"
}

fun getExtension(nameOrUrl: String?): String {
    if (nameOrUrl.isNullOrEmpty()) return ""
    val clean = nameOrUrl.split("?", "#").first()
    val dot = clean.lastIndexOf('.')
    if (dot < 0 || dot == clean.lastIndex) return ""
    return clean.substring(dot + 1).lowercase()
}

fun extractUrls(text: String): List<String> {
    val re = Regex("https?://[^\\s]+")
    return re.findAll(text).map { it.value }.toList()
}

fun <T> uniqueBy(arr: List<T>, keyFn: (T) -> String): List<T> {
    val seen = mutableSetOf<String>()
    val out = mutableListOf<T>()
    arr.forEach { item ->
        val key = keyFn(item)
        if (seen.add(key)) out += item
    }
    return out
}
