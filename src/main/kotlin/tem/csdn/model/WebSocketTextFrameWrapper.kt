package tem.csdn.model

data class WebSocketFrameWrapper(
    val type: FrameType,
    val content: Any?
) {
    enum class FrameType {
        // 消息体
        MESSAGE,
        // 新的连接消息
        NEW_CONNECTION,
        // 新的断开消息
        NEW_DISCONNECTION,
        // 心跳包
        HEARTBEAT,
        // 心跳返回包
        HEARTBEAT_ACK,
        // 需要客户端重新同步
        NEED_SYNC,
        ;
    }
}