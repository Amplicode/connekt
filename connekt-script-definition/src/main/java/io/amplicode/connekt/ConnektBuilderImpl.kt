package io.amplicode.connekt

import io.amplicode.connekt.context.ClientConfigurer
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.context.DelegateProvider
import io.amplicode.connekt.dsl.*
import kotlin.reflect.KProperty

fun ConnektBuilder(context: ConnektContext): ConnektBuilder =
    ConnektBuilderImpl(context, JsonExtensionsProviderImpl(context))

internal class ConnektBuilderImpl(
    private val context: ConnektContext,
    private val jsonPathExtensions: JsonPathExtensionsProvider
) : ConnektBuilder,
    JsonPathExtensionsProvider by jsonPathExtensions {

    override val env = context.env
    override val vars = context.vars

    override fun <T> variable(): DelegateProvider<T> {
        return DelegateProvider(context.responseValuesContext.values)
    }

    override fun configureClient(configure: ClientConfigurer) {
        context.clientContext.globalConfigurer = configure
    }

    override fun useCase(
        name: String?,
        runUseCase: UseCaseBuilder.() -> Unit
    ) {
        val useCase = object : UseCase {
            override val name: String? = name
            override fun perform(useCaseBuilder: UseCaseBuilder) =
                useCaseBuilder.runUseCase()
        }
        val useCaseExecutable = UseCaseExecutable(context, useCase)
        context.requestsContext.registerExecutable(useCaseExecutable)
    }

    override fun request(
        method: String,
        path: String,
        configure: RequestBuilder.() -> Unit
    ): RequestHolder {
        val requestBuilderProvider = RequestBuilderProvider {
            RequestBuilder(method, path, context).apply(configure)
        }
        val requestsContext = context.requestsContext
        val requestHolder = RequestHolder(requestBuilderProvider, context)
        requestsContext.registerExecutable(requestHolder)
        return requestHolder
    }

    override operator fun <R> ConnektRequestExecutable<R>.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): RequestDelegate<R> = PersistentRequestDelegate(
        context,
        this,
        prop.name
    )
}

private class UseCaseExecutable(
    private val context: ConnektContext,
    private val useCase: UseCase
) : Executable<Unit>() {
    override fun execute() {
        val executionStrategy = context.requestsContext.getExecutionStrategy(this, context)
        executionStrategy.executeUseCase(useCase)
    }
}

interface UseCase {
    val name: String?
    fun perform(useCaseBuilder: UseCaseBuilder)
}

class PersistentRequestDelegate<T>(
    private val connektContext: ConnektContext,
    private val requestHolder: ConnektRequestExecutable<T>,
    private val storageKey: String
) : RequestDelegate<T> {

    init {
        requestHolder.onResultObtained {
            connektContext.responseValuesContext.values[storageKey] = it
        }
    }

    override operator fun getValue(
        @Suppress("unused") thisRef: Nothing?,
        @Suppress("unused") property: KProperty<*>
    ): T {
        return getValueImpl()
    }

    override operator fun getValue(
        @Suppress("unused") receiver: Any?,
        @Suppress("unused") prop: KProperty<*>
    ): T {
        return getValueImpl()
    }

    private fun getValueImpl(): T {
        val storedValue = connektContext.responseValuesContext.values[storageKey]
        if (storedValue == null) {
            connektContext.printer.println("Initializing value for property `$storageKey`")
            return requestHolder.execute()
        }

        return connektContext.responseValuesContext.values[storageKey] as T
    }
}