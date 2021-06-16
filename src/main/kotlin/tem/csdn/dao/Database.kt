package tem.csdn.dao

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import tem.csdn.model.MessageType
import java.sql.Connection

fun connectToFile(file: String) {
    Database.connect("jdbc:sqlite:${file}", "org.sqlite.JDBC")
    TransactionManager.manager.defaultIsolationLevel =
        Connection.TRANSACTION_SERIALIZABLE
    transaction {
        addLogger(StdOutSqlLogger)
        SchemaUtils.create(Users, Messages)
    }
}

object Users : Table() {
    val id = varchar("id", 36)
    val displayId = varchar("display_id", 36).uniqueIndex()
    val name = varchar("name", 50)
    val displayName = varchar("display_name", 50)
    val position = varchar("position", 50)
    val photo = varchar("image", 64).nullable().default(null)
    val github = varchar("github", 256).nullable()
    val qq = varchar("qq", 20).nullable()
    val weChat = varchar("wechat", 30).nullable()
    val lastSyncFail = bool("last_sync_failed").default(false)

    override val primaryKey = PrimaryKey(id)
}

object Messages : Table() {
    val id = long("id").autoIncrement()
    val clientId = varchar("client_id", 36).uniqueIndex()
    val content = varchar("content", 1024)
    val timestamp = integer("timestamp")
    val type = enumeration("type", MessageType::class)
    val author = varchar("user_id", 36) references Users.displayId

    override val primaryKey = PrimaryKey(id)
}

