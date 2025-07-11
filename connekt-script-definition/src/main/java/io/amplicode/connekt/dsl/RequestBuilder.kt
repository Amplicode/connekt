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

    fun configureRequest(configurer: RequestBuilderConfigurer) {
        requestBuilderTweaks += configurer
    }

    fun configureClient(configurer: ClientConfigurer) {
        clientBuilderTweaks += configurer
    }

    fun noCookies() {
        noCookies = true
    }

    fun noRedirect() {
        noRedirect = true
    }

    fun http2() {
        http2 = true
    }

    fun headers(@Header vararg headers: Pair<String, Any>) {
        this.headers.addAll(headers)
    }

    fun header(
        @HeaderName key: String,
        @HeaderValue value: Any
    ) {
        headers.add(key to value)
    }

    fun body(@Body body: String) {
        this.body = StringBody(body)
    }

    fun body(body: ByteArray) {
        this.body = ByteArrayBody(body)
    }

    fun formData(block: FormDataBodyBuilder.() -> Unit) {
        this.body = FormDataBodyBuilder().apply(block).build()
    }

    fun multipart(boundary: String = "boundary", block: MultipartBodyBuilder.() -> Unit) {
        val builder = MultipartBodyBuilder(boundary).apply(block)
        this.body = builder.build()
    }

    fun queryParams(vararg params: Pair<String, Any>) {
        params.forEach { (key, value) ->
            queryParamsMap[key] = value
        }
    }

    fun queryParam(vararg params: Pair<String, Any>) {
        params.forEach { (key, value) ->
            queryParamsMap[key] = value
        }
    }

    fun queryParam(key: String, value: Any) {
        queryParamsMap[key] = value
    }

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
