package tem.csdn.model

class NoParameterException(val parameterName: String, override val message: String? = "no parameter:${parameterName}") :
    Exception(message)

sealed class HeartBeatException(override val message: String? = null, override val cause: Throwable? = null) :
    Exception(message, cause) {

    class HeartBeatTimeoutException(
        override val message: String = "HeartBeat timeout",
        override val cause: Throwable? = null
    ) :
        HeartBeatException(message, cause)

    class HeartBeatContentMismatchException(
        sendContent: String,
        receiveContent: String,
        override val message: String = "HeartBeat last content is '$sendContent' but receive $receiveContent",
        override val cause: Throwable? = null
    ) :
        HeartBeatException(message, cause)
}