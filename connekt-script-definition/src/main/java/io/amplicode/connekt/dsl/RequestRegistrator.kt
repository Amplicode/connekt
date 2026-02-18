@file:Suppress("FunctionName")

package io.amplicode.connekt.dsl

import io.amplicode.connekt.Request
import io.amplicode.connekt.RequestBuilderCall
import io.amplicode.connekt.RequestName
import io.amplicode.connekt.RequestPath

/**
 * Base interface for registering HTTP requests in a Connekt script.
 *
 * Implementations of this interface are provided by the Connekt runtime and exposed to script
 * authors as the primary entry point for declaring HTTP requests. The interface exposes a single
 * [request] method that accepts an arbitrary HTTP method string; convenience extension functions
 * ([GET], [POST], [PUT], [PATCH], [DELETE], [HEAD], [OPTIONS], [TRACE]) cover the standard HTTP
 * verbs and delegate to [request] internally.
 *
 * @param R the return type produced when a request declaration is registered (e.g. a request
 *   descriptor or `Unit`, depending on the runtime context)
 * @see GET
 * @see POST
 * @see PUT
 * @see PATCH
 * @see DELETE
 * @see HEAD
 * @see OPTIONS
 * @see TRACE
 */
interface RequestRegistrator<R> {
    /**
     * Registers an HTTP request with an explicit HTTP method string.
     *
     * This is the core building block used by all HTTP verb extension functions. Prefer calling
     * the named verb extensions (e.g. [GET], [POST]) instead of this method directly unless you
     * need a non-standard HTTP method.
     *
     * ```kotlin
     * val response by request("REPORT", "$baseUrl/calendars/main") {
     *     contentType("text/xml")
     *     body("""<C:calendar-query xmlns:C="urn:ietf:params:xml:ns:caldav">...</C:calendar-query>""")
     * }
     * ```
     *
     * @param method the HTTP method to use (e.g. `"GET"`, `"POST"`, or a custom verb).
     * @param path the request URL or URL template (may contain path variables).
     * @param name an optional human-readable name for the request, used in logs and reports.
     *   Defaults to `null`, in which case the runtime derives a name automatically.
     * @param configure a lambda applied to a [RequestBuilder] that allows setting headers, body,
     *   query parameters, and other request options. Defaults to an empty configuration.
     * @return a value of type [R] representing the registered request, as defined by the runtime.
     * @see RequestBuilder
     * @see GET
     * @see POST
     * @see OPTIONS
     * @see PUT
     * @see PATCH
     * @see DELETE
     * @see HEAD
     * @see TRACE
     */
    @RequestBuilderCall
    fun request(
        method: String,
        @RequestPath path: String,
        @RequestName name: String? = null,
        configure: RequestBuilder.() -> Unit = {}
    ): R
}

/**
 * Registers an HTTP OPTIONS request.
 *
 * ```kotlin
 * OPTIONS("$baseUrl/resource")
 *
 * // With headers
 * OPTIONS("$baseUrl/resource") {
 *     header("Origin", "https://example.com")
 * }
 * ```
 *
 * @param path the request URL or URL template.
 * @param name an optional human-readable name for the request used in logs and reports.
 *   Defaults to `null`.
 * @param configure a lambda applied to a [RequestBuilder] for setting headers, query parameters,
 *   and other request options. Defaults to an empty configuration.
 * @return a value of type [R] representing the registered request, as defined by the runtime.
 * @see RequestBuilder
 */
@RequestBuilderCall
@Request("OPTIONS")
fun <R> RequestRegistrator<R>.OPTIONS(
    @RequestPath path: String,
    @RequestName name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("OPTIONS", path, name, configure)

/**
 * Registers an HTTP POST request.
 *
 * Supports JSON, form-data, and multipart bodies. The response can be captured raw or
 * transformed with [then]:
 *
 * ```kotlin
 * // Simplest case — fire and forget
 * POST("$baseUrl/pets/notify")
 *
 * // JSON body
 * val created by POST("$baseUrl/pets") {
 *     contentType("application/json")
 *     body("""{"name": "Fido", "species": "dog"}""")
 * }
 *
 * // URL-encoded form with `then` to extract a value
 * val token by POST("$baseUrl/auth/token") {
 *     formData {
 *         field("username", "alice")
 *         field("password", "secret")
 *     }
 * } then {
 *     decode<String>("$.access_token")
 * }
 *
 * // Multipart file upload
 * val upload by POST("$baseUrl/photos") {
 *     multipart {
 *         part(name = "metadata", contentType = "application/json") {
 *             body("""{"album": "vacation"}""")
 *         }
 *         file(name = "photo", fileName = "beach.jpg", file = File("/tmp/beach.jpg"))
 *     }
 * }
 * ```
 *
 * @param path the request URL or URL template.
 * @param name an optional human-readable name for the request used in logs and reports.
 *   Defaults to `null`.
 * @param configure a lambda applied to a [RequestBuilder] for setting headers, body, query
 *   parameters, and other request options. Defaults to an empty configuration.
 * @return a value of type [R] representing the registered request, as defined by the runtime.
 * @see RequestBuilder
 * @see RequestBuilder.formData
 * @see RequestBuilder.multipart
 * @see UseCaseBuilder.then
 */
@RequestBuilderCall
@Request("POST")
fun <R> RequestRegistrator<R>.POST(
    @RequestPath path: String,
    @RequestName name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("POST", path, name, configure)

/**
 * Registers an HTTP GET request.
 *
 * The result can be delegated to a `val` to capture the raw response, or piped through [then]
 * to transform it into a typed value:
 *
 * ```kotlin
 * // Simplest case
 * GET("$baseUrl/pets")
 *
 * // Capture raw response
 * val response by GET("$baseUrl/pets") {
 *     header("Accept", "application/json")
 *     queryParam("status", "available")
 * }
 *
 * // Transform response with `then`
 * val petNames by GET("$baseUrl/pets") then {
 *     decode<List<String>>("$.name")
 * }
 * ```
 *
 * @param path the request URL or URL template.
 * @param name an optional human-readable name for the request used in logs and reports.
 *   Defaults to `null`.
 * @param configure a lambda applied to a [RequestBuilder] for setting headers, query parameters,
 *   and other request options. Defaults to an empty configuration.
 * @return a value of type [R] representing the registered request, as defined by the runtime.
 * @see RequestBuilder
 * @see UseCaseBuilder.then
 */
@RequestBuilderCall
@Request("GET")
fun <R> RequestRegistrator<R>.GET(
    @RequestPath path: String,
    @RequestName name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("GET", path, name, configure)

/**
 * Registers an HTTP PUT request.
 *
 * Use [then] to decode the updated resource from the response body:
 *
 * ```kotlin
 * // Simplest case
 * PUT("$baseUrl/pets/{id}") {
 *     pathParam("id", petId)
 *     contentType("application/json")
 *     body("""{"name": "Rex"}""")
 * }
 *
 * // Capture decoded result
 * data class Pet(val id: String, val name: String, val status: String)
 *
 * val updated by PUT("$baseUrl/pets/{id}") {
 *     pathParam("id", petId)
 *     contentType("application/json")
 *     body("""{"name": "Rex", "status": "available"}""")
 * } then {
 *     decode<Pet>("$")
 * }
 * println(updated.status) // "available"
 * ```
 *
 * @param path the request URL or URL template.
 * @param name an optional human-readable name for the request used in logs and reports.
 *   Defaults to `null`.
 * @param configure a lambda applied to a [RequestBuilder] for setting headers, body, query
 *   parameters, and other request options. Defaults to an empty configuration.
 * @return a value of type [R] representing the registered request, as defined by the runtime.
 * @see RequestBuilder
 * @see UseCaseBuilder.then
 */
@RequestBuilderCall
@Request("PUT")
fun <R> RequestRegistrator<R>.PUT(
    @RequestPath path: String,
    @RequestName name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("PUT", path, name, configure)

/**
 * Registers an HTTP PATCH request.
 *
 * Use [then] to decode the server's representation of the patched resource:
 *
 * ```kotlin
 * // Simplest case
 * PATCH("$baseUrl/pets/{id}") {
 *     pathParam("id", petId)
 *     contentType("application/json")
 *     body("""{"status": "adopted"}""")
 * }
 *
 * // Capture decoded result
 * val newStatus by PATCH("$baseUrl/pets/{id}") {
 *     pathParam("id", petId)
 *     contentType("application/json")
 *     body("""{"status": "adopted"}""")
 * } then {
 *     decode<String>("$.status")
 * }
 * println(newStatus) // "adopted"
 * ```
 *
 * @param path the request URL or URL template.
 * @param name an optional human-readable name for the request used in logs and reports.
 *   Defaults to `null`.
 * @param configure a lambda applied to a [RequestBuilder] for setting headers, body, query
 *   parameters, and other request options. Defaults to an empty configuration.
 * @return a value of type [R] representing the registered request, as defined by the runtime.
 * @see RequestBuilder
 * @see UseCaseBuilder.then
 */
@RequestBuilderCall
@Request("PATCH")
fun <R> RequestRegistrator<R>.PATCH(
    @RequestPath path: String,
    @RequestName name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("PATCH", path, name, configure)

/**
 * Registers an HTTP DELETE request.
 *
 * Use [then] to inspect the response after deletion:
 *
 * ```kotlin
 * // Simplest case
 * DELETE("$baseUrl/pets/{id}") {
 *     pathParam("id", petId)
 * }
 *
 * // With response inspection
 * val deleted by DELETE("$baseUrl/pets/{id}") {
 *     pathParam("id", petId)
 * } then {
 *     decode<String>("$.message")
 * }
 * ```
 *
 * @param path the request URL or URL template.
 * @param name an optional human-readable name for the request used in logs and reports.
 *   Defaults to `null`.
 * @param configure a lambda applied to a [RequestBuilder] for setting headers, query parameters,
 *   and other request options. Defaults to an empty configuration.
 * @return a value of type [R] representing the registered request, as defined by the runtime.
 * @see RequestBuilder
 * @see UseCaseBuilder.then
 */
@RequestBuilderCall
@Request("DELETE")
fun <R> RequestRegistrator<R>.DELETE(
    @RequestPath path: String,
    @RequestName name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("DELETE", path, name, configure)

/**
 * Registers an HTTP HEAD request.
 *
 * HEAD is identical to GET but instructs the server to return only response headers without a
 * message body. It is commonly used to check resource existence or retrieve metadata.
 *
 * ```kotlin
 * HEAD("$baseUrl/pets/{id}") {
 *     pathParam("id", petId)
 * }
 *
 * // Capture headers from the response
 * val response by HEAD("$baseUrl/pets/{id}") {
 *     pathParam("id", petId)
 * }
 * val etag = response.header("ETag")
 * ```
 *
 * @param path the request URL or URL template.
 * @param name an optional human-readable name for the request used in logs and reports.
 *   Defaults to `null`.
 * @param configure a lambda applied to a [RequestBuilder] for setting headers, query parameters,
 *   and other request options. Defaults to an empty configuration.
 * @return a value of type [R] representing the registered request, as defined by the runtime.
 * @see RequestBuilder
 */
@RequestBuilderCall
@Request("HEAD")
fun <R> RequestRegistrator<R>.HEAD(
    @RequestPath path: String,
    @RequestName name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("HEAD", path, name, configure)

/**
 * Registers an HTTP TRACE request.
 *
 * TRACE performs a message loop-back test along the path to the target resource and is primarily
 * used for diagnostic purposes.
 *
 * ```kotlin
 * TRACE("$baseUrl/echo")
 * ```
 *
 * @param path the request URL or URL template.
 * @param name an optional human-readable name for the request used in logs and reports.
 *   Defaults to `null`.
 * @param configure a lambda applied to a [RequestBuilder] for setting headers, query parameters,
 *   and other request options. Defaults to an empty configuration.
 * @return a value of type [R] representing the registered request, as defined by the runtime.
 * @see RequestBuilder
 */
@RequestBuilderCall
@Request("TRACE")
fun <R> RequestRegistrator<R>.TRACE(
    @RequestPath path: String,
    @RequestName name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("TRACE", path, name, configure)
