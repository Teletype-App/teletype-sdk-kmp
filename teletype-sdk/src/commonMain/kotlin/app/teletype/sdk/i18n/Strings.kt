package app.teletype.sdk.i18n

data class TeletypeStrings(
    val headerTitleFallback: String,
    val headerReplyTime: String,
    val statusConnecting: String,
    val statusOnline: String,
    val statusOffline: String,
    val composerPlaceholder: String,
    val ratingTitle: String,
    val attachmentFallback: String,
    val messageSending: String,
    val messageDelivered: String,
    val messageSeen: String,
    val messageFailed: String,
    val timeJustNow: String,
    val timeAgo: String,
    val timeYesterday: String,
    val mediaSave: String,
    val mediaOpen: String,
    val mediaDownloading: String,
    val mediaSaved: String,
    val mediaDownloadError: String,
    val mediaCannotOpen: String,
    val mediaVideo: String,
    val mediaAudio: String,
    val mediaClose: String,
    val Bt: String,
    val Kb: String,
    val Mb: String,
    val Gb: String,
    val Tb: String,
    val Pb: String,
    val Eb: String,
    val Zb: String,
    val Yb: String,
    val send: String,
    val cancel: String,
    val sendImage: String,
    val sendVideo: String,
    val sendAudio: String,
    val sendFile: String,
    val uploading: String,
    val fileSizeError: String,
    val fileFormatNotAllowed: String,
    val uploadFailed: String,
)

typealias TeletypeTranslations = Map<String, Map<String, String>>

val EN_STRINGS = TeletypeStrings(
    headerTitleFallback = "Chat",
    headerReplyTime = "Typically replies within an hour",
    statusConnecting = "Connecting…",
    statusOnline = "Online",
    statusOffline = "We will reply soon",
    composerPlaceholder = "Write here…",
    ratingTitle = "Please rate this conversation",
    attachmentFallback = "Attachment",
    messageSending = "sending…",
    messageDelivered = "Delivered",
    messageSeen = "Seen",
    messageFailed = "failed · tap to retry",
    timeJustNow = "Just now",
    timeAgo = "ago",
    timeYesterday = "Yesterday",
    mediaSave = "Save to device",
    mediaOpen = "Open",
    mediaDownloading = "Downloading…",
    mediaSaved = "Saved to device",
    mediaDownloadError = "Couldn't download the file",
    mediaCannotOpen = "No app can open this file",
    mediaVideo = "Video",
    mediaAudio = "Audio",
    mediaClose = "Close",
    Bt = "BT", Kb = "KB", Mb = "MB", Gb = "GB", Tb = "TB",
    Pb = "PB", Eb = "EB", Zb = "ZB", Yb = "YB",
    send = "Send",
    cancel = "Cancel",
    sendImage = "Send image",
    sendVideo = "Send video",
    sendAudio = "Send audio",
    sendFile = "Send file",
    uploading = "Loading",
    fileSizeError = "File is too large (max 20 MB)",
    fileFormatNotAllowed = "This file type is not allowed",
    uploadFailed = "Couldn't upload the file",
)

val RU_STRINGS = TeletypeStrings(
    headerTitleFallback = "Чат",
    headerReplyTime = "Обычно отвечают в течение часа",
    statusConnecting = "Подключение…",
    statusOnline = "На связи",
    statusOffline = "Ответим в ближайшее время",
    composerPlaceholder = "Напишите нам…",
    ratingTitle = "Пожалуйста, оцените диалог",
    attachmentFallback = "Вложение",
    messageSending = "отправка…",
    messageDelivered = "Доставлено",
    messageSeen = "Просмотрено",
    messageFailed = "ошибка · нажмите, чтобы повторить",
    timeJustNow = "Только что",
    timeAgo = "назад",
    timeYesterday = "Вчера",
    mediaSave = "Сохранить на устройство",
    mediaOpen = "Открыть",
    mediaDownloading = "Загрузка…",
    mediaSaved = "Сохранено на устройство",
    mediaDownloadError = "Не удалось скачать файл",
    mediaCannotOpen = "Нет приложения для открытия файла",
    mediaVideo = "Видео",
    mediaAudio = "Аудио",
    mediaClose = "Закрыть",
    Bt = "БТ", Kb = "КБ", Mb = "МБ", Gb = "ГБ", Tb = "ТБ",
    Pb = "ПБ", Eb = "ЭБ", Zb = "ЗБ", Yb = "ЙБ",
    send = "Отправить",
    cancel = "Отмена",
    sendImage = "Отправить изображение",
    sendVideo = "Отправить видео",
    sendAudio = "Отправить аудио",
    sendFile = "Отправить файл",
    uploading = "Загрузка",
    fileSizeError = "Файл слишком большой (макс. 20 МБ)",
    fileFormatNotAllowed = "Этот тип файла не поддерживается",
    uploadFailed = "Не удалось загрузить файл",
)

val BUILTIN_LOCALES = mapOf("en" to EN_STRINGS, "ru" to RU_STRINGS)
const val DEFAULT_LOCALE = "en"

expect fun getSystemLanguage(): String

fun normalizeLanguage(locale: String?): String {
    if (locale.isNullOrBlank()) return DEFAULT_LOCALE
    val lang = locale.split("-", "_").firstOrNull()?.lowercase()
    return lang?.takeIf { it.isNotEmpty() } ?: DEFAULT_LOCALE
}

fun resolveStrings(
    locale: String? = null,
    translations: TeletypeTranslations? = null,
): Pair<String, TeletypeStrings> {
    val language = normalizeLanguage(locale ?: getSystemLanguage())
    val builtin = BUILTIN_LOCALES[language]
    val custom = translations?.get(language)
    val strings = mergeStrings(EN_STRINGS, builtin, custom)
    return language to strings
}

private fun mergeStrings(
    en: TeletypeStrings,
    builtin: TeletypeStrings?,
    custom: Map<String, String>?,
): TeletypeStrings {
    fun pick(key: String, fallback: String): String =
        custom?.get(key) ?: fallback

    val base = builtin ?: en
    if (custom.isNullOrEmpty()) return base
    return TeletypeStrings(
        headerTitleFallback = pick("headerTitleFallback", base.headerTitleFallback),
        headerReplyTime = pick("headerReplyTime", base.headerReplyTime),
        statusConnecting = pick("statusConnecting", base.statusConnecting),
        statusOnline = pick("statusOnline", base.statusOnline),
        statusOffline = pick("statusOffline", base.statusOffline),
        composerPlaceholder = pick("composerPlaceholder", base.composerPlaceholder),
        ratingTitle = pick("ratingTitle", base.ratingTitle),
        attachmentFallback = pick("attachmentFallback", base.attachmentFallback),
        messageSending = pick("messageSending", base.messageSending),
        messageDelivered = pick("messageDelivered", base.messageDelivered),
        messageSeen = pick("messageSeen", base.messageSeen),
        messageFailed = pick("messageFailed", base.messageFailed),
        timeJustNow = pick("timeJustNow", base.timeJustNow),
        timeAgo = pick("timeAgo", base.timeAgo),
        timeYesterday = pick("timeYesterday", base.timeYesterday),
        mediaSave = pick("mediaSave", base.mediaSave),
        mediaOpen = pick("mediaOpen", base.mediaOpen),
        mediaDownloading = pick("mediaDownloading", base.mediaDownloading),
        mediaSaved = pick("mediaSaved", base.mediaSaved),
        mediaDownloadError = pick("mediaDownloadError", base.mediaDownloadError),
        mediaCannotOpen = pick("mediaCannotOpen", base.mediaCannotOpen),
        mediaVideo = pick("mediaVideo", base.mediaVideo),
        mediaAudio = pick("mediaAudio", base.mediaAudio),
        mediaClose = pick("mediaClose", base.mediaClose),
        Bt = pick("Bt", base.Bt), Kb = pick("Kb", base.Kb), Mb = pick("Mb", base.Mb),
        Gb = pick("Gb", base.Gb), Tb = pick("Tb", base.Tb), Pb = pick("Pb", base.Pb),
        Eb = pick("Eb", base.Eb), Zb = pick("Zb", base.Zb), Yb = pick("Yb", base.Yb),
        send = pick("send", base.send),
        cancel = pick("cancel", base.cancel),
        sendImage = pick("sendImage", base.sendImage),
        sendVideo = pick("sendVideo", base.sendVideo),
        sendAudio = pick("sendAudio", base.sendAudio),
        sendFile = pick("sendFile", base.sendFile),
        uploading = pick("uploading", base.uploading),
        fileSizeError = pick("fileSizeError", base.fileSizeError),
        fileFormatNotAllowed = pick("fileFormatNotAllowed", base.fileFormatNotAllowed),
        uploadFailed = pick("uploadFailed", base.uploadFailed),
    )
}
