package tem.csdn

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.notExists

class Resources(private val basePath: Path) {
    constructor(basePath: String) : this(Path.of(basePath))

    init {
        if (basePath.notExists()) {
            Files.createDirectories(basePath)
        }
        ResourcesType.values().forEach {
            basePath.resolve(it.dirname).run {
                if (this.notExists()) {
                    Files.createDirectories(this)
                }
            }
        }
    }

    enum class ResourcesType(val dirname: String) {
        PHOTO("photo"),
        IMAGE("image")
        ;
    }

    private fun Path.resolve(type: ResourcesType, id: String): Path {
        return this.resolve(type.dirname).resolve(id)
    }

    private fun Path.resolve(type: ResourcesType, id: Long): Path {
        return this.resolve(type.dirname).resolve(id.toString())
    }

    fun save(type: ResourcesType, id: String, data: InputStream): Long {
        return data.transferTo(Files.newOutputStream(basePath.resolve(type, id)))
    }

    fun save(type: ResourcesType, id: Long, data: InputStream): Long {
        return data.transferTo(Files.newOutputStream(basePath.resolve(type, id)))
    }

    fun save(type: ResourcesType, id: String, data: ByteArray) {
        Files.write(basePath.resolve(type, id), data)
    }

    fun save(type: ResourcesType, id: Long, data: ByteArray) {
        Files.write(basePath.resolve(type, id), data)
    }

    fun get(type: ResourcesType, id: String): InputStream {
        return Files.newInputStream(basePath.resolve(type, id))
    }

    fun get(type: ResourcesType, id: Long): InputStream {
        return Files.newInputStream(basePath.resolve(type, id))
    }
}