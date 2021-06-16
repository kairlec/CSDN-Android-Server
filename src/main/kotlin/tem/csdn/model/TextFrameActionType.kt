package tem.csdn.model

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import tem.csdn.util.ClientIdDeserializer
import tem.csdn.util.ClientIdSerializer
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

enum class TextFrameActionType(val code: Byte) {
    /**
     * 消息
     * content-format:
     * {Message:Json}
     */
    ACTION_MESSAGE(0),

    /**
     * 更新用户消息
     * content-format:
     * {User:Json}
     */
    ACTION_UPDATE_USER(1),

    /**
     * 新的连接消息
     * content-format:
     * {User:Json}
     */
    ACTION_NEW_CONNECTION(2),

    /**
     * 新的心跳消息
     * content-format:
     * {HeartBeat-UUID}
     */
    ACTION_HEARTBEAT(3),

    /**
     * 新的心跳返回消息
     * content-format:
     * {HeartBeat-UUID}
     */
    ACTION_HEARTBEAT_ACK(4),

    /**
     * 新的断开连接消息
     * content-format:
     * {User:Json}
     */
    ACTION_NEW_DISCONNECTION(5),

    /**
     * 强制同步消息
     * content-format:
     * {String:Empty}
     */
    ACTION_NEED_SYNC(6),

    /**
     * 缺少Binary消息内容
     * content-format:
     * {clientId}[|{from}:{to}]
     */
    ACTION_BINARY_RANGE_MISSING(7),

    /**
     * 缺少Binary消息头内容
     * content-format:
     * {clientId}[|{from}:{to}]
     */
    ACTION_BINARY_HEAD_MISSING(8),
    ;
}

