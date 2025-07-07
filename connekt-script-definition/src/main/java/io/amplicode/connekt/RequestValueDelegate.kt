package io.amplicode.connekt

import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.ValueDelegateBase
import kotlin.reflect.KProperty

/**
 * Represents a delegate that retrieves a value of type T when accessed. The value is either
 * retrieved from a stored state or initialized through execution logic.
 *
 * @param T The type of the value managed by the delegate.
 * @param connektContext Provides the application context, including environment details,
 * logging capabilities, client information, and other utilities.
 * @param requestHolder Executes the logic to generate a value when it is not already stored.
 * @param storedValue Holds a potentially precomputed value of type T or null if not initialized.
 */
class RequestValueDelegate<T>(
    private val connektContext: ConnektContext,
    private val requestHolder: ConnektRequestExecutable<T>,
    private val storedValue: StoredValue<T>,
) : ValueDelegateBase<T>() {

    override fun getValueImpl(thisRef: Any?, property: KProperty<*>): T {
        storedValue.value?.let { return it }
        connektContext.printer.println("Initializing value for property `${property.name}`")
        return requestHolder.execute()
    }

    interface StoredValue<T> {
        val value: T?
    }
}