package io.amplicode.connekt

import io.amplicode.connekt.context.ClientConfigurer
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.context.StoredVariableDelegate
import io.amplicode.connekt.context.execution.DeclarationCoordinates
import io.amplicode.connekt.context.execution.Executable
import io.amplicode.connekt.dsl.*
import kotlin.reflect.KProperty

internal class ConnektBuilderImpl(private val context: ConnektContext) :
    ConnektBuilder,
    JsonPathExtensionsProvider by context.jsonPathExtensionsProvider,
    AuthExtensions by context.authExtensions {

    override val env = context.environmentStore
    override val vars = context.variablesStore
    override fun variable(): StoredVariableDelegate = vars.variable()

    override fun configureClient(configure: ClientConfigurer) {
        context.clientContext.globalConfigurer = configure
    }

    override fun <T> useCase(name: String?, runUseCase: UseCaseBuilder.() -> T): UseCaseExecutable<T> {
        val useCase = object : UseCase<T> {
            override val name: String? = name
            override fun perform(useCaseBuilder: UseCaseBuilder) =
                useCaseBuilder.runUseCase()
        }
        val useCaseExecutable = UseCaseExecutable(context, useCase)
        context.executionContext.registerExecutable(useCaseExecutable, name)
        return useCaseExecutable
    }

    override fun request(
        method: String,
        path: String,
        name: String?,
        configure: RequestBuilder.() -> Unit
    ): RequestHolder {
        val requestBuilderProvider = RequestBuilderProvider {
            RequestBuilder(method, path, context).apply(configure)
        }
        val requestHolder = RequestHolder(requestBuilderProvider, context)
        context.executionContext.registerExecutable(requestHolder, name)
        return requestHolder
    }

    override operator fun <R> ExecutableWithResult<R>.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<R> {
        val executable = this
        // Register this declaration with delegating property name
        registerExecutableWithPropName(prop, executable.originalExecutable)
        val storedValue = UpdatableStoredValue(prop, executable)
        return StoredValueDelegate(
            context,
            executable,
            storedValue::value
        )
    }

    override operator fun <R> UseCaseExecutable<R>.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<R> {
        val executable = this
        // Register this declaration with delegating property name
        registerExecutableWithPropName(prop, executable)
        return UseCaseDelegate(prop, executable)
    }

    private fun registerExecutableWithPropName(prop: KProperty<*>, executable: Executable<*>) {
        val coordinates = DeclarationCoordinates(prop.name)
        context.executionContext.addCoordinatesForExecutable(coordinates, executable)
    }

    inner class UpdatableStoredValue<R>(
        private val prop: KProperty<*>,
        requestHolder: ExecutableWithResult<R>
    ) {
        private val key = prop.name
        private val storage = context.variablesStore

        var value: R?
            get() = storage.getValue(key, prop.returnType)
            set(value) {
                storage.setValue(key, value)
            }

        init {
            // update stored value on response received
            requestHolder.onResultObtained<R> {
                value = it
            }
        }
    }

    inner class UseCaseDelegate<R>(
        private val prop: KProperty<*>,
        private val executable: UseCaseExecutable<R>,
    ) : ValueDelegateBase<R>() {
        private val key = prop.name
        private val storeMap = context.variablesStore

        init {
            executable.addListener {
                storeMap.setValue(key, it)
            }
        }

        override fun getValueImpl(
            thisRef: Any?,
            property: KProperty<*>
        ): R {
            var value = storeMap.getValue<R>(key, prop.returnType)

            if (value == null) {
                value = executable.execute()
                storeMap.setValue(key, value)
            }

            return value!!
        }
    }

}

interface UseCase<T> {
    val name: String?
    fun perform(useCaseBuilder: UseCaseBuilder): T
}

class UseCaseExecutable<T>(
    private val context: ConnektContext,
    private val useCase: UseCase<T>
) : Executable<T>() {

    private val listeners: MutableList<(T) -> Unit> = mutableListOf()

    fun addListener(listener: (T) -> Unit) {
        listeners.add(listener)
    }

    override fun execute(): T {
        val executionStrategy = context.executionContext.getExecutionStrategy(this)
        val value = executionStrategy.executeUseCase(context, useCase)

        for (listener in listeners) {
            listener(value)
        }

        return value
    }
}
