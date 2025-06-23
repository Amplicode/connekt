package io.amplicode.connekt.context.persistence

import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.collections.iterator

/**
 * A file-based implementation of PersistenceStore.
 * This uses a simple serialization mechanism to store objects in files.
 */
class JavaSerializationFilePersistenceStore(private val directory: Path) : PersistenceStore {
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
                    val inputStream = ObjectInputStream(Files.newInputStream(file))
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
            val outputStream = ObjectOutputStream(Files.newOutputStream(file))
            outputStream.writeObject(map)
            outputStream.close()
        }
    }
}