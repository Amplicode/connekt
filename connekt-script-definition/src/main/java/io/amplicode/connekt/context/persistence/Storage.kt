package io.amplicode.connekt.context.persistence

import java.io.Closeable
import java.nio.file.Path
import kotlin.reflect.KType

/**
 * Abstraction over a key-value persistence store that can save and restore objects across
 * script invocations.
 *
 * `Storage` decouples the variable-persistence mechanism from the rest of the framework. The
 * default implementation serializes values to a JSON file in the script's working directory (see
 * [defaultStorage]), but alternative implementations can be provided for testing or custom
 * storage backends.
 *
 * Implementations must be [Closeable]: the framework calls [close] when the script finishes
 * execution so that any buffered data is flushed and file handles are released.
 */
interface Storage : Closeable {

    /**
     * Retrieves the value stored under [key] and deserializes it to type [T].
     *
     * @param T The expected return type. The implementation uses [type] to guide deserialization.
     * @param key The string key that identifies the stored entry.
     * @param type The [KType] of [T], used to deserialize the raw stored value into the correct type.
     * @return The deserialized value, or `null` if no entry exists for [key].
     */
    fun <T : Any> getValue(key: String, type: KType): T?

    /**
     * Stores [value] under [key], replacing any previously stored entry.
     *
     * Passing `null` as [value] clears the entry associated with [key].
     *
     * @param key The string key under which the value is persisted.
     * @param value The value to store, or `null` to remove the entry.
     */
    fun setValue(key: String, value: Any?)

    /**
     * Closes the persistence store, releasing any resources.
     */
    override fun close()
}

fun defaultStorage(directory: Path): Storage =
    JsonStorage(directory.resolve("variables.json"))
