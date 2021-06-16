package tem.csdn.model

import java.lang.IllegalArgumentException

enum class MessageType(val code: Byte) {
    /**
     * 普通文本消息
     * content-format:
     * {unicode}
     */
    TEXT_PLAIN(0),

    /**
     * 图片消息
     * content-format:
     * {sha256}
     */
    IMAGE(1),

    /**
     * 地理位置消息
     * content-format:
     * {latitude(经度)}|{longitude(纬度)}|{地名}
     */
    LOCATION(2),

    /**
     * 文件消息
     * content-format:
     * {sha256}|{filename}
     */
    FILE(3),

    /**
     * 语音消息
     * content-format:
     * {sha256}
     */
    VOICE(4),

    /**
     * 视频消息
     * content-format:
     * {sha256}
     */
    VIDEO(5),
    ;

    companion object {
        fun parse(code: Byte): MessageType {
            values().forEach {
                if (it.code == code) {
                    return it
                }
            }
            throw IllegalArgumentException("unknown type code:${code}")
        }

        fun parse(name: String): MessageType {
            values().forEach {
                if (it.name.equals(name, true)) {
                    return it
                }
            }
            throw IllegalArgumentException("unknown type name:${name}")
        }
    }
}