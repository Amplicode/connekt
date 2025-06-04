package io.amplicode.connekt.dsl

import okhttp3.Response
import kotlin.reflect.KProperty

@ConnektDsl
interface UseCaseBuilder : RequestRegistrator<Response>, JsonPathExtensionsProvider {

    operator fun <T> T.provideDelegate(
        @Suppress("unused") receiver: Any?,
        @Suppress("unused") prop: KProperty<*>
    ): UseCaseValueDelegate<T>

    infix fun <T> Response.then(handle: Response.() -> T): T = run(handle)
}

class UseCaseValueDelegate<T>(private val value: T) : ValueDelegate<T> {
    override operator fun getValue(
        @Suppress("unused") thisRef: Nothing?,
        @Suppress("unused") property: KProperty<*>
    ): T = getValueImpl()

    override operator fun getValue(
        @Suppress("unused") thisRef: Any?,
        @Suppress("unused") property: KProperty<*>
    ): T = getValueImpl()

    private fun getValueImpl(): T = value
}
