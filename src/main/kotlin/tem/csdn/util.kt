package tem.csdn

import java.security.MessageDigest
import kotlin.random.Random

fun CharRange.randomString(length: Int) = (1..length).map { randomChar }.joinToString("")

val CharRange.randomChar: Char
    get() = Random.nextInt(first.code, last.code).toChar()

fun CharRange.Companion.randomString(length: Int, vararg ranges: CharRange): String {
    return (1..length).map { ranges[(Random.nextInt(ranges.size))].randomChar }.joinToString("")
}

fun CharRange.Companion.randomString(length: Int, ranges: List<CharRange>): String {
    return (1..length).map { ranges[(Random.nextInt(ranges.size))].randomChar }.joinToString("")
}

fun List<CharRange>.randomChar(length: Int) = CharRange.randomString(length, this)

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

fun ByteArray.sha256() = MessageDigest.getInstance("SHA-256").digest(this).toHexString()
