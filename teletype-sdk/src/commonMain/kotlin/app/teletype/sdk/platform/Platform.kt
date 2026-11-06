package app.teletype.sdk.platform

import io.ktor.client.HttpClient

internal expect fun createHttpClient(): HttpClient

expect fun openUrl(url: String)

expect fun currentTimeMillis(): Long

expect suspend fun readUriBytes(uri: String): ByteArray
