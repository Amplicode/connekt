/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import io.amplicode.connekt.dsl.ConnektBuilder
import io.amplicode.connekt.dsl.decode
import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    fileExtension = "connekt.kts",
    compilationConfiguration = ConnektConfiguration::class
)
/**
 * Implicit base class for every `.connekt.kts` script.
 *
 * Every Connekt script is compiled against this class, which means all members of [ConnektBuilder]
 * are available at the script top-level without any explicit receiver. The actual implementation is
 * delegated to the [ConnektBuilder] instance supplied by the script host at runtime.
 *
 * Script authors do not instantiate this class directly — it is wired up automatically by the
 * Kotlin scripting infrastructure via the [@KotlinScript] annotation.
 *
 * @see ConnektBuilder
 */
abstract class Connekt(private val connektBuilder: ConnektBuilder) : ConnektBuilder by connektBuilder {

    /**
     * Deserializes the JSON response body to type [T] using a JSONPath expression.
     *
     * This is the primary convenience function for extracting typed values from an HTTP response
     * inside a `.connekt.kts` script. It reads the response body as JSON, evaluates [path] against
     * it, and returns the result coerced to [T].
     *
     * Example usage in a script:
     * ```kotlin
     * val name: String = response.decode("$.user.name")
     * val root: MyDto = response.decode()          // defaults to "$"
     * ```
     *
     * @param T The expected return type. Must be a reified type parameter so that the correct
     *   deserializer can be selected at compile time.
     * @param path A JSONPath expression indicating which part of the document to extract.
     *   Defaults to `"$"`, which returns the entire root object.
     * @return The value located at [path] in the response body, deserialized as [T].
     * @see io.amplicode.connekt.dsl.UseCaseBuilder.decode
     */
    inline fun <reified T> okhttp3.Response.decode(path: String = "$"): T {
        return jsonPath().decode<T>(path)
    }
}
