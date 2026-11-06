package app.teletype.sdk.api

import app.teletype.sdk.config.TeletypeEnvironment
import app.teletype.sdk.json.TeletypeJson
import app.teletype.sdk.json.camelKeys
import app.teletype.sdk.json.serializeForm
import app.teletype.sdk.json.snakeKeys
import app.teletype.sdk.platform.createHttpClient
import app.teletype.sdk.platform.readUriBytes
import app.teletype.sdk.types.LocalFile
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class ApiClient(
    private val env: TeletypeEnvironment,
    private val externalId: String,
    private val http: HttpClient = createHttpClient(),
) {
    private fun base(path: String) = "${env.apiUrl}$path"
    private fun v(path: String) = base("api/v${env.apiVersion}/$path")

    suspend fun getJson(url: String): JsonElement {
        val raw = http.get(url).bodyAsText()
        val parsed = TeletypeJson.parseToJsonElement(raw)
        return camelKeys(parsed)
    }

    private suspend fun postForm(url: String, payload: Map<String, Any?>): JsonElement {
        val raw = http.post(url) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(serializeForm(payload))
        }.bodyAsText()
        return camelKeys(TeletypeJson.parseToJsonElement(raw))
    }

    suspend fun getIp(): JsonElement = getJson(v("widget/ip"))

    suspend fun getConfig(): JsonElement = getJson(v("widget/$externalId/config"))

    suspend fun getHistory(appealId: String): JsonElement {
        val dt = "2018-02-12T17:08:55%2B0000"
        return getJson(v("history/$externalId/$appealId/messages?datetime=$dt"))
    }

    suspend fun uploadFile(
        file: LocalFile,
        onProgress: ((Int) -> Unit)? = null,
    ): JsonElement {
        val url = v("widget-attachment/$externalId")
        val bytes = file.bytes ?: readUriBytes(file.uri)
        onProgress?.invoke(0)
        val body = MultiPartFormDataContent(
            formData {
                append(
                    "file",
                    bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, file.type.ifBlank { "application/octet-stream" })
                        append(
                            HttpHeaders.ContentDisposition,
                            "filename=\"${file.name.replace("\"", "")}\"",
                        )
                    },
                )
                append("externalId", externalId)
            },
        )
        val raw = http.post(url) {
            setBody(body)
        }.bodyAsText()
        onProgress?.invoke(100)
        return camelKeys(TeletypeJson.parseToJsonElement(raw.ifBlank { "{}" }))
    }

    suspend fun collect(data: Map<String, Any?>): JsonElement =
        postForm(base("api/v${env.apiVersion}/collect"), (snakeKeys(data) as JsonObject).toMapValues())

    suspend fun reportError(data: Map<String, Any?>): JsonElement =
        postForm(base("api/v${env.apiVersion}/widget/error"), (snakeKeys(data) as JsonObject).toMapValues())

    suspend fun notifyLocationChange(location: String): JsonElement =
        postForm(
            v("widget/$externalId/url"),
            mapOf("client_url" to location),
        )
}

private fun JsonObject.toMapValues(): Map<String, Any?> =
    entries.associate { it.key to it.value }
