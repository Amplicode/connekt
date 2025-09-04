@file:Suppress("FunctionName")

package io.amplicode.connekt.dsl

import io.amplicode.connekt.Request
import io.amplicode.connekt.RequestBuilderCall
import io.amplicode.connekt.RequestName
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
        @RequestName name: String? = null,
        configure: RequestBuilder.() -> Unit = {}
    ): R
}

@RequestBuilderCall
@Request("OPTIONS")
fun <R> RequestRegistrator<R>.OPTIONS(
    @RequestPath path: String,
    @RequestName name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("OPTIONS", path, name, configure)

@RequestBuilderCall
@Request("POST")
fun <R> RequestRegistrator<R>.POST(
    @RequestPath path: String,
    @RequestName name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("POST", path, name, configure)

@RequestBuilderCall
@Request("GET")
fun <R> RequestRegistrator<R>.GET(
    @RequestPath path: String,
    @RequestName name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("GET", path, name, configure)

@RequestBuilderCall
@Request("PUT")
fun <R> RequestRegistrator<R>.PUT(
    @RequestPath path: String,
    @RequestName name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("PUT", path, name, configure)

@RequestBuilderCall
@Request("PATCH")
fun <R> RequestRegistrator<R>.PATCH(
    @RequestPath path: String,
    @RequestName name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("PATCH", path, name, configure)

@RequestBuilderCall
@Request("DELETE")
fun <R> RequestRegistrator<R>.DELETE(
    @RequestPath path: String,
    @RequestName name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("DELETE", path, name, configure)

@RequestBuilderCall
@Request("HEAD")
fun <R> RequestRegistrator<R>.HEAD(
    @RequestPath path: String,
    @RequestName name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("HEAD", path, name, configure)

@RequestBuilderCall
@Request("TRACE")
fun <R> RequestRegistrator<R>.TRACE(
    @RequestPath path: String,
    @RequestName name: String? = null,
    configure: RequestBuilder.() -> Unit = {}
) = request("TRACE", path, name, configure)
