package tem.csdn

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
        const val DIR_NAME = "images"
    }

    private val imagesBasePath = basePath.resolve(DIR_NAME)

    constructor(basePath: String) : this(Path.of(basePath))

    init {
        if (imagesBasePath.notExists()) {
            Files.createDirectories(imagesBasePath)
        }
    }

    fun save(data: InputStream): ResourcesSaveResult {
        return save(data.readAllBytes())
    }

    fun save(data: ByteArray): ResourcesSaveResult {
        val sha256 = data.sha256()
        val path = imagesBasePath.resolve(sha256)
        Files.write(path, data)
        return ResourcesSaveResult(sha256, path)
    }

    fun get(sha256: String): InputStream {
        return imagesBasePath.resolve(sha256).let {
            if (it.notExists()) {
                throw FileNotFoundException(it.toString())
            } else {
                Files.newInputStream(it)
            }
        }
    }

    fun exists(sha256: String): Boolean {
        return imagesBasePath.resolve(sha256).exists()
    }
}