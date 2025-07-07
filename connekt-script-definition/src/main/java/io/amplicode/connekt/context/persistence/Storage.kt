package io.amplicode.connekt.context.persistence

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.amplicode.connekt.context.defaultObjectMapper
import java.io.File
import kotlin.reflect.KClass

class Storage(
    private val file: File,
    val objectMapper: ObjectMapper = defaultObjectMapper
) {

    val data: NodeMap = initStorage()

    private fun initStorage(): NodeMap {
        return if (file.exists()) {
            readFileSafe()
        } else {
            file.parentFile.mkdirs()
            file.createNewFile()
            mutableMapOf()
        }

    }

    private val changedData: NodeMap = mutableMapOf()

    inline fun <reified T : Any> getValue(key: String): T? {
        return getValue(key, T::class)
    }

    fun <T : Any> getValue(key: String, klass: KClass<T>): T? {
        val jsonNode = data[key] ?: return null
        return objectMapper.convertValue<T?>(jsonNode, klass.java)
    }

    fun setValue(key: String, value: Any?) {
        val jsonNode = objectMapper.valueToTree<JsonNode>(value)
        data[key] = jsonNode
        changedData[key] = jsonNode
    }

    fun close() {
        if (changedData.isNotEmpty()) {
            val existingData: NodeMap = readFileSafe()
            existingData.putAll(changedData)
            objectMapper.writeValue(file, existingData)
        }
    }

    private fun readFileSafe(): NodeMap = try {
        objectMapper.readValue(file)
    } catch (_: Exception) {
        mutableMapOf()
    }
}

typealias NodeMap = MutableMap<String, JsonNode>