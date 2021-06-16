package tem.csdn.model

import tem.csdn.convertToClientId
import tem.csdn.convertToLong
import tem.csdn.convertToStringOrNull

/**
 * 从客户端传来的二进制帧,分下列几种
 */
sealed class BinaryFrames(val clientId: ClientId) {
    /**
     * 二进制头帧,这个帧描述了即将要接受的类型,客户端ID与总长度
     */
    class BinaryHeaderFrame(
        clientId: ClientId,
        val type: MessageType,
        val length: Long,
        val extendContent: String?,
    ) : BinaryFrames(clientId) {
        companion object {
            const val FRAME_CODE: Byte = 0
            fun parse(byteArray: ByteArray): BinaryHeaderFrame {
                val clientId = byteArray.convertToClientId(1)
                val type = MessageType.parse(byteArray[37])
                val length = byteArray.convertToLong(38)
                val extendContent = if (byteArray.size > 45) {
                    byteArray.convertToStringOrNull(46)
                } else {
                    null
                }
                return BinaryHeaderFrame(clientId, type, length, extendContent)
            }
        }
    }

    /**
     * 二进制内容帧,这个帧含有主体内容,客户端ID(识别是哪一段的)
     */
    class BinaryContentFrame(
        clientId: ClientId,
        val start: Long,
        val content: ByteArray,
    ) : BinaryFrames(clientId), Comparable<BinaryContentFrame> {
        companion object {
            const val FRAME_CODE: Byte = 1
            fun parse(byteArray: ByteArray): BinaryContentFrame {
                val clientId = byteArray.convertToClientId(1)
                val start = byteArray.convertToLong(37)
                val content = byteArray.copyOfRange(38, byteArray.size)
                return BinaryContentFrame(clientId, start, content)
            }
        }

        override fun compareTo(other: BinaryContentFrame): Int {
            return this.start.compareTo(other.start)
        }
    }

    /**
     * 二进制尾帧,这个帧描述当前传送已结束,需要立即构建消息
     */
    class BinaryTailFrame(
        clientId: ClientId
    ) : BinaryFrames(clientId) {
        companion object {
            const val FRAME_CODE: Byte = 2
            fun parse(byteArray: ByteArray): BinaryTailFrame {
                val clientId = byteArray.convertToClientId(1)
                return BinaryTailFrame(clientId)
            }
        }
    }

    companion object {
        fun parse(byteArray: ByteArray): BinaryFrames {
            return when (byteArray[0]) {
                BinaryHeaderFrame.FRAME_CODE -> BinaryHeaderFrame.parse(byteArray)
                BinaryContentFrame.FRAME_CODE -> BinaryContentFrame.parse(byteArray)
                BinaryTailFrame.FRAME_CODE -> BinaryTailFrame.parse(byteArray)
                else -> throw IllegalArgumentException("unknown binary frame code")
            }
        }
    }
}