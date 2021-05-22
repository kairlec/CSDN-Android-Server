package tem.csdn.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.jetbrains.exposed.sql.ResultRow
import tem.csdn.dao.Messages
import tem.csdn.dao.Users


data class User(
    val displayId: String,
    val name: String,
    val displayName: String,
    val position: String,
    val photo: Boolean?,
    val github: String?,
    val qq: String?,
    val weChat: String?,
    @JsonIgnore
    var lastSyncFailed: Boolean
)

data class Message(
    val id: Long,
    val content: String,
    val timestamp: Int,
    val image: Boolean?,
    val author: User,
)

fun ResultRow.toUser(): User {
    return User(
        this[Users.displayId],
        this[Users.name],
        this[Users.displayName],
        this[Users.position],
        this[Users.photo],
        this[Users.github],
        this[Users.qq],
        this[Users.weChat],
        this[Users.lastSyncFail]
    )
}

fun ResultRow.toMessage(): Message {
    return Message(
        this[Messages.id],
        this[Messages.content],
        this[Messages.timestamp],
        this[Messages.image],
        this.toUser(),
    )
}

fun ResultRow.toMessage(user: User): Message {
    return Message(
        this[Messages.id],
        this[Messages.content],
        this[Messages.timestamp],
        this[Messages.image],
        user,
    )
}

data class LoginSession(val currentUser: User)