package tem.csdn

import tem.csdn.model.BinaryContent
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

class ResourcesSaveResult(
    val sha256: String,
    val path: Path
) {
    fun <T> `try`(event: Path.(String) -> T): T {
        try {
            return event(path, sha256)
        } catch (e: Throwable) {
            path.deleteIfExists()
            throw e
        }
    }
}

class Resources(basePath: Path) {
    companion object {
        const val DIR_NAME = "res"
    }

    private val binaryFileBasePath = basePath.resolve(DIR_NAME)

    constructor(basePath: String) : this(Path.of(basePath))

    init {
        if (binaryFileBasePath.notExists()) {
            Files.createDirectories(binaryFileBasePath)
        }
    }

    fun save(data: InputStream): ResourcesSaveResult {
        return save(data.readAllBytes())
    }

    fun save(data: ByteArray): ResourcesSaveResult {
        val sha256 = data.sha256()
        val path = binaryFileBasePath.resolve(sha256)
        Files.write(path, data)
        return ResourcesSaveResult(sha256, path)
    }

    fun save(binaryContents: List<BinaryContent>): ResourcesSaveResult {
        val sha256 = binaryContents.sha256()
        val path = binaryFileBasePath.resolve(sha256)
        Files.newOutputStream(path).use {
            binaryContents.forEachContent { bytes: ByteArray, offset: Int, length: Int ->
                it.write(bytes, offset, length)
            }
        }
        return ResourcesSaveResult(sha256, path)
    }

    fun get(sha256: String): InputStream {
        return binaryFileBasePath.resolve(sha256).let {
            if (it.notExists()) {
                throw FileNotFoundException(it.toString())
            } else {
                Files.newInputStream(it)
            }
        }
    }

    fun exists(sha256: String): Boolean {
        return binaryFileBasePath.resolve(sha256).exists()
    }
}