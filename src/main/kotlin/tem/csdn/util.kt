package tem.csdn

import tem.csdn.model.BinaryContent
import tem.csdn.model.ClientId
import java.lang.IllegalArgumentException
import java.nio.ByteBuffer
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

inline fun List<BinaryContent>.forEachContent(crossinline event: (ByteArray, Int, Int) -> Unit) {
    val sortedList = this.sortedBy { it.start }
    for (index in sortedList.indices) {
        val next = sortedList.getOrNull(index + 1)
        val current = sortedList[index]
        if (next != null) {
            if (current.start + current.content.size > next.start) {
                event(current.content, 0, (next.start - current.start).toInt())
            } else {
                event(current.content, 0, current.content.size)
            }
        } else {
            event(current.content, 0, current.content.size)
        }
    }
}

fun List<BinaryContent>.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    forEachContent { byteArray, offset, length ->
        digest.update(byteArray, offset, length)
    }
    return digest.digest().toHexString()
}

fun ByteArray.convertToClientId(offset: Int = 0, length: Int = 36): ClientId {
    return ClientId(convertToString(offset, length))
}

fun ByteArray.convertToString(offset: Int = 0, length: Int = -1): String {
    val realLength = if (length == -1) {
        this.size - offset
    } else {
        length
    }
    if (realLength > this.size) {
        throw IllegalArgumentException("length is more than size")
    }
    return buildString(realLength) {
        repeat(realLength) {
            append(this@convertToString[it + offset].toInt().toChar())
        }
    }
}

fun ByteArray.convertToStringOrNull(offset: Int = 0, length: Int = -1): String? {
    return try {
        convertToString(offset, length)
    } catch (e: IllegalArgumentException) {
        null
    }
}

fun ClientId.convertToByteArray(): ByteArray {
    return value.toByteArray()
}

fun Long.convertToByteArray() = ByteBuffer.allocate(Long.SIZE_BYTES).apply {
    putLong(this@convertToByteArray)
}.array()

fun ByteArray.convertToLong(offset: Int = 0, length: Int = Long.SIZE_BYTES) =
    ByteBuffer.allocate(Long.SIZE_BYTES).apply {
        put(this@convertToLong, offset, length)
        flip()
    }.long