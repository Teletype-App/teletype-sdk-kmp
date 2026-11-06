package app.teletype.sdk.i18n

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

actual fun getSystemLanguage(): String {
    val languages = NSLocale.preferredLanguages
    val first = languages.firstOrNull() as? String
    return normalizeLanguage(first)
}
