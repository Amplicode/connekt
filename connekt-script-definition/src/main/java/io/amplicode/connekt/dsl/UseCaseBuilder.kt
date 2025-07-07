package io.amplicode.connekt.dsl

import okhttp3.Response
import kotlin.reflect.KProperty

@ConnektDsl
interface UseCaseBuilder : RequestRegistrator<Response>, JsonPathExtensionsProvider {

    operator fun <T> T.provideDelegate(
        @Suppress("unused") receiver: Any?,
        @Suppress("unused") prop: KProperty<*>
    ): ValueDelegate<T>

    infix fun <T> Response.then(handle: Response.() -> T): T = run(handle)
}

