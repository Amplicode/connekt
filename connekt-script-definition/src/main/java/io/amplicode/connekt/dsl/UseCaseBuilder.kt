package io.amplicode.connekt.dsl

import okhttp3.Response
import kotlin.reflect.KProperty

@ConnektDsl
abstract class UseCaseBuilder : RequestRegistrator<Response>, JsonPathExtensionsProvider {
    abstract operator fun <T> T.provideDelegate(
        @Suppress("unused") receiver: Any?,
        @Suppress("unused") prop: KProperty<*>
    ): ValueDelegate<T>

    infix fun <T> Response.then(handle: Response.() -> T): T = run(handle)

    inline fun <reified T> Response.decode(path: String = "$"): T {
        return jsonPath().doRead<T>(path)
    }
}

