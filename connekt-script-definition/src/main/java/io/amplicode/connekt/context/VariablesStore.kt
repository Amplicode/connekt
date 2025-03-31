/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt.context

import org.mapdb.DB
import org.mapdb.Serializer
import kotlin.reflect.KProperty

class VariablesStore(private val values: MutableMap<String, Any?>) {
    fun string() = DelegateProvider<String>(values)
    fun int() = DelegateProvider<Int>(values)

    fun <T> obj() = DelegateProvider<T>(values)
}

class DelegateProvider<T>(private val values: MutableMap<String, Any?>) {
    private var variable: Variable<T>? = null

    operator fun getValue(receiver: Any?, property: KProperty<*>): Variable<T> {
        if (variable != null) {
            return variable!!
        }
        variable = Variable<T>(values, property.name)

        return variable!!
    }
}

class Variable<T>(
    private val values: MutableMap<String, Any?>,
    private val name: String
) {
    fun get(): T {
        return values[name] as T
    }

    fun exists(): Boolean {
        return name in values
    }

    fun set(value: T) {
        values[name] = value
    }

    override fun toString(): String = if (this.exists())
        this.get().toString()
    else
        "undefined"
}

fun VariablesStore(db: DB): VariablesStore {
    val values: MutableMap<String, Any?> = db.hashMap(
        "values",
        Serializer.STRING,
        Serializer.JAVA
    ).createOrOpen()
    return VariablesStore(values)
}
