/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import io.amplicode.connekt.dsl.BaseRequestBuilder
import okhttp3.Response

class ConnektRequest<T : BaseRequestBuilder>(
    internal val context: ConnektContext,
    private val requestConfigurableFactory: () -> T
) : Executable<Any?>() {
    private var executed: Boolean = false

    internal var thenCallback: (Response.() -> Any?)? = null
        set(value) {
            assert(field == null) { "thenCallback already registered" }
            field = value
        }
    internal var resultName: String? = null
        set(value) {
            assert(field == null) { "Request already registered" }
            field = value
        }

    @Suppress("unused")
    infix fun then(thenCallback: Response.() -> Any?): ConnektRequest<T> {
        this.thenCallback = thenCallback
        return this
    }

    override fun execute(): Any? {
        require(!executed) { "Request can be executed only once" }
        executed = true

        val requestBuilder = requestConfigurableFactory()

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

sealed interface ConnektRequestHolder<T>

class Thenable<T : BaseRequestBuilder>(
    private val connektRequest: ConnektRequest<T>
) : ConnektRequestHolder<Unit> {
    @Suppress("unused")
    infix fun <R> then(assertCallback: Response.() -> R): Terminal<T, R> {
        connektRequest.thenCallback = assertCallback
        return Terminal(connektRequest)
    }

    internal fun getOrExecuteWithName(name: String) {
        val value = connektRequest.context.values[name]
        if (value == null) {
            connektRequest.resultName = name
            return connektRequest.execute() as Unit
        }

        return value as Unit
    }

    internal fun execute() {
        connektRequest.execute()
    }
}

class Terminal<T : BaseRequestBuilder, R>(
    private val connektRequest: ConnektRequest<T>
) : ConnektRequestHolder<R> {
    internal fun getOrExecuteWithName(name: String): R {
        val value = connektRequest.context.values[name]
        if (value == null) {
            connektRequest.resultName = name
            return connektRequest.execute() as R
        }

        return value as R
    }

    internal fun execute(): R {
        return connektRequest.execute() as R
    }
}

object RequestExecutor {
    fun execute(executable: Executable<*>) {
        executable.execute()
    }
}
