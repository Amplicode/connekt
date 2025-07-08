package io.amplicode.connekt.context.persistence

import kotlin.reflect.KType

/**
 * A simple in-memory implementation of PersistenceStore.
 * This is useful for testing or when persistence is not required.
 */
class InMemoryStorage : Storage {
    private val map = mutableMapOf<String, Any?>()

    override fun <T : Any> getValue(key: String, type: KType): T? {
        val value = map[key]
        return value as? T?
    }

    override fun setValue(key: String, value: Any?) {
        map[key] = value
    }

    override fun close() {
        // No resources to release
    }
}
