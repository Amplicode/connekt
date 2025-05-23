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

    operator fun contains(property: KProperty<*>): Boolean
}

class DelegateEnvironmentStore(
    private val stores: List<EnvironmentStore>
) : EnvironmentStore {
    override fun <T> getValue(receiver: Any?, property: KProperty<*>): T {
        return stores.firstOrNull { it.contains(property) }
            ?.getValue<T>(receiver, property)
            ?: throw NoEnvironmentPropertyException(property.name)
    }

    override fun contains(property: KProperty<*>): Boolean {
        return stores.any { it.contains(property) }
    }
}

class ValuesEnvironmentStore(
    private val values: Map<String, String>
) : EnvironmentStore {
    @Suppress("UNCHECKED_CAST")
    override fun <T> getValue(receiver: Any?, property: KProperty<*>): T {
        if (property.name !in values) {
            throw NoEnvironmentPropertyException(property.name)
        }

        val value = values[property.name]!!

        return when {
            property.returnType.isSubtypeOf(typeOf<String>()) -> value as T
            property.returnType.isSubtypeOf(typeOf<Int>()) -> value.toInt() as T
            property.returnType.isSubtypeOf(typeOf<Boolean>()) -> value.toBoolean() as T
            property.returnType.isSubtypeOf(typeOf<Double>()) -> value.toDouble() as T
            property.returnType.isSubtypeOf(typeOf<Long>()) -> value.toLong() as T
            else -> throw IllegalArgumentException("Unsupported type for property ${property.name}")
        }
    }

    override fun contains(property: KProperty<*>): Boolean {
        return property.name in values
    }
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

    @Suppress("UNCHECKED_CAST")
    override operator fun <T> getValue(receiver: Any?, property: KProperty<*>): T {
        val node = values.get(property.name)
            ?: throw NoEnvironmentPropertyException(property.name)

        return when {
            property.returnType.isSubtypeOf(typeOf<String>()) -> node.asText() as T
            property.returnType.isSubtypeOf(typeOf<Int>()) -> node.asInt() as T
            property.returnType.isSubtypeOf(typeOf<Boolean>()) -> node.asBoolean() as T
            property.returnType.isSubtypeOf(typeOf<Double>()) -> node.asDouble() as T
            property.returnType.isSubtypeOf(typeOf<Long>()) -> node.asLong() as T
            else -> throw IllegalArgumentException("Unsupported type for property ${property.name}")
        }
    }

    override fun contains(property: KProperty<*>): Boolean {
        return values.get(property.name) != null
    }
}

object NoopEnvironmentStore : EnvironmentStore {
    override fun <T> getValue(receiver: Any?, property: KProperty<*>): T =
        throw NoEnvironmentPropertyException(property.name)

    override fun contains(property: KProperty<*>): Boolean = false
}

class NoEnvironmentPropertyException(prop: String) :
    Exception("Unable to find property '$prop'")
