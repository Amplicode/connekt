/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.amplicode.connekt.request.BaseRequestConfigurable
import okhttp3.*
import org.mapdb.DB
import org.mapdb.Serializer
import java.net.ConnectException


class RequestHandler<R>(
    private val requestHolder: RequestHolder<*>
) {
    fun get(): R {
        val value = requestHolder.context.values["name"]
            ?: return requestHolder.execute() as R

        return value as R
    }

    override fun toString(): String {
        return get().toString()
    }
}

sealed interface RequestBuilder<T>

class Thenable<T : BaseRequestConfigurable>(
    private val requestHolder: RequestHolder<T>
) : RequestBuilder<Unit> {
    @Suppress("unused")
    infix fun <R> then(assertCallback: Response.() -> R): Terminal<T, R> {
        requestHolder.thenCallback = assertCallback
        return Terminal(requestHolder)
    }

    internal fun toRequestHandler(name: String): RequestHandler<Unit> {
        requestHolder.resultName = name
        return RequestHandler(requestHolder)
    }

    internal fun get(name: String) {
        val value = requestHolder.context.values[name]
            ?: return requestHolder.execute() as Unit

        return value as Unit
    }

    internal fun execute() {
        requestHolder.execute()
    }
}

class Terminal<T : BaseRequestConfigurable, R>(
    private val requestHolder: RequestHolder<T>
) : RequestBuilder<R> {
    internal fun toRequestHandler(name: String): RequestHandler<R> {
        requestHolder.resultName = name
        return RequestHandler(requestHolder)
    }

    internal fun get(name: String): R {
        val value = requestHolder.context.values[name]
            ?: return requestHolder.execute() as R

        return value as R
    }

    internal fun execute(): R {
        return requestHolder.execute() as R
    }
}

class ConnektContext(
    db: DB
) : AutoCloseable {
    val objectMapper: ObjectMapper by lazy {
        ObjectMapper()
            .registerModules(kotlinModule())
            .enable(JsonGenerator.Feature.IGNORE_UNKNOWN)
            .enable(JsonParser.Feature.IGNORE_UNDEFINED)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    val cookies by lazy {
        db
            .hashMap("cookies", Serializer.STRING, Serializer.STRING)
            .createOrOpen()
    }

    val values: MutableMap<String, Any?> =
        db.hashMap("values", Serializer.STRING, Serializer.JAVA).createOrOpen()

    private val clients: MutableMap<ClientConfig, OkHttpClient> = mutableMapOf()

    fun getClient(clientConfig: ClientConfig): OkHttpClient {
        return clients.getOrElse(clientConfig) {
            val builder = OkHttpClient.Builder()

            if (clientConfig.allowRedirect) {
                builder.followRedirects(true)
                builder.followSslRedirects(true)
            }

            if (clientConfig.allowCookies) {
                builder.cookieJar(object : CookieJar {
                    override fun loadForRequest(url: HttpUrl): List<Cookie> {
                        //todo
                        return listOf()
                    }

                    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                        //todo
                    }

                })
            }

            if (clientConfig.http2) {
                builder.protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
            }

            builder.addNetworkInterceptor(CallLogger(System.out))

            val client = builder
                .build()

            clients[clientConfig] = client

            client
        }
    }

    companion object {
        data class ClientConfig(val allowCookies: Boolean, val allowRedirect: Boolean, val http2: Boolean)
    }

    override fun close() {
        for (client in clients) {
            client.value.connectionPool.evictAll()
        }
    }
}

class RequestHolder<T : BaseRequestConfigurable>(
    internal val context: ConnektContext,
    private val requestBuilderFactory: () -> T
) : Executable<Any?>() {
    internal var thenCallback: (Response.() -> Any?)? = null
    internal var resultName: String? = null
        set(value) {
            assert(field == null) { "Request already registered" }
            field = value
        }

    @Suppress("unused")
    infix fun then(thenCallback: Response.() -> Any?): RequestHolder<T> {
        this.thenCallback = thenCallback
        return this
    }

    override fun execute(): Any? {
        val requestBuilder = requestBuilderFactory()

        if (!requestBuilder.requestHints.noCookies) {
            context.cookies.forEach {
                requestBuilder.header("Cookie", it)
            }
        }

        val request = requestBuilder.build()

        val client = context.getClient(
            ConnektContext.Companion.ClientConfig(
                !requestBuilder.requestHints.noCookies,
                !requestBuilder.requestHints.noRedirect,
                requestBuilder.requestHints.http2
            )
        )

        val response: Response = client
            .newCall(request)
            .execute()


        val result = thenCallback?.invoke(response)

        resultName?.let { context.values[it] = result }

        return result
    }
}

object RequestExecutor {
    fun execute(executable: Executable<*>) {
        try {
            executable.execute()
        } catch (e: ConnectException) {
            //todo
        }
    }
}
