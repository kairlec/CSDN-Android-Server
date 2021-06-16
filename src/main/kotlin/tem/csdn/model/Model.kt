package tem.csdn.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.jetbrains.exposed.sql.ResultRow
import tem.csdn.NoArg
import tem.csdn.dao.Messages
import tem.csdn.dao.Users

@NoArg
data class User(
    val displayId: String,
    val name: String,
    val displayName: String,
    val position: String,
    val photo: String?,
    val github: String?,
    val qq: String?,
    val weChat: String?,
    @JsonIgnore
    var lastSyncFailed: Boolean
) {
    class Builder constructor(
        user: User
    ) {
        var displayId: String = user.displayId
        var name: String = user.name
        var displayName: String = user.displayName
        var position: String = user.position
        var photo: String? = user.photo
        var github: String? = user.github
        var qq: String? = user.qq
        var weChat: String? = user.weChat
        var lastSyncFailed: Boolean = user.lastSyncFailed
    }

    constructor(builder: Builder) : this(
        builder.displayId,
        builder.name,
        builder.displayName,
        builder.position,
        builder.photo,
        builder.github,
        builder.qq,
        builder.weChat,
        builder.lastSyncFailed
    )

    inline fun copyBuilder(builderHandler: Builder.() -> Unit): User {
        val builder = Builder(this)
        builderHandler(builder)
        return User(builder)
    }
}


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
    return newMessage(
        this[Messages.id],
        ClientId(this[Messages.clientId]),
        this[Messages.content],
        this[Messages.timestamp],
        this.toUser(),
        this[Messages.type],
    )
}

fun ResultRow.toMessage(user: User): Message {
    return newMessage(
        this[Messages.id],
        ClientId(this[Messages.clientId]),
        this[Messages.content],
        this[Messages.timestamp],
        user,
        this[Messages.type],
    )
}

data class LoginSession(val currentUser: User)