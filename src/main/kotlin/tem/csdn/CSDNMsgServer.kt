package tem.csdn

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.jackson.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import tem.csdn.dao.Messages
import tem.csdn.dao.Users
import tem.csdn.dao.connectToFile
import tem.csdn.model.*
import java.io.FileNotFoundException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.time.*

fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    val databaseFile = environment.config.propertyOrNull("csdnmsg.database")?.getString() ?: "data/data.db"
    val resourcesSavePath = environment.config.propertyOrNull("csdnmsg.resources")?.getString() ?: "data"
    val chatDisplayName = environment.config.property("csdnmsg.name").getString()
    val resources = Resources(resourcesSavePath)
    connectToFile(databaseFile)

    install(ContentNegotiation) {
        jackson {
            //enable(SerializationFeature.INDENT_OUTPUT)
        }
    }
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(60) // Disabled (null) by default
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE // Disabled (max value). The connection will be closed if surpassed this length.
        masking = false
    }
    install(StatusPages) {
        exception<NoParameterException> { param ->
            call.respond(HttpStatusCode.BadRequest, param.message ?: param.parameterName)
        }
        exception<ResultException> { resultException ->
            call.respond(resultException.result)
        }
        exception<FileNotFoundException> { nouFound ->
            call.respond(HttpStatusCode.NotFound, nouFound.message ?: "")
        }
    }
    install(Sessions) {
        cookie<LoginSession>("LOGIN_SESSION")
    }


    val objectMapper = jacksonObjectMapper()

    routing {
        val sessions = ConcurrentHashMap<String, DefaultWebSocketSession>()
        get("/img/{method}/{id}") {
            // if photo then id is User.DisplayId else if image then id is Message.Id
            call.sessions.get<LoginSession>()?.currentUser ?: Result.NOT_LOGIN.throwOut()
            val id = call.parameters["id"]!!
            when (call.parameters["method"]!!) {
                "photo" -> {
                    call.respondOutputStream {
                        resources.get(Resources.ResourcesType.PHOTO, id).transferTo(this)
                    }
                }
                "image" -> {
                    call.respondOutputStream {
                        resources.get(Resources.ResourcesType.IMAGE, id).transferTo(this)
                    }
                }
                "chat" -> {
                    call.respondOutputStream {
                        resources.get(Resources.ResourcesType.CHAT, 0).transferTo(this)
                    }
                }
            }
        }
        post("/photo") {
            val user = call.sessions.get<LoginSession>()?.currentUser ?: Result.NOT_LOGIN.throwOut()
            val multipart = call.receiveMultipart()
            multipart.forEachPart { partData ->
                if (partData is PartData.FileItem) {
                    resources.save(Resources.ResourcesType.PHOTO, user.displayId, partData.streamProvider())
                    transaction {
                        Users.update({ Users.displayId eq user.displayId }) {
                            it[photo] = true
                        }
                    }
                    return@forEachPart
                }
            }
        }
        get("/messages") {
            call.sessions.get<LoginSession>()?.currentUser ?: Result.NOT_LOGIN.throwOut()
            val parameters = call.request.queryParameters
            val after = parameters["after"]?.toLongOrNull()
            val afterId = parameters["after_id"]?.toLongOrNull()
            if (after == null && afterId == null) {
                val currentTime = LocalDate.now().plusDays(-7).toEpochSecond(LocalTime.MIN, OffsetDateTime.now().offset)
                val messages = transaction {
                    (Messages leftJoin Users).slice(Messages.author, Users.displayId).select {
                        Messages.timestamp greaterEq currentTime
                    }.map { it.toMessage() }
                }
                call.respond(Result(0, null, messages))
            } else {
                val messages = transaction {
                    (Messages leftJoin Users).slice(Messages.author, Users.displayId).select {
                        if (after != null) {
                            Messages.timestamp greaterEq after
                        } else {
                            Messages.id greater afterId!!
                        }
                    }.map { it.toMessage() }
                }
                call.respond(Result(0, null, messages))
            }
        }
        get("/profiles") {
            val user = call.sessions.get<LoginSession>()?.currentUser ?: Result.NOT_LOGIN.throwOut()
            val profiles =
                transaction {
                    Users.select { (Users.id inList sessions.keys().toList()) and (Users.displayId neq user.displayId) }
                        .map { it.toUser() }
                }
            call.respond(Result(0, null, profiles))
        }
        post("/profile") {
            val user = call.sessions.get<LoginSession>()?.currentUser ?: Result.NOT_LOGIN.throwOut()
            val parameters = call.receiveParameters()
            transaction {
                Users.update({ Users.displayId eq user.displayId }) {
                    parameters["name"]?.let { name ->
                        it[Users.name] = name
                    }
                    parameters["displayName"]?.let { displayName ->
                        it[Users.displayName] = displayName
                    }
                    parameters["position"]?.let { position ->
                        it[Users.position] = position
                    }
                    parameters["github"]?.let { github ->
                        it[Users.github] = github
                    }
                    parameters["qq"]?.let { qq ->
                        it[Users.qq] = qq
                    }
                    parameters["weChat"]?.let { weChat ->
                        it[Users.weChat] = weChat
                    }
                }
            }
        }
        get("/chat_name") {
            call.respond(Result(0, null, chatDisplayName))
        }
        get("/count") {
            call.sessions.get<LoginSession>()?.currentUser ?: Result.NOT_LOGIN.throwOut()
            call.respond(Result(0, null, sessions.size))
        }
        post("/csdnchat/{id}") {
            val id = call.parameters["id"]!!
            val userResultRow =
                transaction { Users.select { Users.id eq id }.singleOrNull() }
            val user = if (userResultRow != null) {
                userResultRow.toUser()
            } else {
                val displayId = UUID.randomUUID().toString()
                val name = "用户${('a'..'z').randomString(6)}"
                transaction {
                    Users.insert {
                        it[this.displayId] = displayId
                        it[this.id] = id
                        it[this.name] = name
                        it[this.displayName] = name
                        it[this.position] = ""
                        it[this.photo] = false
                        it[this.github] = null
                        it[this.qq] = null
                        it[this.weChat] = null
                    }
                }
                User(displayId, name, name, "", false, null, null, null)
            }
            call.sessions.set(LoginSession(user))
            call.respond(Result(0, null, user))
        }

        suspend fun DefaultWebSocketSession.sendRetry(frame: Frame, cnt: Int = 3): Boolean {
            if (cnt == 0) {
                return false
            }
            return try {
                this.send(frame)
                true
            } catch (e: ClosedReceiveChannelException) {
                false
            } catch (e: Throwable) {
                val reason = closeReason.await()!!
                log.error("a error has throw when send retry(${cnt}):${e.message}|${reason.code}:${reason.message}", e)
                sendRetry(frame, cnt - 1)
            }
        }

        suspend fun DefaultWebSocketSession.sendToAll(wrapper: WebSocketFrameWrapper) {
            sessions.forEach { (id, session) ->
                // 给自己也发,代表ack
                //if (session != this) {
                if (!session.sendRetry(Frame.Text(objectMapper.writeValueAsString(wrapper)))) {
                    log.error("send msg to id[${id}] has failed")
                }
                //}
            }
        }

        webSocket("/csdnchat/{id}") {
            val id = call.parameters["id"]!!
            val userResultRow =
                transaction { Users.select { Users.id eq id }.singleOrNull() } ?: Result.NOT_LOGIN.throwOut()
            val user = userResultRow.toUser()
            if (sessions[id] != null) {
                Result.ID_MULTI_ERROR.throwOut()
            }
            log.info("user[${user.name}](${user.displayId}) connected!")
            sessions[id] = this
            GlobalScope.launch {
                sendToAll(
                    WebSocketFrameWrapper(
                        WebSocketFrameWrapper.FrameType.NEW_CONNECTION,
                        user
                    )
                )
            }
            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val receivedText = frame.readText()
                            val message = transaction {
                                val row = Messages.insert {
                                    it[author] = user.displayId
                                    it[image] = false
                                    it[content] = receivedText
                                    it[timestamp] = (System.currentTimeMillis() / 1000).toInt()
                                }
                                row.resultedValues!!.single().toMessage(user)
                            }
                            GlobalScope.launch {
                                sendToAll(
                                    WebSocketFrameWrapper(
                                        WebSocketFrameWrapper.FrameType.MESSAGE,
                                        message
                                    )
                                )
                            }
                        }
                        is Frame.Binary -> {
                            val receivedBytes = frame.readBytes()
                            val message = transaction {
                                val row = Messages.insert {
                                    it[author] = user.displayId
                                    it[image] = true
                                    it[content] = ""
                                    it[timestamp] = (System.currentTimeMillis() / 1000).toInt()
                                }
                                row.resultedValues!!.single().toMessage(user).apply {
                                    resources.save(Resources.ResourcesType.IMAGE, this.id, receivedBytes)
                                }
                            }
                            GlobalScope.launch {
                                sendToAll(
                                    WebSocketFrameWrapper(
                                        WebSocketFrameWrapper.FrameType.MESSAGE,
                                        message
                                    )
                                )
                            }
                        }
                        else -> continue
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                val reason = closeReason.await()!!
                log.info("session has closed:${e.message}|${reason.code}:${reason.message}")
            } catch (e: Throwable) {
                val reason = closeReason.await()!!
                log.error("a error has throw:${e.message}|${reason.code}:${reason.message}", e)
            } finally {
                GlobalScope.launch {
                    sendToAll(
                        WebSocketFrameWrapper(
                            WebSocketFrameWrapper.FrameType.NEW_DISCONNECTION,
                            user
                        )
                    )
                }
                log.info("session[${id}] has removed")
                sessions.remove(id)
            }
        }
    }
}


