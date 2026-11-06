package app.teletype.sdk.i18n

import java.util.Locale

actual fun getSystemLanguage(): String =
    normalizeLanguage(Locale.getDefault().language)
