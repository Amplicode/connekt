package io.amplicode.connekt.context.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.module.kotlin.kotlinModule
import java.nio.file.Files
import java.nio.file.Path
import kotlin.collections.iterator

/**
 * A JSON-based implementation of PersistenceStore.
 * This uses Jackson to serialize objects to JSON format.
 */
class JsonFilePersistenceStore(private val directory: Path) : PersistenceStore {
    private val maps: MutableMap<String, MutableMap<String, Any?>> = mutableMapOf()
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModules(kotlinModule())
        // Enable default typing to include class information in JSON
        .activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Any::class.java)
                .build(),
            ObjectMapper.DefaultTyping.NON_FINAL
        )
        .enable(SerializationFeature.INDENT_OUTPUT)

    init {
        // Create the directory if it doesn't exist
        if (!Files.exists(directory)) {
            Files.createDirectories(directory)
        }
    }

    override fun getMap(name: String): MutableMap<String, Any?> {
        return maps.getOrPut(name) {
            // Load the map from a file if it exists
            val file = directory.resolve("$name.json")
            if (Files.exists(file)) {
                try {
                    val inputStream = Files.newInputStream(file)
                    @Suppress("UNCHECKED_CAST")
                    val map = objectMapper.readValue(inputStream, Map::class.java) as MutableMap<String, Any?>
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
            val file = directory.resolve("$name.json")
            val outputStream = Files.newOutputStream(file)
            objectMapper.writeValue(outputStream, map)
            outputStream.close()
        }
    }
}