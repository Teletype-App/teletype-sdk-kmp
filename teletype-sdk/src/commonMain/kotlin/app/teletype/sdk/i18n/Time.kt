package app.teletype.sdk.i18n

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val SEC_MIN = 60
private const val SEC_HOUR = 60 * 60
private const val SEC_DAY = SEC_HOUR * 24

private val UNITS = mapOf(
    "en" to mapOf(
        "minute" to listOf("minute", "minutes"),
        "hour" to listOf("hour", "hours"),
    ),
    "ru" to mapOf(
        "minute" to listOf("минуту", "минуты", "минут"),
        "hour" to listOf("час", "часа", "часов"),
    ),
)

private val MONTHS = mapOf(
    "en" to listOf(
        "January", "February", "March", "April", "May", "June", "July",
        "August", "September", "October", "November", "December",
    ),
    "ru" to listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня", "июля",
        "августа", "сентября", "октября", "ноября", "декабря",
    ),
)

private fun morph(n: Int, forms: List<String>, language: String): String {
    if (language == "ru") {
        val mod100 = kotlin.math.abs(n) % 100
        val mod10 = mod100 % 10
        return when {
            mod100 in 11..14 -> forms.getOrElse(2) { forms.last() }
            mod10 == 1 -> forms[0]
            mod10 in 2..4 -> forms.getOrElse(1) { forms.last() }
            else -> forms.getOrElse(2) { forms.last() }
        }
    }
    return if (n == 1) forms[0] else forms.getOrElse(1) { forms[0] }
}

private fun pad(n: Int): String = if (n < 10) "0$n" else n.toString()

private fun parseInstant(iso: String): Instant? {
    val normalized = iso.trim()
        .replace(" ", "T")
        .replace(Regex("([+-]\\d{2})(\\d{2})$")) { "${it.groupValues[1]}:${it.groupValues[2]}" }
    return runCatching { Instant.parse(normalized) }.getOrNull()
}

fun formatRelativeTime(iso: String?, language: String, strings: TeletypeStrings): String {
    if (iso.isNullOrBlank()) return ""
    val instant = parseInstant(iso) ?: return ""
    val now = Clock.System.now()
    val diffSec = ((now - instant).inWholeSeconds).coerceAtLeast(0).toInt()
    val units = UNITS[language] ?: UNITS.getValue("en")
    val tz = TimeZone.currentSystemDefault()
    val local = instant.toLocalDateTime(tz)

    if (diffSec < SEC_MIN) return strings.timeJustNow
    if (diffSec < SEC_HOUR) {
        val n = diffSec / SEC_MIN
        return "$n ${morph(n, units.getValue("minute"), language)} ${strings.timeAgo}"
    }
    if (diffSec < SEC_HOUR * 3) {
        val n = diffSec / SEC_HOUR
        return "$n ${morph(n, units.getValue("hour"), language)} ${strings.timeAgo}"
    }
    val hhmm = "${pad(local.hour)}:${pad(local.minute)}"
    if (diffSec < SEC_DAY) return hhmm
    if (diffSec < SEC_DAY * 2) return "${strings.timeYesterday} $hhmm"

    val months = MONTHS[language] ?: MONTHS.getValue("en")
    val month = months[local.month.ordinal]
    val nowLocal = now.toLocalDateTime(tz)
    return if (local.year == nowLocal.year) {
        "${local.dayOfMonth} $month"
    } else {
        "${local.dayOfMonth} $month, ${local.year}"
    }
}
