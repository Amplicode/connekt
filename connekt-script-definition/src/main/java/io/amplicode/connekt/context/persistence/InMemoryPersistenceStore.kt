package io.amplicode.connekt.context.persistence

/**
 * A simple in-memory implementation of PersistenceStore.
 * This is useful for testing or when persistence is not required.
 */
class InMemoryPersistenceStore : PersistenceStore {
    private val maps: MutableMap<String, MutableMap<String, Any?>> = mutableMapOf()

    override fun getMap(name: String): MutableMap<String, Any?> {
        return maps.getOrPut(name) { mutableMapOf() }
    }

    override fun close() {
        // No resources to release
    }
}