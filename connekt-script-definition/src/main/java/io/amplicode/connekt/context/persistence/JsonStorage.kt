package io.amplicode.connekt.context.persistence

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.amplicode.connekt.context.defaultObjectMapper
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.notExists
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

class JsonStorage(
    private val file: Path,
    val objectMapper: ObjectMapper = defaultObjectMapper,
) : Storage {

    val data: NodeMap = initStorage()

    private fun initStorage(): NodeMap {
        if (file.notExists()) {
            file.parent.createDirectories()
            file.createFile()
        }
        return readFileSafe()
    }

    private val changedData: NodeMap = mutableMapOf()

    override fun <T : Any> getValue(key: String, type: KType): T? {
        val jsonNode = data[key] ?: return null
        val javaType = objectMapper.constructType(type.javaType)
        return try {
            objectMapper.convertValue(jsonNode, javaType) as? T
        } catch (e: Exception) {
            println("Unable to parse $key")
            null
        }
    }

    override fun setValue(key: String, value: Any?) {
        val jsonNode = objectMapper.valueToTree<JsonNode>(value)
        data[key] = jsonNode
        changedData[key] = jsonNode
    }

    override fun close() {
        if (changedData.isNotEmpty()) {
            val existingData: NodeMap = readFileSafe()
            existingData.putAll(changedData)
            objectMapper.writeValue(file.toFile(), existingData)
        }
    }

    private fun readFileSafe(): NodeMap = try {
        objectMapper.readValue(file.toFile())
    } catch (_: Exception) {
        mutableMapOf()
    }
}

typealias NodeMap = MutableMap<String, JsonNode>
