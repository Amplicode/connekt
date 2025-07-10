package io.amplicode.connekt

import io.amplicode.connekt.context.ConnektContext
import kotlin.reflect.KProperty

/**
 * Represents a delegate that retrieves a value of type T when accessed. The value is either
 * retrieved from a stored state or initialized through execution logic.
 *
 * @param T The type of the value managed by the delegate.
 * @param connektContext Provides the application context, including environment details,
 * logging capabilities, client information, and other utilities.
 * @param executableWithResult Executes the logic to generate a value when it is not already stored.
 * @param storedValueProvider Holds a potentially precomputed value of type T or null if not initialized.
 */
class StoredValueDelegate<T>(
    private val connektContext: ConnektContext,
    private val executableWithResult: ExecutableWithResult<T>,
    private val storedValueProvider: () -> T?,
) : ValueDelegateBase<T>() {

    override fun getValueImpl(thisRef: Any?, property: KProperty<*>): T {
        storedValueProvider()?.let { return it }
        connektContext.printer.println("Initializing value for property `${property.name}`")
        return executableWithResult.execute()
    }
}