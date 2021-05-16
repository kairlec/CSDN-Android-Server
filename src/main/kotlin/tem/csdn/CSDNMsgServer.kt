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
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
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
    val objectMapper = jacksonObjectMapper()

    routing {
        val sessions = ConcurrentHashMap<String, DefaultWebSocketSession>()
        get("/img/{method}/{id}") {
            // if photo then id is User.DisplayId else if image then id is Message.Id
            call.sessions.get("user") as User? ?: Result.NOT_LOGIN.throwOut()
            val id = call.parameters["id"]!!
            val method = call.parameters["method"]!!
            when (method) {
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
            }
        }
        post("/photo") {
            val user = call.sessions.get("user") as User? ?: Result.NOT_LOGIN.throwOut()
            val multipart = call.receiveMultipart()
            multipart.forEachPart {
                if (it is PartData.FileItem) {
                    resources.save(Resources.ResourcesType.PHOTO, user.displayId, it.streamProvider())
                    return@forEachPart
                }
            }
        }
        get("/message") {
            call.sessions.get("user") as User? ?: Result.NOT_LOGIN.throwOut()
            val parameters = call.receiveParameters()
            val after = parameters["after"]?.toLongOrNull()
            val afterId = parameters["after_id"]?.toLongOrNull()
            if (after == null && afterId == null) {
                val currentTime = LocalDate.now().plusDays(-7).toEpochSecond(LocalTime.MIN, OffsetDateTime.now().offset)
                val messages = (Users rightJoin Messages).slice(Users.id, Messages.author).select {
                    Messages.timestamp greaterEq currentTime
                }.map { it.toMessage() }
                call.respond(Result(0, null, messages))
            } else {
                val messages = (Users rightJoin Messages).slice(Users.id, Messages.author).select {
                    if (after != null) {
                        Messages.timestamp greaterEq after
                    } else {
                        Messages.id greaterEq afterId!!
                    }
                }.map { it.toMessage() }
                call.respond(Result(0, null, messages))
            }
        }
        get("/count") {
            call.sessions.get("user") as User? ?: Result.NOT_LOGIN.throwOut()
            call.respond(Result(0, null, sessions.size))
        }
        post("/csdnchat/{id}") {
            val id = call.parameters["id"]!!
            val userResultRow =
                transaction { Users.select { Users.id eq id }.singleOrNull() }
            if (userResultRow != null) {
                val user = userResultRow.toUser()
                call.respond(Result(0, null, user))
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
                call.respond(Result(0, null, User(displayId, name, name, "", false, null, null, null)))
            }
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

        suspend fun DefaultWebSocketSession.sendToOther(frame: Frame) {
            sessions.forEach { (id, session) ->
                if (session != this) {
                    if (!session.sendRetry(frame)) {
                        log.error("send msg to id[${id}] has failed")
                    }
                }
            }
        }

        suspend fun DefaultWebSocketSession.sendToOther(text: String) {
            sessions.forEach { (id, session) ->
                if (session != this) {
                    if (!session.sendRetry(Frame.Text(text))) {
                        log.error("send msg to id[${id}] has failed")
                    }
                }
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
                            GlobalScope.launch { sendToOther(objectMapper.writeValueAsString(message)) }
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
                            GlobalScope.launch { sendToOther(objectMapper.writeValueAsString(message)) }
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
                sessions.remove(id)
            }
        }
    }
}


