package app.teletype.sdk.platform

import app.teletype.sdk.json.TeletypeJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.memcpy

internal actual fun createHttpClient(): HttpClient = HttpClient(Darwin) {
    install(WebSockets)
    install(ContentNegotiation) { json(TeletypeJson) }
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 20_000
        socketTimeoutMillis = 60_000
    }
}

actual fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl)
}

actual fun currentTimeMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()

@OptIn(ExperimentalForeignApi::class)
actual suspend fun readUriBytes(uri: String): ByteArray {
    val nsUrl = NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri)
    val data = platform.Foundation.NSData.dataWithContentsOfURL(nsUrl)
        ?: error("Cannot read $uri")
    val size = data.length.toInt()
    val out = ByteArray(size)
    if (size > 0) {
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
    }
    return out
}
