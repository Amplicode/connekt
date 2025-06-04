package io.amplicode.connekt.dsl

import okhttp3.Response
import kotlin.reflect.KProperty

@ConnektDsl
interface UseCaseBuilder : RequestRegistrator<Response>, JsonPathExtensionsProvider {
    operator fun <T> T.provideDelegate(
        @Suppress("unused") receiver: Any?,
        @Suppress("unused") prop: KProperty<*>
    ): UseCaseRequestDelegate<T>

    infix fun <T> Response.then(handle: Response.() -> T): T = run(handle)
}

class UseCaseRequestDelegate<T>(private val value: T) : RequestDelegate<T> {
    override operator fun getValue(
        @Suppress("unused") thisRef: Nothing?,
        @Suppress("unused") property: KProperty<*>
    ): T {
        return getValueImpl()
    }

    override operator fun getValue(
        @Suppress("unused") receiver: Any?,
        @Suppress("unused") prop: KProperty<*>
    ): T {
        return getValueImpl()
    }

    private fun getValueImpl(): T = value
}
