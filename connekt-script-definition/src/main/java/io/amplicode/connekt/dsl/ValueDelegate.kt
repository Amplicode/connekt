package io.amplicode.connekt.dsl

import kotlin.reflect.KProperty

interface ValueDelegate<T> {
    operator fun getValue(
        @Suppress("unused") thisRef: Nothing?,
        @Suppress("unused") property: KProperty<*>
    ): T

    operator fun getValue(
        @Suppress("unused") thisRef: Any?,
        @Suppress("unused") property: KProperty<*>
    ): T
}