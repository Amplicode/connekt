/*
 * Copyright (c) Haulmont 2025. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt.dsl

import io.amplicode.connekt.Body
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.Header
import io.amplicode.connekt.HeaderName
import io.amplicode.connekt.HeaderValue
import io.amplicode.connekt.MissingPathParameterException
import io.amplicode.connekt.context.ClientConfigurer
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.http.HttpMethod

@DslMarker
annotation class ConnektDsl

private fun buildHeaders(headers: List<Pair<String, Any>>) = Headers.Builder().apply {
    headers.forEach {
        add(it.first, it.second.toString())
    }
}.build()

private fun RequestBody.toOkHttpBody(contentType: MediaType? = null): okhttp3.RequestBody {
    return when (this) {
        is ByteArrayBody -> body.toRequestBody(contentType)
        is FormDataBody -> FormBody.Builder()
            .apply {
                for ((name, value) in body) {
                    add(name, value)
                }
            }
            .build()

        is StringBody -> body.toRequestBody(contentType)

        is MultipartBody -> okhttp3.MultipartBody.Builder(boundary)
            .apply {
                setType(okhttp3.MultipartBody.FORM)
                for (part in parts) {
                    assert(part.body !is MultipartBody) { "Unable to use multipart as multipart part" }
                    addPart(buildHeaders(part.headers), part.body.toOkHttpBody(part.contentType))
                }
            }
            .build()
    }
}

/**
 * Fluent builder for a single HTTP request within the Connekt scripting DSL.
 *
 * An instance of [RequestBuilder] is provided as the receiver of the `configure` lambda passed to
 * every HTTP method shortcut (e.g. `GET`, `POST`). Use it to set headers, query/path parameters,
 * the request body, cookie/redirect policies, and low-level OkHttp client or request tweaks.
 *
 * The class is annotated with [ConnektDsl] to prevent accidental implicit scope leakage when
 * lambdas are nested inside one another.
 */
// TODO extract interface
@ConnektDsl
class RequestBuilder(
    private val method: String,
    private val path: String,
    private val context: ConnektContext?
) {
    private var noCookies = false
    private var noRedirect = false
    private var http2 = false

    private val requestBuilderTweaks: MutableList<RequestBuilderConfigurer> = mutableListOf()
    private val clientBuilderTweaks: MutableList<ClientConfigurer> = mutableListOf()

    private var body: RequestBody? = null
        set(value) {
            require(field == null) {
                "Body already set"
            }
            field = value
        }

    private var headers = mutableListOf<Pair<String, Any>>()
    private val queryParamsMap = mutableMapOf<String, Any>()
    private val pathParamsMap = mutableMapOf<String, Any>()

    /**
     * Registers a low-level [RequestBuilderConfigurer] lambda that is applied to the underlying
     * OkHttp [Request.Builder] just before the request is built.
     *
     * Multiple configurers can be registered; they are applied in the order they were added.
     *
     * ```kotlin
     * GET("$baseUrl/resource") {
     *     configureRequest {
     *         ...
     *     }
     * }
     * ```
     *
     * @param configurer A lambda with [Request.Builder] as its receiver that can call any
     *   [Request.Builder] method to further customize the request.
     * @see okhttp3.Request.Builder
     */
    fun configureRequest(configurer: RequestBuilderConfigurer) {
        requestBuilderTweaks += configurer
    }

    /**
     * Registers a low-level [ClientConfigurer] lambda that is applied to the underlying
     * [okhttp3.OkHttpClient.Builder] before the client is used to dispatch the request.
     *
     * Multiple configurers can be registered; they are applied in the order they were added.
     * This is the extension point for advanced client settings such as custom interceptors,
     * SSL configuration, or timeouts.
     *
     * ```kotlin
     * GET("$baseUrl/slow-endpoint") {
     *     configureClient {
     *         readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
     *     }
     * }
     * ```
     *
     * @param configurer A lambda with [okhttp3.OkHttpClient.Builder] as its receiver.
     * @see okhttp3.OkHttpClient.Builder
     */
    fun configureClient(configurer: ClientConfigurer) {
        clientBuilderTweaks += configurer
    }

    /**
     * Disables cookie handling for this request.
     *
     * By default, cookies stored in the session's cookie jar are sent with every request and any
     * cookies returned in the response are persisted. Calling this method opts the request out of
     * that behavior so no cookies are read from or written to the jar.
     *
     * ```kotlin
     * GET("$baseUrl/resource") {
     *     noCookies()
     * }
     * ```
     */
    fun noCookies() {
        noCookies = true
    }

    /**
     * Disables automatic HTTP redirect following for this request.
     *
     * By default, OkHttp follows both HTTP and HTTPS redirects. Calling this method stops the
     * client from following `3xx` redirect responses, leaving the redirect response as the final
     * result so that the script can inspect the `Location` header manually.
     *
     * ```kotlin
     * GET("$baseUrl/old-url") {
     *     noRedirect()
     * }
     * ```
     */
    fun noRedirect() {
        noRedirect = true
    }

    /**
     * Forces the request to be sent over HTTP/2 using prior-knowledge (clear-text h2c).
     *
     * When called, the client's protocol list is replaced with [Protocol.H2_PRIOR_KNOWLEDGE],
     * which skips the TLS/ALPN negotiation and connects directly using HTTP/2. This setting is
     * suitable for plain-text HTTP/2 backends (e.g. gRPC over h2c) and should not be combined
     * with HTTPS URLs.
     *
     * ```kotlin
     * GET("http://grpc-backend/api/resource") {
     *     http2()
     * }
     * ```
     */
    fun http2() {
        http2 = true
    }

    /**
     * Adds multiple request headers at once.
     *
     * If a header with the same name is added more than once, both values are retained and sent
     * (multi-value headers). Header values are converted to their [String] representation via
     * [Any.toString].
     *
     * ```kotlin
     * GET("$baseUrl/resource") {
     *     headers("Accept" to "application/json", "X-Api-Version" to "2")
     * }
     * ```
     *
     * @param headers Pairs of header name to header value. The header name must be a valid HTTP
     *   header field name.
     */
    fun headers(@Header vararg headers: Pair<String, Any>) {
        this.headers.addAll(headers)
    }

    /**
     * Adds a single request header.
     *
     * If a header with the same [key] is added more than once, both values are retained and sent
     * (multi-value headers). The [value] is converted to its [String] representation via
     * [Any.toString].
     *
     * ```kotlin
     * GET("$baseUrl/resource") {
     *     header("Accept", "application/json")
     * }
     * ```
     *
     * @param key The HTTP header field name (e.g. `"Content-Type"`).
     * @param value The header value. Non-string values are converted with [Any.toString].
     */
    fun header(
        @HeaderName key: String,
        @HeaderValue value: Any
    ) {
        headers.add(key to value)
    }

    /**
     * Sets the request body to the given plain-text string.
     *
     * The string is sent as-is; it is the caller's responsibility to set an appropriate
     * `Content-Type` header (e.g. `application/json` or `text/plain`).
     *
     * ```kotlin
     * POST("$baseUrl/pets") {
     *     body("""{"name": "Fido"}""")
     * }
     * ```
     *
     * @param body The body string to send with the request.
     * @throws IllegalArgumentException if a body has already been set for this request.
     */
    fun body(@Body body: String) {
        this.body = StringBody(body)
    }

    /**
     * Sets the request body to the given raw byte array.
     *
     * Use this overload when the payload is binary data (e.g. a serialized protobuf message or a
     * file's raw bytes). Set an appropriate `Content-Type` header separately if required.
     *
     * ```kotlin
     * POST("$baseUrl/upload") {
     *     header("Content-Type", "application/octet-stream")
     *     body(File("/tmp/data.bin").readBytes())
     * }
     * ```
     *
     * @param body The raw bytes to send as the request body.
     * @throws IllegalArgumentException if a body has already been set for this request.
     */
    fun body(body: ByteArray) {
        this.body = ByteArrayBody(body)
    }

    /**
     * Sets the request body to an `application/x-www-form-urlencoded` form.
     *
     * The [block] lambda receives a [FormDataBodyBuilder] as its receiver. Use
     * [FormDataBodyBuilder] methods to add individual form fields. A `Content-Type:
     * application/x-www-form-urlencoded` header is added automatically when the request is built.
     *
     * ```kotlin
     * POST("$baseUrl/login") {
     *     formData {
     *         field("username", "alice")
     *         field("password", "secret")
     *     }
     * }
     * ```
     *
     * @param block Builder lambda for populating form fields.
     * @throws IllegalArgumentException if a body has already been set for this request.
     * @see FormDataBodyBuilder
     */
    fun formData(block: FormDataBodyBuilder.() -> Unit) {
        this.body = FormDataBodyBuilder().apply(block).build()
    }

    /**
     * Sets the request body to a `multipart/form-data` body.
     *
     * The [block] lambda receives a [MultipartBodyBuilder] as its receiver. Use
     * [MultipartBodyBuilder] methods to add individual parts. A `Content-Type:
     * multipart/form-data; boundary=<boundary>` header is added automatically when the request
     * is built.
     *
     * ```kotlin
     * POST("$baseUrl/upload") {
     *     multipart {
     *         part(name = "metadata", contentType = "application/json") {
     *             body("""{"description": "profile picture"}""")
     *         }
     *         file(name = "photo", fileName = "avatar.jpg", file = File("/tmp/avatar.jpg"))
     *     }
     * }
     * ```
     *
     * @param boundary The MIME boundary string that separates parts in the multipart body.
     *   Defaults to `"boundary"`.
     * @param block Builder lambda for populating multipart parts.
     * @throws IllegalArgumentException if a body has already been set for this request.
     * @see MultipartBodyBuilder
     */
    fun multipart(boundary: String = "boundary", block: MultipartBodyBuilder.() -> Unit) {
        val builder = MultipartBodyBuilder(boundary).apply(block)
        this.body = builder.build()
    }

    /**
     * Adds multiple query parameters to the request URL at once.
     *
     * If the same key is provided more than once, the last value wins because the parameters are
     * stored in a map. Values are converted to their [String] representation via [Any.toString].
     *
     * ```kotlin
     * GET("$baseUrl/pets") {
     *     queryParams("status" to "available", "limit" to 20)
     * }
     * ```
     *
     * @param params Pairs of query parameter name to value.
     */
    fun queryParams(vararg params: Pair<String, Any>) {
        params.forEach { (key, value) ->
            queryParamsMap[key] = value
        }
    }

    /**
     * Adds one or more query parameters to the request URL using [Pair] arguments.
     *
     * This is a convenience overload of [queryParams] that accepts the same [Pair]-based syntax
     * but is named `queryParam` (singular) for readability when adding a single parameter.
     * If the same key is provided more than once across calls, the last value wins.
     *
     * ```kotlin
     * GET("$baseUrl/pets") {
     *     queryParam("status" to "available")
     * }
     * ```
     *
     * @param params Pairs of query parameter name to value.
     */
    fun queryParam(vararg params: Pair<String, Any>) {
        params.forEach { (key, value) ->
            queryParamsMap[key] = value
        }
    }

    /**
     * Adds a single query parameter to the request URL using explicit key and value arguments.
     *
     * If a parameter with the same [key] was previously registered, it is replaced by [value].
     * The value is converted to its [String] representation via [Any.toString].
     *
     * ```kotlin
     * GET("$baseUrl/pets") {
     *     queryParam("status", "available")
     * }
     * ```
     *
     * @param key The query parameter name.
     * @param value The query parameter value. Non-string values are converted with [Any.toString].
     */
    fun queryParam(key: String, value: Any) {
        queryParamsMap[key] = value
    }

    /**
     * Supplies the value for a named path parameter in the request URL template.
     *
     * Path parameters are denoted by curly-brace placeholders in the URL path, for example
     * `{id}` in `https://example.com/users/{id}`. Call this method once per placeholder,
     * using the placeholder name (without braces) as [key].
     *
     * ```kotlin
     * GET("$baseUrl/pets/{id}") {
     *     pathParam("id", 42)
     * }
     * ```
     *
     * @param key The name of the path parameter placeholder (e.g. `"id"`).
     * @param value The value that replaces the placeholder. Non-string values are converted with
     *   [Any.toString].
     * @throws io.amplicode.connekt.MissingPathParameterException if the URL template contains a
     *   placeholder for which no value has been provided when the request is built.
     */
    fun pathParam(key: String, value: Any) {
        pathParamsMap[key] = value
    }

    // TODO move to impl API
    fun build(): Request {
        applyAdditionalHeaders()
        val requestBuilder = Request.Builder()
            .headers(buildHeaders(headers))
            .url(createUrl())
            .method(method, createBody(body))

        requestBuilderTweaks.forEach { configure ->
            requestBuilder.configure()
        }

        return requestBuilder.build()
    }

    private fun applyAdditionalHeaders() {
        val body = this.body
        when (body) {
            is FormDataBody -> header(
                "Content-Type",
                "application/x-www-form-urlencoded"
            )

            is MultipartBody -> header(
                "Content-Type",
                "multipart/form-data; boundary=${body.boundary}"
            )

            else -> {
                // Do nothing
            }
        }

        if ("User-Agent" !in headers.map { it.first }) {
            header("User-Agent", "connekt/0.0.1")
        }
    }

    private fun createBody(body: RequestBody?) = when {
        body != null -> body.toOkHttpBody()
        HttpMethod.requiresRequestBody(method) -> ByteArray(0).toRequestBody()
        else -> null
    }

    private fun createUrl(): HttpUrl {
        val normalizedPath = path.replacePathParams()
        return normalizedPath
            .toHttpUrl()
            .newBuilder()
            .also { urlBuilder ->
                queryParamsMap.forEach { (name, value) ->
                    urlBuilder.addQueryParameter(name, value.toString())
                }
            }
            .build()
    }

    private fun String.replacePathParams(): String {
        val pathParamPattern = Regex(
            """\{(\p{javaJavaIdentifierStart}[\p{javaJavaIdentifierPart}-]*)\}"""
        )
        return replace(pathParamPattern) { matchResult ->
            val paramName = matchResult.groupValues[1]
            pathParamsMap.getOrElse(paramName) {
                throw MissingPathParameterException(paramName)
            }.toString()
        }
    }

    // TODO move into impl API
    fun getClientConfigurer(): ClientConfigurer = {
        if (!noCookies) {
            val cookieJar = context?.cookiesContext?.cookieJar
            if (cookieJar != null) {
                cookieJar(cookieJar)
            }
        }

        followRedirects(!noRedirect)
        followSslRedirects(!noRedirect)

        if (http2) {
            protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
        }

        clientBuilderTweaks.forEach { configure ->
            configure()
        }
    }
}

typealias RequestBuilderConfigurer = Request.Builder.() -> Unit
