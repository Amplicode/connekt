package io.amplicode.connekt

import io.amplicode.connekt.dsl.ValueDelegate
import kotlin.reflect.KProperty

abstract class ValueDelegateBase<T> : ValueDelegate<T> {
    final override operator fun getValue(
        @Suppress("unused") thisRef: Nothing?,
        @Suppress("unused") property: KProperty<*>
    ): T = getValueImpl(thisRef, property)

    final override operator fun getValue(
        @Suppress("unused") thisRef: Any?,
        @Suppress("unused") property: KProperty<*>
    ): T = getValueImpl(thisRef, property)

    protected abstract fun getValueImpl(thisRef: Any?, property: KProperty<*>): T
}