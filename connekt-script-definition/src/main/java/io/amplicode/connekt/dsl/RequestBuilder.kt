/*
 * Copyright (c) Haulmont 2025. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt.dsl

import io.amplicode.connekt.Body
import io.amplicode.connekt.Header
import io.amplicode.connekt.HeaderName
import io.amplicode.connekt.HeaderValue
import io.amplicode.connekt.MissingPathParameterException
import io.amplicode.connekt.client.ClientConfigurer
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.http.HttpMethod
import java.io.File
import java.util.*

@DslMarker
annotation class ConnektDsl

abstract class RequestBuilder {
    internal abstract fun build(): Request

    internal val requestHints: RequestHints = RequestHints(
        noCookies = false,
        noRedirect = false,
        http2 = false
    )
}

data class RequestHints(
    var noCookies: Boolean,
    var noRedirect: Boolean,
    var http2: Boolean
)

fun RequestHints.toClientConfigurer(): ClientConfigurer = {
    if (!noCookies) {
        cookieJar(object : CookieJar {
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                //todo
                return listOf()
            }

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                //todo
            }
        })
    }

    if (!noRedirect) {
        followRedirects(true)
        followSslRedirects(true)
    }

    if (http2) {
        protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
    }

    this
}

@ConnektDsl
class MultipartBodyBuilder(private val boundary: String) {
    private val parts = mutableListOf<MultipartBody.Part>()

    fun part(name: String? = null, contentType: String? = null, block: PartBuilder.() -> Unit) {
        parts.add(PartBuilder(contentType).apply {
            if (name != null) {
                contentDisposition(args = listOf("name" to name))
            }
        }.apply(block).build())
    }

    fun file(name: String, fileName: String, file: File) {
        part {
            contentDisposition(args = listOf("name" to name, "filename" to fileName))
            body(file.readBytes())
        }
    }

    internal fun build(): MultipartBody = MultipartBody(parts, boundary)

    @ConnektDsl
    class PartBuilder(private val contentType: String? = null) {
        private var body: RequestBody? = null
            set(value) {
                require(field == null) {
                    "Body already set"
                }

                field = value
            }
        private val headers = mutableListOf<Pair<String, Any>>()

        fun body(body: String) {
            this.body = StringBody(body)
        }

        fun body(body: ByteArray) {
            this.body = ByteArrayBody(body)
        }

        fun formDataBody(block: FormDataBodyBuilder.() -> Unit) {
            this.body = FormDataBodyBuilder().apply(block).build()
        }

        fun header(name: String, value: Any) {
            headers.add(name to value)
        }

        fun contentDisposition(value: String = "form-data", args: List<Pair<String, String>>) {
            headers.removeIf { it.first == "Content-Disposition" }
            header(
                "Content-Disposition",
                "$value; " + args.joinToString(separator = ";") { it.first + "=" + "\"" + it.second + "\"" })
        }

        internal fun build(): MultipartBody.Part {
            val body = body
            require(body != null) {
                "Body is mandatory"
            }
            return MultipartBody.Part(body, headers, contentType?.toMediaType())
        }
    }
}

@ConnektDsl
class FormDataBodyBuilder {
    private val fields = mutableListOf<Pair<String, String>>()

    fun field(name: String, value: Any) {
        fields.add(name to value.toString())
    }

    internal fun build(): FormDataBody = FormDataBody(fields)
}

interface RequestBuilderExtensions {
    fun BaseRequestBuilder.basicAuth(username: String, password: String) {
        val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        header("Authorization", "Basic $token")
    }

    fun BaseRequestBuilder.bearerAuth(token: String) {
        header("Authorization", "Bearer $token")
    }

    fun BaseRequestBuilder.contentType(@HeaderValue("Content-Type") contentType: String) {
        header("Content-Type", contentType)
    }

    fun BaseRequestBuilder.accept(@HeaderValue("Accept") contentType: String) {
        header("Accept", contentType)
    }
}

@ConnektDsl
open class BaseRequestBuilder(
    private val method: String,
    private val path: String,
) : RequestBuilder(), RequestBuilderExtensions {
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

    @Suppress("unused")
    fun noCookies() {
        this.requestHints.noCookies = true
    }

    @Suppress("unused")
    fun noRedirect() {
        this.requestHints.noRedirect = true
    }

    fun http2() {
        this.requestHints.http2 = true
    }

    @Suppress("unused")
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

    override fun build(): Request {
        applyAdditionalHeaders()
        val requestBuilder = Request.Builder()
            .headers(buildHeaders(headers))
            .url(createUrl())
            .method(method, createBody(body))
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
        );
        return replace(pathParamPattern) { matchResult ->
            val paramName = matchResult.groupValues[1]
            pathParamsMap.getOrElse(paramName) {
                throw MissingPathParameterException(paramName)
            }.toString()
        }
    }
}

class GetBuilder(path: String) : BaseRequestBuilder("GET", path)
class PostBuilder(path: String) : BaseRequestBuilder("POST", path)
class PutBuilder(path: String) : BaseRequestBuilder("PUT", path)
class OptionsBuilder(path: String) : BaseRequestBuilder("OPTIONS", path)
class PatchBuilder(path: String) : BaseRequestBuilder("PATCH", path)
class DeleteBuilder(path: String) : BaseRequestBuilder("DELETE", path)
class HeadBuilder(path: String) : BaseRequestBuilder("HEAD", path)
class TraceBuilder(path: String) : BaseRequestBuilder("TRACE", path)

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