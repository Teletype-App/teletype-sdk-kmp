package app.teletype.sdk.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
enum class MessageType {
    @SerialName("text") Text,
    @SerialName("typing") Typing,
    @SerialName("attachment") Attachment,
    @SerialName("image") Image,
    @SerialName("gif") Gif,
    @SerialName("audio") Audio,
    @SerialName("video") Video,
}

@Serializable
enum class MessageStatus {
    @SerialName("sending") Sending,
    @SerialName("sent") Sent,
    @SerialName("failed") Failed,
}

@Serializable
enum class FormType {
    @SerialName("rating") Rating,
    @SerialName("boolean") Boolean,
    @SerialName("faq") Faq,
    @SerialName("input") Input,
    @SerialName("contact") Contact,
    @SerialName("contactButtons") ContactButtons,
}

@Serializable
enum class ConversationRating(val wire: String) {
    @SerialName("10") Unsatisfactory("10"),
    @SerialName("20") Bad("20"),
    @SerialName("30") Satisfactory("30"),
    @SerialName("40") Good("40"),
    @SerialName("50") Excellent("50");

    companion object {
        fun fromWire(value: String?): ConversationRating? =
            entries.firstOrNull { it.wire == value || it.name.equals(value, ignoreCase = true) }
    }
}

@Serializable
data class DateObject(
    val date: String = "",
    val timezone: String = "UTC",
)

@Serializable
data class BotButton(
    val label: String = "",
    val value: String = "",
)

@Serializable
data class ContactForm(
    val method: String = "phone",
    val value: String? = null,
    val valid: Boolean? = null,
    val placeholder: String? = null,
)

@Serializable
data class MessageFrom(
    val avatar: String? = null,
    val avatarDefault: String? = null,
    val id: String? = null,
    val isOnline: Boolean? = null,
    val isOperator: Boolean? = null,
    val messenger: String? = null,
    val name: String? = null,
)

@Serializable
data class Operator(
    val name: String = "",
    val isOnline: Boolean = false,
    val avatar: String? = null,
    val id: String? = null,
    val messenger: String? = null,
    val avatarDefault: String? = null,
)

@Serializable
data class ChannelMessenger(
    val id: String? = null,
    val name: String = "",
    val desktopUrl: String? = null,
    val mobileUrl: String? = null,
)

@Serializable
data class InvitationCondition(
    val condition: String = "",
    val conditionAction: String = "",
    val target: String? = null,
)

@Serializable
data class InvitationRules(
    val messages: List<JsonObject> = emptyList(),
    val conditions: List<InvitationCondition> = emptyList(),
    val index: Int? = null,
    val shown: Boolean? = null,
    val iteration: Int? = null,
    val isEnabled: JsonElement? = null,
)

@Serializable
data class WorkRange(
    val day: String = "",
    val from: String = "",
    val to: String = "",
)

@Serializable
data class WidgetConfigData(
    val name: String = "",
    val projectName: String = "",
    val projectId: String = "",
    val language: String? = null,
    val backgroundColor: String? = null,
    val backgroundImage: String? = null,
    val position: String? = null,
    val enabledChat: Boolean = true,
    val soundEnabled: Boolean? = null,
    val operators: List<Operator> = emptyList(),
    val channels: List<ChannelMessenger> = emptyList(),
    val rules: List<InvitationRules> = emptyList(),
    val workRange: List<WorkRange> = emptyList(),
    val holidays: List<JsonElement> = emptyList(),
    val timezone: String? = null,
    val showLink: Boolean = false,
    val buttonTextEnabled: Boolean? = null,
)

@Serializable
data class ApiEnvelope<T>(
    val success: Boolean = true,
    val error: List<String> = emptyList(),
    val data: T? = null,
    val datetime: String? = null,
)

@Serializable
data class UploadFileData(
    val attachment: String? = null,
    val attachmentId: String? = null,
    val attachmentUrl: String? = null,
)

@Serializable
data class Message(
    val id: String? = null,
    val tempId: String = "",
    val appealId: String? = null,
    val message: String? = null,
    val originalMessage: String? = null,
    val attachment: String? = null,
    val attachmentUrl: String? = null,
    val attachmentPreviewUrl: String? = null,
    val attachmentId: String? = null,
    val isOwn: Boolean = false,
    val date: DateObject? = null,
    val dateToDisplay: String? = null,
    val avatar: String? = null,
    val avatarDefault: String? = null,
    val messageType: MessageType? = null,
    val emojiOnly: Boolean = false,
    val progress: Int? = null,
    val isForm: Boolean = false,
    val formId: String? = null,
    val formType: FormType? = null,
    val ratingValue: ConversationRating? = null,
    val contactForm: ContactForm? = null,
    val operator: Operator? = null,
    val from: MessageFrom? = null,
    val fromInvite: Boolean = false,
    val serviceMessage: Boolean = false,
    val errorMessage: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val unread: Boolean = false,
    val seen: Boolean = false,
    val status: MessageStatus? = null,
    val size: Long? = null,
)

data class LocalFile(
    val uri: String,
    val name: String,
    val type: String,
    val size: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val previewUri: String? = null,
    val bytes: ByteArray? = null,
)

data class UploadedAttachment(
    val attachment: String,
    val attachmentId: String,
    val attachmentUrl: String,
    val type: MessageType,
    val width: Int? = null,
    val height: Int? = null,
    val size: Long? = null,
)

enum class ChatBackground { White, Doodle, Circles, Scribbles }

val DEFAULT_CHAT_BACKGROUND = ChatBackground.White

val CHAT_BACKGROUNDS = ChatBackground.entries

val CHAT_BACKGROUND_PICKER = listOf(
    ChatBackground.Doodle,
    ChatBackground.Circles,
    ChatBackground.Scribbles,
    ChatBackground.White,
)

fun isChatBackground(value: String?): Boolean =
    ChatBackground.entries.any { it.name.equals(value, ignoreCase = true) }

fun parseChatBackground(value: String?): ChatBackground? =
    ChatBackground.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }

data class AdditionalPersonInfo(
    val name: String? = null,
    val email: String? = null,
    val avatar: String? = null,
    val phone: String? = null,
    val payload: Map<String, String> = emptyMap(),
)

data class DialogClosedPayload(
    val status: Int,
)

data class DialogRatedPayload(
    val id: String,
    val formValue: String,
)
