package tem.csdn.dao

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
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
    val displayId = varchar("display_ud", 36).uniqueIndex()
    val name = varchar("name", 50)
    val displayName = varchar("display_name", 50)
    val position = varchar("position", 50)
    val photo = bool("image").nullable().default(false)
    val github = varchar("github", 256).nullable()
    val qq = varchar("qq", 20).nullable()
    val weChat = varchar("wechat", 30).nullable()

    override val primaryKey = PrimaryKey(id)
}

object Messages : Table() {
    val id = long("id").autoIncrement()
    val content = varchar("content", 1024)
    val timestamp = integer("timestamp")
    val image = bool("image").nullable().default(false)
    val author = varchar("user_id", 36) references Users.id

    override val primaryKey = PrimaryKey(id)
}

