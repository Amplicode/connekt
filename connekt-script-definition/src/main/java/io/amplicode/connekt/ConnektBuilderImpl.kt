package io.amplicode.connekt

import io.amplicode.connekt.context.ClientConfigurer
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.context.StoredVariableDelegate
import io.amplicode.connekt.dsl.*
import kotlin.reflect.KProperty

fun ConnektBuilder(context: ConnektContext): ConnektBuilder =
    ConnektBuilderImpl(
        context,
        JsonExtensionsProviderImpl(context),
        ConnektAuthExtensionsImpl(context)
    )

internal class ConnektBuilderImpl(
    private val context: ConnektContext,
    private val jsonPathExtensions: JsonPathExtensionsProvider,
    private val authExtensionsImpl: ConnektAuthExtensionsImpl
) : ConnektBuilder,
    JsonPathExtensionsProvider by jsonPathExtensions,
    AuthExtensions by authExtensionsImpl {

    override val env = context.env
    override val vars = context.vars
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
        context.executionContext.registerExecutable(useCaseExecutable)
        return useCaseExecutable
    }

    override fun request(
        method: String,
        path: String,
        configure: RequestBuilder.() -> Unit
    ): RequestHolder {
        val requestBuilderProvider = RequestBuilderProvider {
            RequestBuilder(method, path, context).apply(configure)
        }
        val requestHolder = RequestHolder(requestBuilderProvider, context)
        context.executionContext.registerExecutable(requestHolder)
        return requestHolder
    }

    override operator fun <R> ExecutableWithResult<R>.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<R> {
        val storedValue = UpdatableStoredValue<R>(prop, this)
        return StoredValueDelegate(
            context,
            this,
            storedValue::value
        )
    }

    override operator fun <R> UseCaseExecutable<R>.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<R> {
        return UseCaseDelegate(prop, this)
    }

    inner class UpdatableStoredValue<R>(
        private val prop: KProperty<*>,
        requestHolder: ExecutableWithResult<R>
    ) {
        private val key = prop.name
        private val storage = context.vars

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
        private val storeMap = context.vars

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
        val executionStrategy = context.executionContext.getExecutionStrategy(this, context)
        val value = executionStrategy.executeUseCase(useCase)

        for (listener in listeners) {
            listener(value)
        }

        return value
    }
}
