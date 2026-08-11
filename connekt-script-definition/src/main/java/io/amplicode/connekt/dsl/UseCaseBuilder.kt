package io.amplicode.connekt.dsl

import okhttp3.Response
import kotlin.reflect.KProperty
import kotlin.time.Duration

/**
 * DSL context receiver for a `useCase {}` block.
 *
 * An instance of [UseCaseBuilder] is the implicit `this` inside every `useCase` lambda. It
 * provides access to HTTP request helpers inherited from [RequestRegistrator], JSON parsing
 * utilities inherited from [JsonPathExtensionsProvider], and the higher-level convenience
 * members defined here.
 *
 * Typical usage:
 * ```kotlin
 * useCase {
 *     val pets by GET("$baseUrl/pets")
 *     val names by pets.decode<List<String>>("$.name")
 * }
 * ```
 */
@ConnektDsl
abstract class UseCaseBuilder : RequestRegistrator<Response>, JsonPathExtensionsProvider {

    internal var ttlDuration: Duration? = null
        private set(value) {
            require(field == null) { "TTL already set. Use ttl() only once per useCase" }
            field = value
        }

    /**
     * Sets a fixed time-to-live for the value this useCase produces when it is delegated to a
     * variable (via `by`). After the TTL elapses, the next access to the variable re-runs the
     * useCase and refreshes the stored value.
     *
     * Only a fixed [duration] is supported for a useCase: unlike a single request, a useCase returns
     * its value imperatively, so a response-derived TTL has no single response to compute from.
     *
     * @param duration How long the produced value stays valid, measured from the moment the useCase
     *   finishes.
     */
    fun ttl(duration: Duration) {
        ttlDuration = duration
    }

    /**
     * Enables property delegation for HTTP responses using the `by` keyword.
     *
     * When a request builder (e.g. the result of `GET(...)`) is used as a delegated property,
     * this operator is invoked to execute the request and bind the resulting [Response] to the
     * property. This allows concise, readable request declarations:
     *
     * ```kotlin
     * val data by GET("$baseUrl/resource")
     * ```
     *
     * @receiver The value to be delegated — typically the return value of a request factory
     *   function such as `GET(...)`.
     * @param receiver Unused — the object that owns the delegated property.
     * @param prop Unused — metadata about the delegated property.
     * @return A [ValueDelegate] that holds the executed result of type [T].
     */
    abstract operator fun <T> T.provideDelegate(
        @Suppress("unused") receiver: Any?,
        @Suppress("unused") prop: KProperty<*>
    ): ValueDelegate<T>

    /**
     * Chains response handling after an HTTP request using infix notation.
     *
     * This infix function allows a [Response] to be piped into a handler block in a fluent,
     * readable style:
     *
     * ```kotlin
     * val names by GET("$baseUrl/pets") then { decode<List<String>>("$.name") }
     * ```
     *
     * @receiver The [Response] returned by an HTTP request.
     * @param handle A lambda with [Response] as its receiver that transforms or extracts data
     *   from the response.
     * @return The value produced by [handle].
     */
    infix fun <T> Response.then(handle: Response.() -> T): T = run(handle)

    /**
     * Deserializes the response body from JSON at the given JSONPath expression.
     *
     * Parses the response body as a JSON document, navigates to [path] using JSONPath, and
     * deserializes the value found there into type [T]. The default path `"$"` represents the
     * entire document root.
     *
     * ```kotlin
     * val pets = response.decode<List<Pet>>("$.pets")
     * val count = response.decode<Int>("$.totalCount")
     * ```
     *
     * @receiver The [Response] whose body should be deserialized.
     * @param path A JSONPath expression indicating the value to extract. Defaults to `"$"` (root).
     * @return The deserialized value of type [T].
     */
    inline fun <reified T> Response.decode(path: String = "$"): T {
        return jsonPath().decode<T>(path)
    }
}
