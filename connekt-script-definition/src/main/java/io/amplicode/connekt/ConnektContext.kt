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
import io.amplicode.connekt.client.ClientConfigurer
import io.amplicode.connekt.client.ClientFactory
import io.amplicode.connekt.client.ClientFactoryImpl
import io.amplicode.connekt.client.NoopClientConfigurer
import io.amplicode.connekt.console.Printer
import io.amplicode.connekt.console.SystemOutPrinter
import okhttp3.OkHttpClient
import org.mapdb.DB
import org.mapdb.Serializer

class ConnektContext(
    private val db: DB,
    val env: EnvironmentStore,
    val vars: VariablesStore,
    val printer: Printer = SystemOutPrinter,
    val clientFactory: ClientFactory = ClientFactoryImpl(printer)
) : AutoCloseable {

    var globalClientConfigurer: ClientConfigurer = NoopClientConfigurer

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

    fun getClient(customizeClient: ClientConfigurer = NoopClientConfigurer): OkHttpClient {
        return clientFactory.getClient {
            globalClientConfigurer().customizeClient()
        }
    }

    override fun close() {
        try {
            clientFactory.close()
            db.close()
        } catch (_: Exception) {
        }
    }
}