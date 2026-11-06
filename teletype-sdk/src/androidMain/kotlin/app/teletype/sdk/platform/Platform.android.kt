package app.teletype.sdk.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import android.content.Intent
import android.net.Uri
import app.teletype.sdk.json.TeletypeJson

internal actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(WebSockets)
    install(ContentNegotiation) { json(TeletypeJson) }
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 20_000
        socketTimeoutMillis = 60_000
    }
}

actual fun openUrl(url: String) {
    val context = applicationContextOrNull() ?: return
    val uri = Uri.parse(url)
    val mime = mimeFromUrl(url)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        if (mime != null) setDataAndType(uri, mime) else data = uri
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
    }
}

private fun mimeFromUrl(url: String): String? {
    val path = url.substringBefore('?').lowercase()
    return when {
        path.endsWith(".png") -> "image/png"
        path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
        path.endsWith(".gif") -> "image/gif"
        path.endsWith(".webp") -> "image/webp"
        path.endsWith(".mp4") -> "video/mp4"
        path.endsWith(".mov") -> "video/quicktime"
        path.endsWith(".webm") -> "video/webm"
        path.endsWith(".pdf") -> "application/pdf"
        path.endsWith(".txt") -> "text/plain"
        else -> null
    }
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual suspend fun readUriBytes(uri: String): ByteArray {
    val context = applicationContextOrNull()
        ?: error("No Android context to read $uri")
    val parsed = android.net.Uri.parse(uri)
    context.contentResolver.openInputStream(parsed)?.use { return it.readBytes() }
    val path = uri.removePrefix("file://")
    return java.io.File(path).readBytes()
}

private fun applicationContextOrNull(): android.content.Context? = try {
    val activityThread = Class.forName("android.app.ActivityThread")
    val app = activityThread.getMethod("currentApplication").invoke(null)
    app as? android.content.Context
} catch (_: Throwable) {
    null
}
