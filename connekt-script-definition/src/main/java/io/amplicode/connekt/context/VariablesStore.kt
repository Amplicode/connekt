/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt.context

import io.amplicode.connekt.context.persistence.Storage
import kotlin.reflect.KProperty
import kotlin.reflect.KType

class VariablesStore(val values: Storage) {
    fun string() = DelegateProvider<String>(values)
    fun int() = DelegateProvider<Int>(values)
    fun <T> obj() = DelegateProvider<T>(values)

    fun variable() = StoredVariableDelegate(values)
//    internal fun

    fun <T> setValue(name: String, value: T?) = values.setValue(name, value)
    fun <T> getValue(name: String, type: KType): T? = values.getValue(name, type)
}

class DelegateProvider<T>(private val values: Storage) {
    private var variable: Variable<T>? = null

    operator fun getValue(receiver: Any?, property: KProperty<*>): Variable<T> {
        if (variable != null) {
            return variable!!
        }
        variable = Variable<T>(values, property)

        return variable!!
    }
}

class Variable<T>(
    private val storage: Storage,
    private val property: KProperty<*>
) {
    fun get(): T? {
        return storage.getValue(property.name, property.getter.returnType) as T?
    }

    fun set(value: T) {
        storage.setValue(property.name, value)
    }

    override fun toString(): String = this.get().toString()
}

class StoredVariableDelegate(val storage: Storage) {

    inline operator fun <reified T> getValue(
        thisRef: Any,
        property: KProperty<*>
    ): T {
        return storage.getValue(property.name, property.getter.returnType)!!
    }

    inline operator fun <reified T> getValue(
        thisRef: Nothing?,
        property: KProperty<*>
    ): T {
        return storage.getValue(property.name, property.getter.returnType)!!
    }

    inline operator fun <reified T> setValue(
        thisRef: Any,
        property: KProperty<*>,
        value: T
    ) {
        storage.setValue(property.name, value)
    }

    inline operator fun <reified T> setValue(
        thisRef: Nothing?,
        property: KProperty<*>,
        value: T
    ) {
        storage.setValue(property.name, value)
    }
}
