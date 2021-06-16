package tem.csdn

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.apache.tika.Tika
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import tem.csdn.dao.Messages
import tem.csdn.dao.Users
import tem.csdn.dao.connectToFile
import tem.csdn.intf.TextFrameActionConvertable
import tem.csdn.model.*
import tem.csdn.model.Result.Companion.ID_MULTI_ERROR
import tem.csdn.util.ClientIdDeserializer
import tem.csdn.util.ClientIdSerializer
import java.io.FileNotFoundException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

fun main(args: Array<String>): Unit = io.ktor.server.cio.EngineMain.main(args)

@Suppress("unused", "BlockingMethodInNonBlockingContext", "SpellCheckingInspection")
// Referenced in application.conf
fun Application.module() {
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
        timeout = Duration.ofSeconds(30)
        maxFrameSize = Long.MAX_VALUE // Disabled (max value). The connection will be closed if surpassed this length.
        masking = false
    }
    install(StatusPages) {
        exception<NoParameterException> { param ->
            call.respond(HttpStatusCode.BadRequest, param.message ?: param.parameterName)
        }
        exception<ResultException> { resultException ->
            log.error("result error:${resultException.result}", resultException)
            call.respond(resultException.result)
        }
        exception<FileNotFoundException> { notFound ->
            log.info("404 -> ${notFound.message}")
            call.respond(HttpStatusCode.NotFound, notFound.message ?: "")
        }
    }
    install(Sessions) {
        cookie<LoginSession>("LOGIN_SESSION")
    }


    val objectMapper = jacksonObjectMapper()
    objectMapper.registerModule(SimpleModule().apply {
        addDeserializer(ClientId::class.java, ClientIdDeserializer())
        addSerializer(ClientId::class.java, ClientIdSerializer())
    })
    val tika = Tika()

    routing {
        val sessions = ConcurrentHashMap<String, Pair<User, DefaultWebSocketSession>>()
        val displayIdSessions = ConcurrentHashMap<String, Pair<User, DefaultWebSocketSession>>()

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

        suspend fun Pair<User, DefaultWebSocketSession>.trySendSync(id: String, wrapper: TextFrameAction) {
            if (!this.second.sendRetry(Frame.Text(objectMapper.writeValueAsString(wrapper)))) {
                log.error("send msg to id[${id}] has failed")
                transaction {
                    Users.update({ Users.displayId eq this@trySendSync.first.displayId }) {
                        it[lastSyncFail] = true
                    }
                }
                this.first.lastSyncFailed = true
            }
        }

        suspend fun DefaultWebSocketSession.sendToAll(wrapper: TextFrameAction) {
            log.info("(all)a new ${wrapper.type.name} event to send")
            sessions.forEach { (id, sessionPair) ->
                // 给自己也发,代表ack
                if (!sessionPair.first.lastSyncFailed) {
                    sessionPair.trySendSync(id, wrapper)
                } else {
                    (sessionPair.second as DefaultWebSocketServerSession).sendRetry(
                        Frame.Text(
                            objectMapper.writeValueAsString(
                                TextFrameAction(TextFrameActionType.ACTION_NEED_SYNC, "")
                            )
                        )
                    )
                }
            }
        }

        suspend fun DefaultWebSocketSession.sendToSelf(wrapper: TextFrameAction) {
            log.info("(self)a new ${wrapper.type.name} event to send")
            if (!this.sendRetry(Frame.Text(objectMapper.writeValueAsString(wrapper)))) {
                log.error("send msg to self has failed")
            }
        }

        suspend fun DefaultWebSocketSession.sendToOther(wrapper: TextFrameAction) {
            log.info("(other)a new ${wrapper.type.name} event to send")
            sessions.forEach { (id, sessionPair) ->
                if (!sessionPair.first.lastSyncFailed) {
                    if (sessionPair.second != this) {
                        sessionPair.trySendSync(id, wrapper)
                    }
                } else {
                    (sessionPair.second as DefaultWebSocketServerSession).sendRetry(
                        Frame.Text(
                            objectMapper.writeValueAsString(
                                TextFrameAction(TextFrameActionType.ACTION_NEED_SYNC, "")
                            )
                        )
                    )
                }
            }
        }

        get("/image/{sha256}") {
            // if photo then id is User.DisplayId else if image then id is Message.Id
//            call.sessions.get<LoginSession>()?.currentUser
//                ?: call.request.header("auth-uuid")?.let {
//                    transaction { Users.select { Users.id eq it }.singleOrNull() }
//                }?.toUser() ?: Result.NOT_LOGIN.throwOut()
            val sha256 = call.parameters["sha256"]!!
            call.respondOutputStream(
                ContentType.parse(
                    resources.get(sha256).use { tika.detect(it) })
            ) {
                resources.get(sha256).transferTo(this)
            }
        }
        get("/upc/{sha256}") {
            call.sessions.get<LoginSession>()?.currentUser ?: Result.NOT_LOGIN.throwOut()
            val sha256 = call.parameters["sha256"]!!
            call.respond(Result(0, null, resources.exists(sha256)))
        }
        post("/photo") {
            val user = call.sessions.get<LoginSession>()?.currentUser ?: Result.NOT_LOGIN.throwOut()
            val multipart = call.receiveMultipart()
            val newUser = user.copyBuilder {
                multipart.forEachPart { partData ->
                    if (partData is PartData.FileItem) {
                        transaction {
                            resources.save(partData.streamProvider()).`try` { sha256 ->
                                Users.update({ Users.displayId eq user.displayId }) {
                                    it[photo] = sha256
                                }
                                this@copyBuilder.photo = sha256
                            }
                        }
                        return@forEachPart
                    }
                }
            }
            call.sessions.set(LoginSession(newUser))
            GlobalScope.launch {
                for (displayIdSession in displayIdSessions) {
                    if (displayIdSession.key == user.displayId) {
                        displayIdSession.value.second.sendToOther(
                            TextFrameAction(
                                TextFrameActionType.ACTION_UPDATE_USER,
                                newUser
                            )
                        )
                        break
                    }
                }
            }
            call.respond(Result(0, null, newUser))
        }
        get("/messages") {
            val user = call.sessions.get<LoginSession>()?.currentUser ?: Result.NOT_LOGIN.throwOut()
            val parameters = call.request.queryParameters
            val after = parameters["after"]?.toLongOrNull()
            val afterId = parameters["after_id"]?.toLongOrNull()
            val messages = if (after == null && afterId == null) {
                val currentTime = LocalDate.now().plusDays(-7).toEpochSecond(LocalTime.MIN, OffsetDateTime.now().offset)
                transaction {
                    (Messages leftJoin Users).select {
                        Messages.timestamp greaterEq currentTime
                    }.map { it.toMessage() }
                }
            } else {
                transaction {
                    (Messages leftJoin Users).select {
                        if (after != null) {
                            Messages.timestamp greaterEq after
                        } else {
                            Messages.id greater afterId!!
                        }
                    }.map { it.toMessage() }
                }
            }
            if (user.lastSyncFailed) {
                transaction {
                    Users.update({ Users.displayId eq user.displayId }) {
                        it[lastSyncFail] = false
                    }
                }
            }
            call.respond(Result(0, null, messages))
        }
        get("/profiles") {
            call.sessions.get<LoginSession>()?.currentUser ?: Result.NOT_LOGIN.throwOut()
            val profiles =
                transaction {
                    Users.selectAll()// { (Users.id inList sessions.keys().toList()) and (Users.displayId neq user.displayId) }
                        .map { it.toUser() }
                }
            call.respond(Result(0, null, profiles))
        }
        post("/profile") {
            val user = call.sessions.get<LoginSession>()?.currentUser ?: Result.NOT_LOGIN.throwOut()
            val parameters = call.receiveParameters()
            val newUser = user.copyBuilder {
                transaction {
                    Users.update({ Users.displayId eq user.displayId }) {
                        parameters["name"]?.let { name ->
                            it[Users.name] = name
                            this@copyBuilder.name = name
                        }
                        parameters["displayName"]?.let { displayName ->
                            it[Users.displayName] = displayName
                            this@copyBuilder.displayName = displayName
                        }
                        parameters["position"]?.let { position ->
                            it[Users.position] = position
                            this@copyBuilder.position = position
                        }
                        parameters["github"]?.let { github ->
                            it[Users.github] = github
                            this@copyBuilder.github = github
                        }
                        parameters["qq"]?.let { qq ->
                            it[Users.qq] = qq
                            this@copyBuilder.qq = qq
                        }
                        parameters["weChat"]?.let { weChat ->
                            it[Users.weChat] = weChat
                            this@copyBuilder.weChat = weChat
                        }
                    }
                }
            }
            call.sessions.set(LoginSession(newUser))
            GlobalScope.launch {
                for (displayIdSession in displayIdSessions) {
                    if (displayIdSession.key == user.displayId) {
                        displayIdSession.value.second.sendToOther(
                            TextFrameAction(
                                TextFrameActionType.ACTION_UPDATE_USER,
                                newUser
                            )
                        )
                        break
                    }
                }
            }
            call.respond(Result(0, null, newUser))
        }
        get("/chat_name") {
            call.respond(Result(0, null, chatDisplayName))
        }
        get("/count") {
            val user = call.sessions.get<LoginSession>()?.currentUser ?: Result.NOT_LOGIN.throwOut()
            if (displayIdSessions.containsKey(user.displayId)) {
                call.respond(Result(0, null, sessions.size))
            } else {
                call.respond(Result(0, null, sessions.size + 1))
            }
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
                        it[this.github] = null
                        it[this.photo] = null
                        it[this.qq] = null
                        it[this.weChat] = null
                        it[this.lastSyncFail] = false
                    }
                }
                User(displayId, name, name, "", null, null, null, null, false)
            }
            call.sessions.set(LoginSession(user))
            call.respond(Result(0, null, user))
        }

        webSocket("/csdnchat/{id}") {
            val id = call.parameters["id"]!!
            val userResultRow =
                transaction { Users.select { Users.id eq id }.singleOrNull() } ?: Result.NOT_LOGIN.throwOut()
            val user = userResultRow.toUser()
            if (sessions[id] != null) {
                sessions[id]!!.second.close(CloseReason(CloseReason.Codes.NORMAL, ID_MULTI_ERROR.msg!!))
            }
            log.info("user[${user.name}](${user.displayId}) connected!")
            val sessionPair = user to this
            sessions[id] = sessionPair
            displayIdSessions[user.displayId] = sessionPair
            launch {
                sendToAll(
                    TextFrameAction(
                        TextFrameActionType.ACTION_NEW_CONNECTION,
                        user
                    )
                )
            }
            // 上次有同步失败的,发送强制同步信息
            if (user.lastSyncFailed) {
                sendToSelf(
                    TextFrameAction(TextFrameActionType.ACTION_NEED_SYNC, "")
                )
            }
            var lastHeartBeatUUIDString: String? = null
            val heartBeatJob = launch {
                while (isActive) {
                    if (lastHeartBeatUUIDString != null) {
                        val exp = HeartBeatException.HeartBeatTimeoutException()
                        close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, exp.toString()))
                        break
                    }
                    lastHeartBeatUUIDString = UUID.randomUUID().toString()
                    (this@webSocket as DefaultWebSocketSession).sendRetry(
                        Frame.Text(
                            objectMapper.writeValueAsString(
                                TextFrameAction(
                                    TextFrameActionType.ACTION_HEARTBEAT,
                                    lastHeartBeatUUIDString!!
                                )
                            )
                        )
                    )
                    //30s心跳一次
                    delay(30_1000)
                }
            }
//            //上一次有未收到的消息,这次websocket重连时同步
//            if (user.lastSyncFailedMessageId != -1L) {
//                transaction {
//                    Messages.select { Messages.id greaterEq user.lastSyncFailedMessageId }
//                }
//            }
            try {
                for (frame in incoming) {
                    log.info("new frame is coming:${frame.frameType}")
                    when (frame) {
                        is Frame.Text -> {
                            try {
                                val receivedText = frame.readText()
                                val node = objectMapper.readValue<TextFrameAction>(receivedText)
                                when (node.type) {
                                    TextFrameActionType.ACTION_MESSAGE -> {
                                        val receiveMessage =
                                            newMessageFromJson(objectMapper.convertValue(node.content), objectMapper)
                                        val message = transaction {
                                            val row = Messages.insert {
                                                it[author] = user.displayId
                                                it[content] = receiveMessage.content
                                                it[type] = receiveMessage.type
                                                it[timestamp] = (System.currentTimeMillis() / 1000).toInt()
                                            }
                                            row.resultedValues!!.single().toMessage(user)
                                        }
                                        launch {
                                            sendToAll(
                                                TextFrameAction(
                                                    TextFrameActionType.ACTION_MESSAGE,
                                                    message
                                                )
                                            )
                                        }
                                    }
                                    TextFrameActionType.ACTION_HEARTBEAT -> {
                                        val contentString = objectMapper.convertValue<String>(node.content)
                                        sendToSelf(
                                            TextFrameAction(
                                                TextFrameActionType.ACTION_HEARTBEAT_ACK,
                                                contentString
                                            )
                                        )
                                    }
                                    TextFrameActionType.ACTION_HEARTBEAT_ACK -> {
                                        val contentString = objectMapper.convertValue<String>(node.content)
                                        //心跳回包内容异常,断开连接
                                        if (contentString != lastHeartBeatUUIDString) {
                                            val exp = HeartBeatException.HeartBeatContentMismatchException(
                                                lastHeartBeatUUIDString ?: "null",
                                                contentString
                                            )
                                            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, exp.toString()))
                                        }
                                        lastHeartBeatUUIDString = null
                                    }
                                    TextFrameActionType.ACTION_UPDATE_USER -> TODO("服务端无需处理,客户端不会发送")
                                    TextFrameActionType.ACTION_NEW_CONNECTION -> TODO("服务端无需处理,客户端不会发送")
                                    TextFrameActionType.ACTION_NEW_DISCONNECTION -> TODO("服务端无需处理,客户端不会发送")
                                    TextFrameActionType.ACTION_NEED_SYNC -> TODO("服务端无需处理,客户端不会发送")
                                    TextFrameActionType.ACTION_BINARY_RANGE_MISSING -> TODO("服务端无需处理,客户端不会发送")
                                    TextFrameActionType.ACTION_BINARY_HEAD_MISSING -> TODO("服务端无需处理,客户端不会发送")
                                }
                            } catch (e: Throwable) {
                                log.warn("wrong text format:${e.message}")
                                if (e is TextFrameActionConvertable) {
                                    val action = e.toTextFrameAction()
                                    sendToSelf(action)
                                }
                            }
                        }
                        is Frame.Binary -> {
                            try {
                                val receivedBytes = frame.readBytes()
                                val binaryFrames = BinaryFrames.parse(receivedBytes)
                                RawBinaryMessageBuildHelper.append(binaryFrames)
                                if (binaryFrames is BinaryFrames.BinaryTailFrame) {
                                    val message = transaction {
                                        val msg = RawBinaryMessageBuildHelper.build(binaryFrames.clientId)
                                        val result = msg.save(resources)
                                        val row = Messages.insert {
                                            it[author] = user.displayId
                                            it[clientId] = msg.clientId.value
                                            it[timestamp] = (System.currentTimeMillis() / 1000).toInt()
                                            it[type] = msg.type
                                            it[content] = msg.getContent(result.sha256)
                                        }
                                        row.resultedValues!!.single().toMessage(user)
                                    }
                                    launch {
                                        sendToAll(
                                            TextFrameAction(
                                                TextFrameActionType.ACTION_MESSAGE,
                                                message
                                            )
                                        )
                                    }
                                }
                            } catch (e: Throwable) {
                                log.warn("wrong binary format:${e.message}")
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
                heartBeatJob.cancel()
                launch {
                    sendToOther(
                        TextFrameAction(
                            TextFrameActionType.ACTION_NEW_DISCONNECTION,
                            user
                        )
                    )
                }
                log.info("session[${id}] has removed")
                sessions.remove(id)
                displayIdSessions.remove(user.displayId)
            }
        }
    }
}


