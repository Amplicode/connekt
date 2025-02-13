/*
 * Copyright (c) Haulmont 2025. All Rights Reserved.
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
import io.amplicode.connekt.console.println
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.reflect.KProperty

class ConnektBuilder(
    val connektContext: ConnektContext,
    val requests: MutableList<Executable<*>> = mutableListOf()
) {
    val env = connektContext.env
    val vars = connektContext.vars

    @Suppress("unused")
    fun <T> variable(): DelegateProvider<T> {
        return DelegateProvider(connektContext.values)
    }

    @RequestBuilderCall
    @Request("GET")
    @Suppress("FunctionName", "unused")
    fun GET(
        @RequestPath path: String,
        configure: GetBuilder.() -> Unit = {}
    ) = addRequest {
        GetBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("POST")
    @Suppress("FunctionName", "unused")
    fun POST(
        @RequestPath path: String,
        configure: PostBuilder.() -> Unit = {}
    ) = addRequest {
        PostBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("PUT")
    @Suppress("FunctionName", "unused")
    fun PUT(
        @RequestPath path: String,
        configure: PutBuilder.() -> Unit = {}
    ) = addRequest {
        PutBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("OPTIONS")
    @Suppress("FunctionName", "unused")
    fun OPTIONS(
        @RequestPath path: String,
        configure: OptionsBuilder.() -> Unit = {}
    ) = addRequest {
        OptionsBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("PATCH")
    @Suppress("FunctionName", "unused")
    fun PATCH(
        @RequestPath path: String,
        configure: PatchBuilder.() -> Unit = {}
    ) = addRequest {
        PatchBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("DELETE")
    @Suppress("FunctionName", "unused")
    fun DELETE(
        @RequestPath path: String,
        configure: DeleteBuilder.() -> Unit = {}
    ) = addRequest {
        DeleteBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("HEAD")
    @Suppress("FunctionName", "unused")
    fun HEAD(
        @RequestPath path: String,
        configure: HeadBuilder.() -> Unit = {}
    ) = addRequest {
        HeadBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("TRACE")
    @Suppress("FunctionName", "unused")
    fun TRACE(
        @RequestPath path: String,
        configure: TraceBuilder.() -> Unit = {}
    ) = addRequest {
        TraceBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Suppress("unused")
    fun request(
        method: String,
        @RequestPath path: String,
        configure: BaseRequestBuilder.() -> Unit = {}
    ) = addRequest {
        BaseRequestBuilder(method, path).apply(configure)
    }

    @RequestBuilderCall
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
        return connektContext.values.getOrPut(property.name) {
            connektContext.printer.println("Initializing value for property `${property.name}`")
            when (this) {
                is Thenable<*> -> execute()
                is Terminal<*, *> -> execute()
            }
        } as R
    }
}

class FlowBuilder(
    private val connektContext: ConnektContext
) {
    @RequestBuilderCall
    @Request("GET")
    @Suppress("FunctionName", "unused")
    fun GET(
        @RequestPath path: String,
        configure: GetBuilder.() -> Unit = {}
    ) = addRequest {
        GetBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("POST")
    @Suppress("FunctionName", "unused")
    fun POST(
        @RequestPath path: String,
        configure: PostBuilder.() -> Unit = {}
    ) = addRequest {
        PostBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("PUT")
    @Suppress("FunctionName", "unused")
    fun PUT(
        @RequestPath path: String,
        configure: PutBuilder.() -> Unit = {}
    ) = addRequest {
        PutBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("OPTIONS")
    @Suppress("FunctionName", "unused")
    fun OPTIONS(
        @RequestPath path: String,
        configure: OptionsBuilder.() -> Unit = {}
    ) = addRequest {
        OptionsBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("PATCH")
    @Suppress("FunctionName", "unused")
    fun PATCH(
        @RequestPath path: String,
        configure: PatchBuilder.() -> Unit = {}
    ) = addRequest {
        PatchBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("DELETE")
    @Suppress("FunctionName", "unused")
    fun DELETE(
        @RequestPath path: String,
        configure: DeleteBuilder.() -> Unit = {}
    ) = addRequest {
        DeleteBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("HEAD")
    @Suppress("FunctionName", "unused")
    fun HEAD(
        @RequestPath path: String,
        configure: HeadBuilder.() -> Unit = {}
    ) = addRequest {
        HeadBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Request("TRACE")
    @Suppress("FunctionName", "unused")
    fun TRACE(
        @RequestPath path: String,
        configure: TraceBuilder.() -> Unit = {}
    ) = addRequest {
        TraceBuilder(path).apply(configure)
    }

    @RequestBuilderCall
    @Suppress("unused")
    fun request(
        method: String,
        @RequestPath path: String,
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
