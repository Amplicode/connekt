package io.amplicode.connekt

import kotlin.reflect.KProperty

class UseCaseValueDelegate<T>(private val value: T) : ValueDelegateBase<T>() {
    override fun getValueImpl(
        thisRef: Any?,
        property: KProperty<*>
    ) = value
}