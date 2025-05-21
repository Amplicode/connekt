package io.amplicode.connekt.context

import java.io.Closeable
import java.nio.file.Files
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

/**
 * A file-based implementation of PersistenceStore.
 * This uses a simple serialization mechanism to store objects in files.
 */
class FilePersistenceStore(private val directory: Path) : PersistenceStore {
    private val maps: MutableMap<String, MutableMap<String, Any?>> = mutableMapOf()

    init {
        // Create the directory if it doesn't exist
        if (!Files.exists(directory)) {
            Files.createDirectories(directory)
        }
    }

    override fun getMap(name: String): MutableMap<String, Any?> {
        return maps.getOrPut(name) {
            // Load the map from a file if it exists
            val file = directory.resolve("$name.dat")
            if (Files.exists(file)) {
                try {
                    val inputStream = java.io.ObjectInputStream(Files.newInputStream(file))
                    @Suppress("UNCHECKED_CAST")
                    val map = inputStream.readObject() as MutableMap<String, Any?>
                    inputStream.close()
                    map
                } catch (_: Exception) {
                    // If there's an error loading the file, start with an empty map
                    mutableMapOf()
                }
            } else {
                mutableMapOf()
            }
        }
    }

    override fun close() {
        // Save all maps to files
        for ((name, map) in maps) {
            val file = directory.resolve("$name.dat")
            val outputStream = java.io.ObjectOutputStream(Files.newOutputStream(file))
            outputStream.writeObject(map)
            outputStream.close()
        }
    }
}

/**
 * Creates a PersistenceStore based on the given type.
 *
 * @param type The type of persistence store to create
 * @param directory The directory to use for file-based persistence (only used if type is FILE)
 * @return A new PersistenceStore instance
 */
fun createPersistenceStore(type: PersistenceStoreType, directory: Path? = null): PersistenceStore {
    return when (type) {
        PersistenceStoreType.MEMORY -> InMemoryPersistenceStore()
        PersistenceStoreType.FILE -> {
            if (directory == null) {
                throw IllegalArgumentException("Directory must be provided for FILE persistence store")
            }
            FilePersistenceStore(directory)
        }
    }
}

/**
 * Enum for the type of persistence store to create.
 */
enum class PersistenceStoreType {
    MEMORY,
    FILE
}