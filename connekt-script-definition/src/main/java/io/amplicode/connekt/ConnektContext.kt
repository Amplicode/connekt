/*
 * Copyright (c) Haulmont 2025. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.amplicode.connekt.console.Printer
import io.amplicode.connekt.console.SystemOutPrinter
import okhttp3.*
import org.mapdb.DB
import org.mapdb.Serializer
import java.util.concurrent.TimeUnit

class ConnektContext(
    private val db: DB,
    val env: EnvironmentStore,
    val vars: VariablesStore,
    val printer: Printer = SystemOutPrinter,
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

            builder.readTimeout(1, TimeUnit.MINUTES)
            builder.writeTimeout(1, TimeUnit.MINUTES)

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

            builder.addNetworkInterceptor(CallLogger(printer))

            val client = builder
                .build()

            clients[clientConfig] = client

            client
        }
    }

    override fun close() {
        for ((_, client) in clients) {
            client.connectionPool.evictAll()
        }
        try {
            db.close()
        } catch (_: Exception) {}
    }

    companion object {
        data class ClientConfig(
            val allowCookies: Boolean,
            val allowRedirect: Boolean,
            val http2: Boolean
        )
    }
}