package tem.csdn.model

class NoParameterException(val parameterName: String, override val message: String? = "no parameter:${parameterName}") :
    Exception(message)
