package tem.csdn.model

import tem.csdn.intf.TextFrameActionConvertable
import java.lang.Exception
import java.util.concurrent.TimeUnit

/**
 * 原生二进制消息构造异常
 */
sealed class RawBinaryMessageBuildException(
    override val message: String? = "build failed",
    override val cause: Throwable? = null
) : Exception(message, cause), TextFrameActionConvertable {
    class MissingHeaderFrameExceptionRaw(
        val clientId: ClientId,
        override val message: String = "build failed because miss header frame on clientId:${clientId.value}"
    ) : RawBinaryMessageBuildException(message) {
        override fun toTextFrameAction(): TextFrameAction {
            return TextFrameAction(TextFrameActionType.ACTION_BINARY_HEAD_MISSING, clientId.value)
        }
    }

//    class MissingTailFrameExceptionRaw(
//        val clientId: ClientId,
//        override val message: String = "build failed because miss tail frame on clientId:${clientId.value}"
//    ) : RawBinaryMessageBuildException(message)

    class MissingContentFrameExceptionRaw(
        val clientId: ClientId,
        val offset: Long,
        val length: Long,
        override val message: String = "build failed because miss content frame from $offset to ${length + offset} on clientId:${clientId.value}"
    ) : RawBinaryMessageBuildException(message) {
        override fun toTextFrameAction(): TextFrameAction {
            return TextFrameAction(TextFrameActionType.ACTION_BINARY_RANGE_MISSING, "${clientId.value}|")
        }
    }

//    class WrongClientIdExceptionRaw(
//        val clientId: ClientId,
//        override val message: String = "build failed because wrong clientId:${clientId.value}"
//    ) : RawBinaryMessageBuildException(message)
}

/**
 * 原生二进制消息构造辅助器
 */
object RawBinaryMessageBuildHelper {
    /**
     * 原生二进制消息构造器
     */
    class RawBinaryMessageBuilder(
        val clientId: ClientId
    ) {
        /**
         * 头帧
         */
        private var headerFrame: BinaryFrames.BinaryHeaderFrame? = null

        /**
         * 内容帧
         */
        private val contentFrames = mutableListOf<BinaryFrames.BinaryContentFrame>()

        /**
         * 尾帧
         */
        private var tailFrame: BinaryFrames.BinaryTailFrame? = null

        /**
         * 最后修改时间
         */
        internal var lastModifierTime = System.currentTimeMillis()

        /**
         * 追加新帧
         */
        fun append(frames: BinaryFrames) {
            when (frames) {
                is BinaryFrames.BinaryContentFrame -> contentFrames.add(frames)
                is BinaryFrames.BinaryHeaderFrame -> headerFrame = frames
                is BinaryFrames.BinaryTailFrame -> tailFrame = frames
            }
            lastModifierTime = System.currentTimeMillis()
        }

        /**
         * 构建消息体
         */
        fun build(): RawBinaryMessage {
            if (headerFrame == null) {
                throw RawBinaryMessageBuildException.MissingHeaderFrameExceptionRaw(clientId)
            }
            // 由代码逻辑保证了ClientId的正确,无需判断
//            if (headerFrame!!.clientId != clientId) {
//                throw RawBinaryMessageBuildException.WrongClientIdExceptionRaw(clientId)
//            }
            // 尾帧不是必须的,尾帧只是为了提醒服务端我的内容已经传输完毕,如果缺失尾帧,主体内容在的话也可以构建完成
//            if (tailFrame == null) {
//                throw RawBinaryMessageBuildException.MissingTailFrameExceptionRaw(clientId)
//            }
//            if (tailFrame!!.clientId != clientId) {
//                throw RawBinaryMessageBuildException.WrongClientIdExceptionRaw(clientId)
//            }
            contentFrames.sort()
            var lastFill = 0L
            contentFrames.forEach {
//                if (it.clientId != clientId) {
//                    throw RawBinaryMessageBuildException.WrongClientIdExceptionRaw(clientId)
//                }
                if (lastFill < it.start) {
                    throw RawBinaryMessageBuildException.MissingContentFrameExceptionRaw(
                        clientId,
                        lastFill,
                        it.start - lastFill
                    )
                }
                lastFill += it.content.size
            }
            if (lastFill < headerFrame!!.length) {
                throw RawBinaryMessageBuildException.MissingContentFrameExceptionRaw(
                    clientId,
                    lastFill,
                    headerFrame!!.length - lastFill
                )
            }
            return RawBinaryMessage(
                clientId,
                contentFrames.map { BinaryContent(it.start, it.content) },
                headerFrame!!.type,
                headerFrame!!.extendContent
            )
        }
    }

    /**
     * 消息池
     */
    private val clientIdMap = mutableMapOf<ClientId, RawBinaryMessageBuilder>()

    /**
     * 追加消息
     */
    fun append(binaryFrame: BinaryFrames) {
        val builder = clientIdMap.getOrPut(binaryFrame.clientId) { RawBinaryMessageBuilder(binaryFrame.clientId) }
        builder.append(binaryFrame)
    }

    /**
     * 获取构建超时,指定单位时间内,若上次修改时间与现在的时间差超过了指定的单位时间,则返回该构建器
     */
    fun getTimeout(duration: Long, unit: TimeUnit): Map<ClientId, RawBinaryMessageBuilder> {
        return clientIdMap.filterValues { System.currentTimeMillis() - it.lastModifierTime > unit.toMillis(duration) }
    }

    /**
     * 构建
     */
    fun build(clientId: ClientId): RawBinaryMessage {
        return clientIdMap[clientId]!!.build()
    }

    /**
     * 所有的clientId
     */
    val clientIds: MutableSet<ClientId>
        get() = clientIdMap.keys

    /**
     * 尝试构建
     */
    fun tryBuild(clientId: ClientId): RawBinaryMessage? {
        return try {
            clientIdMap[clientId]?.build()
        } catch (e: Throwable) {
            null
        }
    }
}