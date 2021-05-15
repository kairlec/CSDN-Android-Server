package tem.csdn

import kotlin.random.Random

fun CharRange.randomString(length: Int) = (1..length).map { randomChar }.joinToString("")

val CharRange.randomChar: Char
    get() = Random.nextInt(first.code, last.code).toChar()

fun CharRange.Companion.randomString(length: Int, vararg ranges: CharRange): String {
    return (1..length).map { ranges[(Random.nextInt(ranges.size))].randomChar }.joinToString("")
}

fun String.Companion.byteArrayAsHex(byteArray: ByteArray): String {
    return StringBuilder(byteArray.size * 2).let { builder ->
        byteArray.forEach { builder.append("%02X".format(it)) }
        builder.toString()
    }
}
