package io.amplicode.connekt.context.persistence

import java.io.Closeable
import java.nio.file.Path

/**
 * Interface for a persistence store that can store and retrieve objects.
 * This is used to abstract the storage mechanism from the rest of the code.
 */
interface PersistenceStore : Closeable {
    /**
     * Creates or gets a map with the given name.
     *
     * @param name The name of the map
     * @return A mutable map that can be used to store and retrieve objects
     */
    fun getMap(name: String): MutableMap<String, Any?>

    /**
     * Closes the persistence store, releasing any resources.
     */
    override fun close()
}

fun defaultPersistenceStore(directory: Path) =
    JsonFilePersistenceStore(directory)
