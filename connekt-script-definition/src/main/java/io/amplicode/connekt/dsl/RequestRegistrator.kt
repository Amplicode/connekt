package io.amplicode.connekt.dsl

import io.amplicode.connekt.Request
import io.amplicode.connekt.RequestBuilderCall
import io.amplicode.connekt.RequestPath

interface RequestRegistrator<R> {
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
    ): R
}

@RequestBuilderCall
@Request("OPTIONS")
fun <R> RequestRegistrator<R>.OPTIONS(
    @RequestPath path: String,
    configure: RequestBuilder.() -> Unit = {}
) = request("OPTIONS", path, configure)

@RequestBuilderCall
@Request("POST")
fun <R> RequestRegistrator<R>.POST(
    @RequestPath path: String,
    configure: RequestBuilder.() -> Unit = {}
) = request("POST", path, configure)

@RequestBuilderCall
@Request("GET")
fun <R> RequestRegistrator<R>.GET(
    @RequestPath path: String,
    configure: RequestBuilder.() -> Unit = {}
) = request("GET", path, configure)

@RequestBuilderCall
@Request("PUT")
fun <R> RequestRegistrator<R>.PUT(
    @RequestPath path: String,
    configure: RequestBuilder.() -> Unit = {}
) = request("PUT", path, configure)

@RequestBuilderCall
@Request("PATCH")
fun <R> RequestRegistrator<R>.PATCH(
    @RequestPath path: String,
    configure: RequestBuilder.() -> Unit = {}
) = request("PATCH", path, configure)

@RequestBuilderCall
@Request("DELETE")
fun <R> RequestRegistrator<R>.DELETE(
    @RequestPath path: String,
    configure: RequestBuilder.() -> Unit = {}
) = request("DELETE", path, configure)

@RequestBuilderCall
@Request("HEAD")
fun <R> RequestRegistrator<R>.HEAD(
    @RequestPath path: String,
    configure: RequestBuilder.() -> Unit = {}
) = request("HEAD", path, configure)

@RequestBuilderCall
@Request("TRACE")
fun <R> RequestRegistrator<R>.TRACE(
    @RequestPath path: String,
    configure: RequestBuilder.() -> Unit = {}
) = request("TRACE", path, configure)