package io.amplicode.connekt

import io.amplicode.connekt.dsl.BaseRequestBuilder
import okhttp3.Response

sealed interface ConnektRequestHolder<T> {
    fun execute(): T
}

class Thenable<T : BaseRequestBuilder>(
    private val connektRequest: ConnektRequest<T>
) : ConnektRequestHolder<Unit> {

    infix fun <R> then(thenAction: Response.() -> R): Terminal<T, R> {
        connektRequest.then(thenAction)
        return Terminal(connektRequest)
    }

    override fun execute() {
        connektRequest.execute()
    }
}

class Terminal<T : BaseRequestBuilder, R>(
    private val connektRequest: ConnektRequest<T>
) : ConnektRequestHolder<R> {

    override fun execute(): R {
        return connektRequest.execute() as R
    }
}