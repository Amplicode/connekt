/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import com.fasterxml.jackson.databind.JsonNode
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.ReadContext
import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import io.amplicode.connekt.ide.Invokable
import io.amplicode.connekt.request.*
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.reflect.KProperty

class ConnektBuilder(
    private val connektContext: ConnektContext,
    @Suppress("unused")
    val env: EnvironmentStore,
    private val requests: MutableList<Executable<*>>
) {


    @Suppress("unused")
    val vars = VariablesStore(connektContext.values)

    @Suppress("unused")
    fun <T> variable(): DelegateProvider<T> {
        return DelegateProvider(connektContext.values)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun GET(
        path: String,
        configure: GetConfigurable.() -> Unit = {}
    ) = addRequest {
        GetConfigurable(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun POST(
        path: String,
        configure: PostConfigurable.() -> Unit = {}
    ) = addRequest {
        PostConfigurable(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun PUT(
        path: String,
        configure: PutConfigurable.() -> Unit = {}
    ) = addRequest {
        PutConfigurable(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun OPTIONS(
        path: String,
        configure: OptionsConfigurable.() -> Unit = {}
    ) = addRequest {
        OptionsConfigurable(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun PATCH(
        path: String,
        configure: PatchConfigurable.() -> Unit = {}
    ) = addRequest {
        PatchConfigurable(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun DELETE(
        path: String,
        configure: DeleteConfigurable.() -> Unit = {}
    ) = addRequest {
        DeleteConfigurable(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun HEAD(
        path: String,
        configure: HeadConfigurable.() -> Unit = {}
    ) = addRequest {
        HeadConfigurable(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun TRACE(
        path: String,
        configure: TraceConfigurable.() -> Unit = {}
    ) = addRequest {
        TraceConfigurable(path).apply(configure)
    }

    @Invokable
    @Suppress("unused")
    fun request(
        method: String,
        path: String,
        configure: BaseRequestConfigurable.() -> Unit = {}
    ) = addRequest {
        BaseRequestConfigurable(method, path).apply(configure)
    }

    @Invokable
    fun scenario(
        name: String,
        scenario: ScenarioBuilder.() -> Unit = {}
    ) {
        val scenarioBuilder = ScenarioBuilder(connektContext)

        requests.add(
            object : Executable<Unit>() {
                override fun execute() {
                    println("Scenario [${name}]")
                    scenarioBuilder.scenario()
                }
            }
        )
    }

    private val jsonPaths: WeakHashMap<Response, ReadContext> = WeakHashMap()

    fun Response.jsonPath(): ReadContext {
        var readContext = jsonPaths[this]

        if (readContext == null) {
            val stream = ByteArrayOutputStream()
            body?.source()?.buffer?.copyTo(stream)

            readContext = JsonPath.parse(
                ByteArrayInputStream(stream.toByteArray()),
                Configuration.builder()
                    .jsonProvider(JacksonJsonProvider(connektContext.objectMapper))
                    .mappingProvider(JacksonMappingProvider(connektContext.objectMapper))
                    .build()
            )

            jsonPaths[this] = readContext

            return readContext
        }

        return readContext
    }

    fun ReadContext.readString(path: String): String {
        return read(path)
    }

    fun ReadContext.readInt(path: String): Int {
        return read(path)
    }

    fun ReadContext.readLong(path: String): Int {
        return read(path)
    }

    fun <T> ReadContext.readList(path: String, clazz: Class<T>): List<T> {
        val nodes: List<JsonNode> = read(path, object : TypeRef<List<JsonNode>>() {})
        return nodes.map {
            connektContext.objectMapper.treeToValue(it, clazz)
        }
    }

    private fun <T : BaseRequestConfigurable> addRequest(requestBuilderSupplier: () -> T): Thenable<T> {
        val requestHolder = RequestHolder(
            connektContext,
            requestBuilderSupplier
        )
        requests.add(requestHolder)
        return Thenable(requestHolder)
    }


    operator fun <R> RequestBuilder<R>.getValue(
        any: Any?,
        property: KProperty<*>
    ): R {
        return when (this) {
            is Thenable<*> -> get(property.name) as R
            is Terminal<*, *> -> get(property.name) as R
        }
    }
}


class ScenarioBuilder(
    private val connektContext: ConnektContext
) {
    @Invokable
    @Suppress("FunctionName", "unused")
    fun GET(
        path: String,
        configure: GetConfigurable.() -> Unit = {}
    ) = addRequest {
        GetConfigurable(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun POST(
        path: String,
        configure: PostConfigurable.() -> Unit = {}
    ) = addRequest {
        PostConfigurable(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun PUT(
        path: String,
        configure: PutConfigurable.() -> Unit = {}
    ) = addRequest {
        PutConfigurable(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun OPTIONS(
        path: String,
        configure: OptionsConfigurable.() -> Unit = {}
    ) = addRequest {
        OptionsConfigurable(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun PATCH(
        path: String,
        configure: PatchConfigurable.() -> Unit = {}
    ) = addRequest {
        PatchConfigurable(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun DELETE(
        path: String,
        configure: DeleteConfigurable.() -> Unit = {}
    ) = addRequest {
        DeleteConfigurable(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun HEAD(
        path: String,
        configure: HeadConfigurable.() -> Unit = {}
    ) = addRequest {
        HeadConfigurable(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun TRACE(
        path: String,
        configure: TraceConfigurable.() -> Unit = {}
    ) = addRequest {
        TraceConfigurable(path).apply(configure)
    }

    @Invokable
    @Suppress("unused")
    fun request(
        method: String,
        path: String,
        configure: BaseRequestConfigurable.() -> Unit = {}
    ) = addRequest {
        BaseRequestConfigurable(method, path).apply(configure)
    }

    private fun <T : BaseRequestConfigurable> addRequest(requestBuilderSupplier: () -> T): Thenable<T> {
        val requestHolder = RequestHolder(
            connektContext,
            requestBuilderSupplier
        )
        return Thenable<T>(requestHolder)
    }

    operator fun <R> RequestBuilder<R>.getValue(any: Any?, property: KProperty<*>): R {
        return when (this) {
            is Thenable<*> -> execute() as R
            is Terminal<*, *> -> execute() as R
        }
    }
}
