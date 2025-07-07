package io.amplicode.connekt.context.persistence

import java.io.Closeable
import java.nio.file.Path
import kotlin.reflect.KClass

/**
 * Interface for a persistence store that can store and retrieve objects.
 * This is used to abstract the storage mechanism from the rest of the code.
 */
interface Storage : Closeable {

    fun <T : Any> getValue(key: String, klass: KClass<T>): T?

    fun setValue(key: String, value: Any?)

    /**
     * Closes the persistence store, releasing any resources.
     */
    override fun close()
}

fun defaultStorage(directory: Path): Storage =
    JsonStorage(directory.resolve("variables.json"))


inline fun <reified T : Any> Storage.getValue(key: String): T? {
    return getValue(key, T::class)
}