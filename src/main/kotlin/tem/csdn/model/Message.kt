package tem.csdn.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import tem.csdn.NoArg
import tem.csdn.Resources
import tem.csdn.ResourcesSaveResult
import tem.csdn.sha256
import java.io.InputStream
import java.lang.IllegalArgumentException

@NoArg
interface Message {
    val id: Long
    val clientId: ClientId
    val content: String
    val timestamp: Int
    val type: MessageType
    val author: User

    @JsonIgnore
    fun getShowText(): String
}

@NoArg
interface Binary {
    @JsonIgnore
    fun getSha256(): String

    fun getBinary(resources: Resources): ByteArray {
        return resources.get(getSha256()).use { it.readAllBytes() }
    }

    fun getInputStream(resources: Resources): InputStream {
        return resources.get(getSha256())
    }

    fun save(resources: Resources, byteArray: ByteArray): ResourcesSaveResult {
        return resources.save(byteArray)
    }

    fun save(resources: Resources, binaryContents: List<BinaryContent>): ResourcesSaveResult {
        return resources.save(binaryContents)
    }

    fun save(resources: Resources, inputStream: InputStream): ResourcesSaveResult {
        return resources.save(inputStream)
    }
}

@NoArg
abstract class BinaryMessage(
    override val id: Long,
    override val clientId: ClientId,
    override val content: String,
    override val timestamp: Int,
    override val type: MessageType,
    override val author: User
) : Message, Binary {
    override fun toString(): String {
        return "TextMessage[type=${type}]${getShowText()}:${getSha256()}"
    }
}

@NoArg
abstract class TextMessage(
    override val id: Long,
    override val clientId: ClientId,
    override val content: String,
    override val timestamp: Int,
    override val type: MessageType,
    override val author: User
) : Message {
    override fun toString(): String {
        return "TextMessage[type=${type}]${getShowText()}"
    }
}

@NoArg
class TextPlainMessage @Deprecated(
    "",
    ReplaceWith("newMessage(id,clientId,content,timestamp,author,MessageType.TEXT_PLAIN)"),
    DeprecationLevel.ERROR
) constructor(id: Long, clientId: ClientId, content: String, timestamp: Int, author: User) :
    TextMessage(id, clientId, content, timestamp, MessageType.TEXT_PLAIN, author) {
    override fun getShowText(): String {
        return content
    }
}

@NoArg
class ImageMessage @Deprecated(
    "",
    ReplaceWith("newMessage(id,clientId,content,timestamp,author,MessageType.IMAGE)"),
    DeprecationLevel.ERROR
) constructor(id: Long, clientId: ClientId, content: String, timestamp: Int, author: User) :
    BinaryMessage(id, clientId, content, timestamp, MessageType.IMAGE, author) {
    override fun getSha256(): String {
        return content
    }

    override fun getShowText(): String {
        return "[图片]"
    }
}

@NoArg
class LocationMessage @Deprecated(
    "",
    ReplaceWith("newMessage(id,clientId,content,timestamp,author,MessageType.LOCATION)"),
    DeprecationLevel.ERROR
) constructor(id: Long, clientId: ClientId, content: String, timestamp: Int, author: User) :
    TextMessage(id, clientId, content, timestamp, MessageType.LOCATION, author) {
    val latitude: Double
    val longitude: Double
    val locationName: String

    init {
        val (latitude, longitude, locationName) = content.split("|")
        this.latitude = latitude.toDouble()
        this.longitude = longitude.toDouble()
        this.locationName = locationName
    }

    override fun getShowText(): String {
        return "[地点]${locationName}"
    }
}

@NoArg
class FileMessage @Deprecated(
    "",
    ReplaceWith("newMessage(id,clientId,content,timestamp,author,MessageType.FILE)"),
    DeprecationLevel.ERROR
) constructor(id: Long, clientId: ClientId, content: String, timestamp: Int, author: User) :
    BinaryMessage(id, clientId, content, timestamp, MessageType.FILE, author) {
    private val sha256: String
    val filename: String

    init {
        val (sha256, filename) = content.split("|")
        this.sha256 = sha256
        this.filename = filename
    }

    override fun getSha256(): String {
        return sha256
    }

    override fun getShowText(): String {
        return "[文件]${filename}"
    }
}

@NoArg
class VoiceMessage @Deprecated(
    "",
    ReplaceWith("newMessage(id,clientId,content,timestamp,author,MessageType.VOICE)"),
    DeprecationLevel.ERROR
) constructor(id: Long, clientId: ClientId, content: String, timestamp: Int, author: User) :
    BinaryMessage(id, clientId, content, timestamp, MessageType.VOICE, author) {
    override fun getSha256(): String {
        return content
    }

    override fun getShowText(): String {
        return "[语音]"
    }
}

@NoArg
class VideoMessage @Deprecated(
    "",
    ReplaceWith("newMessage(id,clientId,content,timestamp,author,MessageType.VIDEO)"),
    DeprecationLevel.ERROR
) constructor(id: Long, clientId: ClientId, content: String, timestamp: Int, author: User) :
    BinaryMessage(id, clientId, content, timestamp, MessageType.VIDEO, author) {
    override fun getSha256(): String {
        return content
    }

    override fun getShowText(): String {
        return "[视频]"
    }
}

@Suppress("DEPRECATION_ERROR")
fun newMessage(
    id: Long,
    clientId: ClientId,
    content: String,
    timestamp: Int,
    author: User,
    type: MessageType
): Message {
    return when (type) {
        MessageType.TEXT_PLAIN -> TextPlainMessage(id, clientId, content, timestamp, author)
        MessageType.IMAGE -> ImageMessage(id, clientId, content, timestamp, author)
        MessageType.LOCATION -> LocationMessage(id, clientId, content, timestamp, author)
        MessageType.FILE -> FileMessage(id, clientId, content, timestamp, author)
        MessageType.VOICE -> VoiceMessage(id, clientId, content, timestamp, author)
        MessageType.VIDEO -> VideoMessage(id, clientId, content, timestamp, author)
    }
}

@Suppress("DEPRECATION_ERROR")
fun newMessageFromJson(
    json: String,
    objectMapper: ObjectMapper
): Message {
    val tree = objectMapper.readTree(json)
    return when (MessageType.parse(tree["type"].textValue())) {
        MessageType.TEXT_PLAIN -> objectMapper.convertValue<TextPlainMessage>(tree)
        MessageType.IMAGE -> objectMapper.convertValue<ImageMessage>(tree)
        MessageType.LOCATION -> objectMapper.convertValue<LocationMessage>(tree)
        MessageType.FILE -> objectMapper.convertValue<FileMessage>(tree)
        MessageType.VOICE -> objectMapper.convertValue<VoiceMessage>(tree)
        MessageType.VIDEO -> objectMapper.convertValue<VideoMessage>(tree)
    }
}

class BinaryContent(
    val start: Long,
    val content: ByteArray
)


class RawBinaryMessage(
    val clientId: ClientId,
    val binaryContents: List<BinaryContent>,
    val type: MessageType,
    val extendContent: String?,
) : Binary {
    override fun getSha256(): String {
        return binaryContents.sha256()
    }

    fun save(resources: Resources): ResourcesSaveResult {
        return save(resources, binaryContents)
    }

    fun getContent(sha256: String): String {
        return when (type) {
            MessageType.TEXT_PLAIN -> throw IllegalArgumentException()
            MessageType.IMAGE -> sha256
            MessageType.LOCATION -> throw IllegalArgumentException()
            MessageType.FILE -> "${sha256}|${extendContent}"
            MessageType.VOICE -> sha256
            MessageType.VIDEO -> sha256
        }
    }
}