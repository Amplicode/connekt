/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt.context


import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import kotlin.reflect.KProperty
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf

interface EnvironmentStore {
    operator fun <T> getValue(receiver: Any?, property: KProperty<*>): T
}

class FileEnvironmentStore(
    private val file: File,
    private val envName: String
) : EnvironmentStore {
    private val values: JsonNode by lazy {
        val objectMapper = ObjectMapper()
        val tree = objectMapper.readTree(file)
        val envNode = tree.get(envName) ?: TODO()
        envNode
    }

    override operator fun <T> getValue(receiver: Any?, property: KProperty<*>): T {
        val node = values.get(property.name)
            ?: throw NoEnvironmentPropertyException(envName, property.name)

        return when {
            property.returnType.isSubtypeOf(typeOf<String>()) -> node.asText() as T
            property.returnType.isSubtypeOf(typeOf<Int>()) -> node.asInt() as T
            property.returnType.isSubtypeOf(typeOf<Boolean>()) -> node.asBoolean() as T
            property.returnType.isSubtypeOf(typeOf<Double>()) -> node.asDouble() as T
            property.returnType.isSubtypeOf(typeOf<Long>()) -> node.asLong() as T
            else -> throw IllegalArgumentException("Unsupported type for property ${property.name}")
        }
    }
}

object NoopEnvironmentStore : EnvironmentStore {
    override fun <T> getValue(receiver: Any?, property: KProperty<*>): T =
        throw NoEnvironmentException(property.name)
}

class NoEnvironmentException(prop: String) :
    Exception("Please provide environment to access property '$prop'")

class NoEnvironmentPropertyException(env: String, prop: String) :
    Exception("Please add property '$prop' into environment '$env'")