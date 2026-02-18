package io.amplicode.connekt.dsl

import com.jayway.jsonpath.ReadContext
import com.jayway.jsonpath.TypeRef
import okhttp3.Response
import org.intellij.lang.annotations.Language

/**
 * Reads a string value from the JSON document at the given JSONPath expression.
 *
 * ```kotlin
 * val name = ctx.readString("$.name")
 * ```
 *
 * @receiver The [ReadContext] representing the parsed JSON document.
 * @param path A JSONPath expression that points to the target string value.
 * @return The string value at [path].
 */
@Deprecated(
    message = "Use decode() instead.",
    replaceWith = ReplaceWith("decode<String>(path)", "io.amplicode.connekt.Connekt")
)
fun ReadContext.readString(@Language("JSONPath") path: String): String {
    return read(path)
}

/**
 * Reads and deserializes the value at [path] into type [T] using full generic-type information.
 *
 * Unlike a plain `read(path)` call, this function preserves generic type parameters (e.g.
 * `List<Pet>`) through a [TypeRef], allowing the JSONPath library to perform accurate
 * deserialization for parameterized types.
 *
 * ```kotlin
 * val pets = ctx.doRead<List<Pet>>("$.pets")
 * ```
 *
 * @receiver The [ReadContext] representing the parsed JSON document.
 * @param path A JSONPath expression that points to the target value.
 * @return The deserialized value of type [T] found at [path].
 */
@Deprecated(
    message = "Use decode() instead.",
    replaceWith = ReplaceWith("decode<T>(path)", "io.amplicode.connekt.Connekt")
)
inline fun <reified T> ReadContext.doRead(@Language("JSONPath") path: String): T {
    return read(path, object : TypeRef<T>() {})
}

inline fun <reified T> ReadContext.decode(@Language("JSONPath") path: String): T {
    return read(path, object : TypeRef<T>() {})
}

/**
 * Reads an integer value from the JSON document at the given JSONPath expression.
 *
 * ```kotlin
 * val count = ctx.readInt("$.totalCount")
 * ```
 *
 * @receiver The [ReadContext] representing the parsed JSON document.
 * @param path A JSONPath expression that points to the target integer value.
 * @return The integer value at [path].
 */
@Deprecated(
    message = "Use decode() instead.",
    replaceWith = ReplaceWith("decode<Int>(path)", "io.amplicode.connekt.Connekt")
)
fun ReadContext.readInt(@Language("JSONPath") path: String): Int {
    return read(path)
}

/**
 * Reads a long value from the JSON document at the given JSONPath expression.
 *
 * ```kotlin
 * val id = ctx.readLong("$.id")
 * ```
 *
 * @receiver The [ReadContext] representing the parsed JSON document.
 * @param path A JSONPath expression that points to the target long value.
 * @return The long value at [path].
 */
@Deprecated(
    message = "Use decode() instead.",
    replaceWith = ReplaceWith("decode<Long>(path)", "io.amplicode.connekt.Connekt")
)
fun ReadContext.readLong(@Language("JSONPath") path: String): Long {
    return read(path)
}

/**
 * Reads a boolean value from the JSON document at the given JSONPath expression.
 *
 * ```kotlin
 * val active = ctx.readBoolean("$.active")
 * ```
 *
 * @receiver The [ReadContext] representing the parsed JSON document.
 * @param path A JSONPath expression that points to the target boolean value.
 * @return The boolean value at [path].
 */
@Deprecated(
    message = "Use decode() instead.",
    replaceWith = ReplaceWith("decode<Boolean>(path)", "io.amplicode.connekt.Connekt")
)
fun ReadContext.readBoolean(@Language("JSONPath") path: String): Boolean {
    return read(path)
}

/**
 * Provides JSONPath parsing and reading capabilities within the Connekt DSL.
 *
 * This interface is mixed into both [ConnektBuilder] and [UseCaseBuilder], making its members
 * available in script-level configuration blocks as well as inside `useCase {}` lambdas.
 * Implementations handle the wiring between OkHttp [Response] objects and the JSONPath library.
 */
interface JsonPathExtensionsProvider {

    /**
     * Parses the response body as a JSON document and returns a [ReadContext] for JSONPath queries.
     *
     * Call this on a [Response] to obtain a [ReadContext] that can be used with the
     * `readString`, `readInt`, `readLong`, `readBoolean`, `readList`, or `doRead` helpers to
     * extract values from the body.
     *
     * ```kotlin
     * val ctx = response.jsonPath()
     * val name = ctx.readString("$.name")
     * ```
     *
     * @receiver The [Response] whose body should be parsed.
     * @return A [ReadContext] over the parsed JSON body.
     */
    fun Response.jsonPath(): ReadContext

    /**
     * Reads a JSON array at [path] and deserializes it as a typed [List].
     *
     * Uses the JSONPath library's typed deserialization to map each element of the JSON array
     * found at [path] to an instance of [clazz].
     *
     * ```kotlin
     * val pets = ctx.readList("$.pets", Pet::class.java)
     * ```
     *
     * @receiver The [ReadContext] representing the parsed JSON document.
     * @param path A JSONPath expression that points to a JSON array.
     * @param clazz The [Class] of the list element type [T].
     * @return A [List] of [T] containing the deserialized elements.
     */
    @Deprecated(
        message = "Use decode() instead.",
        replaceWith = ReplaceWith("decode<List<T>>(path)", "io.amplicode.connekt.Connekt")
    )
    fun <T> ReadContext.readList(
        @Language("JSONPath") path: String,
        clazz: Class<T>
    ): List<T>
}
