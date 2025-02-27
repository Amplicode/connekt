/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import io.amplicode.connekt.dsl.BaseRequestBuilder
import okhttp3.Response

class ConnektRequest<T : BaseRequestBuilder>(
    internal val context: ConnektContext,
    private val requestBuilderSupplier: () -> T
) : Executable<Any?>() {

    internal var thenCallback: (Response.() -> Any?)? = null
        set(value) {
            assert(field == null) { "thenCallback already registered" }
            field = value
        }

    private val response by lazy {
        doRequest()
    }

    fun <R> then(thenCallback: Response.() -> R?) {
        this.thenCallback = thenCallback
    }

    fun initResponse() {
        // init lazy var
        response
    }

    private fun doRequest(): Response {
        val requestBuilder = requestBuilderSupplier()

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

        return response
    }

    override fun execute(): Any? {
        val response = response
        val result = thenCallback?.invoke(response)
        return result
    }
}