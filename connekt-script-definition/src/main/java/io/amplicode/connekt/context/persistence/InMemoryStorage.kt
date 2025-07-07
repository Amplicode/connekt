package io.amplicode.connekt.context.persistence

import kotlin.reflect.KClass
import kotlin.reflect.safeCast

/**
 * A simple in-memory implementation of PersistenceStore.
 * This is useful for testing or when persistence is not required.
 */
class InMemoryStorage : Storage {
    private val map = mutableMapOf<String, Any?>()

    override fun <T : Any> getValue(key: String, klass: KClass<T>): T? {
        val value = map[key]
        return klass.safeCast(value)
    }

    override fun setValue(key: String, value: Any?) {
        map[key] = value
    }

    override fun close() {
        // No resources to release
    }
}