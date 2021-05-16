package tem.csdn.model


data class Result(
    val code: Int = 0,
    val msg: String? = null,
    val data: Any? = null
) {
    companion object {
        val NOT_LOGIN = Result(1, "not login")
        val ID_MULTI_ERROR = Result(2, "multi id connected")
    }

    fun throwOut(): Nothing {
        throw ResultException(this@Result)
    }
}

class ResultException(val result: Result) : Exception(result.msg)