/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt.dsl

import com.fasterxml.jackson.databind.JsonNode
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.ReadContext
import com.jayway.jsonpath.TypeRef
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import io.amplicode.connekt.*
import io.amplicode.connekt.ConnektRequestHolder
import io.amplicode.connekt.ide.Invokable
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
        configure: GetBuilder.() -> Unit = {}
    ) = addRequest {
        GetBuilder(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun POST(
        path: String,
        configure: PostBuilder.() -> Unit = {}
    ) = addRequest {
        PostBuilder(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun PUT(
        path: String,
        configure: PutBuilder.() -> Unit = {}
    ) = addRequest {
        PutBuilder(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun OPTIONS(
        path: String,
        configure: OptionsBuilder.() -> Unit = {}
    ) = addRequest {
        OptionsBuilder(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun PATCH(
        path: String,
        configure: PatchBuilder.() -> Unit = {}
    ) = addRequest {
        PatchBuilder(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun DELETE(
        path: String,
        configure: DeleteBuilder.() -> Unit = {}
    ) = addRequest {
        DeleteBuilder(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun HEAD(
        path: String,
        configure: HeadBuilder.() -> Unit = {}
    ) = addRequest {
        HeadBuilder(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun TRACE(
        path: String,
        configure: TraceBuilder.() -> Unit = {}
    ) = addRequest {
        TraceBuilder(path).apply(configure)
    }

    @Invokable
    @Suppress("unused")
    fun request(
        method: String,
        path: String,
        configure: BaseRequestBuilder.() -> Unit = {}
    ) = addRequest {
        BaseRequestBuilder(method, path).apply(configure)
    }

    @Invokable
    fun flow(
        name: String?,
        flow: FlowBuilder.() -> Unit = {}
    ) {
        val flowBuilder = FlowBuilder(connektContext)

        requests.add(
            object : Executable<Unit>() {
                override fun execute() {
                    connektContext.printer.println("Running flow [${name}]")
                    flowBuilder.flow()
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

    private fun <T : BaseRequestBuilder> addRequest(requestBuilderSupplier: () -> T): Thenable<T> {
        val connektRequest = ConnektRequest(
            connektContext,
            requestBuilderSupplier
        )
        requests.add(connektRequest)
        return Thenable(connektRequest)
    }


    operator fun <R> ConnektRequestHolder<R>.getValue(
        any: Any?,
        property: KProperty<*>
    ): R {
        return when (this) {
            is Thenable<*> -> getOrExecuteWithName(property.name) as R
            is Terminal<*, *> -> getOrExecuteWithName(property.name) as R
        }
    }
}


class FlowBuilder(
    private val connektContext: ConnektContext
) {
    @Invokable
    @Suppress("FunctionName", "unused")
    fun GET(
        path: String,
        configure: GetBuilder.() -> Unit = {}
    ) = addRequest {
        GetBuilder(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun POST(
        path: String,
        configure: PostBuilder.() -> Unit = {}
    ) = addRequest {
        PostBuilder(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun PUT(
        path: String,
        configure: PutBuilder.() -> Unit = {}
    ) = addRequest {
        PutBuilder(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun OPTIONS(
        path: String,
        configure: OptionsBuilder.() -> Unit = {}
    ) = addRequest {
        OptionsBuilder(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun PATCH(
        path: String,
        configure: PatchBuilder.() -> Unit = {}
    ) = addRequest {
        PatchBuilder(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun DELETE(
        path: String,
        configure: DeleteBuilder.() -> Unit = {}
    ) = addRequest {
        DeleteBuilder(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun HEAD(
        path: String,
        configure: HeadBuilder.() -> Unit = {}
    ) = addRequest {
        HeadBuilder(path).apply(configure)
    }

    @Invokable
    @Suppress("FunctionName", "unused")
    fun TRACE(
        path: String,
        configure: TraceBuilder.() -> Unit = {}
    ) = addRequest {
        TraceBuilder(path).apply(configure)
    }

    @Invokable
    @Suppress("unused")
    fun request(
        method: String,
        path: String,
        configure: BaseRequestBuilder.() -> Unit = {}
    ) = addRequest {
        BaseRequestBuilder(method, path).apply(configure)
    }

    private fun <T : BaseRequestBuilder> addRequest(requestBuilderSupplier: () -> T): Thenable<T> {
        val connektRequest = ConnektRequest(
            connektContext,
            requestBuilderSupplier
        )
        return Thenable<T>(connektRequest)
    }

    operator fun <R> ConnektRequestHolder<R>.getValue(any: Any?, property: KProperty<*>): R {
        return when (this) {
            is Thenable<*> -> execute() as R
            is Terminal<*, *> -> execute() as R
        }
    }
}
