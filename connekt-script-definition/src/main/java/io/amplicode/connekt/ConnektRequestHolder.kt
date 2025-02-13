package io.amplicode.connekt

import io.amplicode.connekt.dsl.BaseRequestBuilder
import okhttp3.Response

sealed interface ConnektRequestHolder<T>

class Thenable<T : BaseRequestBuilder>(
    private val connektRequest: ConnektRequest<T>
) : ConnektRequestHolder<Unit> {

    @Suppress("unused")
    infix fun <R> then(assertCallback: Response.() -> R): Terminal<T, R> {
        connektRequest.thenCallback = assertCallback
        return Terminal(connektRequest)
    }

    internal fun execute() {
        connektRequest.execute()
    }
}

class Terminal<T : BaseRequestBuilder, R>(
    private val connektRequest: ConnektRequest<T>
) : ConnektRequestHolder<R> {

    internal fun execute(): R {
        return connektRequest.execute() as R
    }
}