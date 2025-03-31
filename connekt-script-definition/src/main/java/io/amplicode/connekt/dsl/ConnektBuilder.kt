/*
 * Copyright (c) Haulmont 2025. All Rights Reserved.
 * Use is subject to license terms.
 */

@file:Suppress("FunctionName")

package io.amplicode.connekt.dsl

import com.jayway.jsonpath.ReadContext
import io.amplicode.connekt.*
import io.amplicode.connekt.context.ClientConfigurer
import io.amplicode.connekt.context.DelegateProvider
import io.amplicode.connekt.context.EnvironmentStore
import io.amplicode.connekt.context.VariablesStore
import okhttp3.Response
import kotlin.reflect.KProperty

interface ConnektBuilder {
    val env: EnvironmentStore
    val vars: VariablesStore

    fun <T> variable(): DelegateProvider<T>
    fun configureClient(configure: ClientConfigurer)

    /**
     * A base function for building request
     * @param method an HTTP method
     * @param path request URL
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
        configure: RequestBuilder.() -> Unit = {}
    ): RequestHolder

    @RequestBuilderCall
    fun useCase(
        name: String? = null,
        build: UseCaseBuilder.() -> Unit = {}
    )

    fun Response.jsonPath(): ReadContext

    fun <T> ReadContext.readList(path: String, clazz: Class<T>): List<T>

    operator fun <R> ConnektRequestHolder<R>.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): RequestDelegate<R>
}

@RequestBuilderCall
@Request("OPTIONS")
fun ConnektBuilder.OPTIONS(
    @RequestPath path: String,
    configure: RequestBuilder.() -> Unit = {}
) = request("OPTIONS", path, configure)

@RequestBuilderCall
@Request("POST")
fun ConnektBuilder.POST(
    @RequestPath path: String,
    configure: RequestBuilder.() -> Unit = {}
) = request("POST", path, configure)

@RequestBuilderCall
@Request("GET")
fun ConnektBuilder.GET(
    @RequestPath path: String,
    configure: RequestBuilder.() -> Unit = {}
) = request("GET", path, configure)

@RequestBuilderCall
@Request("PUT")
fun ConnektBuilder.PUT(
    @RequestPath path: String,
    configure: RequestBuilder.() -> Unit = {}
) = request("PUT", path, configure)

@RequestBuilderCall
@Request("PATCH")
fun ConnektBuilder.PATCH(
    @RequestPath path: String,
    configure: RequestBuilder.() -> Unit = {}
) = request("PATCH", path, configure)

@RequestBuilderCall
@Request("DELETE")
fun ConnektBuilder.DELETE(
    @RequestPath path: String,
    configure: RequestBuilder.() -> Unit = {}
) = request("DELETE", path, configure)

@RequestBuilderCall
@Request("HEAD")
fun ConnektBuilder.HEAD(
    @RequestPath path: String,
    configure: RequestBuilder.() -> Unit = {}
) = request("HEAD", path, configure)

@RequestBuilderCall
@Request("TRACE")
fun ConnektBuilder.TRACE(
    @RequestPath path: String,
    configure: RequestBuilder.() -> Unit = {}
) = request("TRACE", path, configure)

interface RequestDelegate<T> {
    operator fun getValue(
        @Suppress("unused") thisRef: Nothing?,
        @Suppress("unused") property: KProperty<*>
    ): T

    operator fun getValue(
        @Suppress("unused") receiver: Any?,
        @Suppress("unused") prop: KProperty<*>
    ): T
}