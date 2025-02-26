package io.amplicode.connekt

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KType

class Storage(
    private val file: File,
    private val objectMapper: ObjectMapper
) {

    private val data: MutableMap<String, JsonNode> = if (file.exists()) {
        objectMapper.readValue(file)
    } else {
        mutableMapOf()
    }
    private val changedData: MutableMap<String, JsonNode> = mutableMapOf()


    private fun KType.toJavaType(): JavaType {
        val typeFactory = objectMapper.typeFactory

        val classifier = this.classifier as? KClass<*>
            ?: throw IllegalArgumentException("Unsupported KType: $this")

        if (this.arguments.isNotEmpty()) {
            val parameterizedType = this.arguments.first().type
            val java: Class<out Any> = classifier.java
            val javaType: JavaType = typeFactory.constructParametricType(
                java,
                parameterizedType?.toJavaType() ?: typeFactory.constructType(Any::class.java)
            )
            return javaType
        }

        if (this.classifier == Array::class) {
            val componentType =
                this.arguments.firstOrNull()?.type?.toJavaType() ?: typeFactory.constructType(Any::class.java)
            return typeFactory.constructArrayType(componentType)
        }

        return objectMapper.constructType(classifier.java)
    }

    fun <T : Any> getValue(type: KType, key: String): T? {
        val jsonNode = data[key] ?: return null

        val javaType = type.toJavaType()
        return objectMapper.convertValue(jsonNode, objectMapper.constructType(javaType)) as? T
    }

    fun setValue(key: String, value: Any) {
        val jsonNode = objectMapper.valueToTree<JsonNode>(value)
        data[key] = jsonNode
        changedData[key] = jsonNode
    }

    fun close() {
        if (changedData.isNotEmpty()) {
            val existingData: MutableMap<String, JsonNode> =
                if (file.exists()) objectMapper.readValue(file) else mutableMapOf()
            existingData.putAll(changedData)
            objectMapper.writeValue(file, existingData)
        }
    }
}
