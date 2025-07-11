package io.amplicode.connekt

import io.amplicode.connekt.auth.KeycloakOAuthParameters
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

    override fun useCase(name: String?, runUseCase: UseCaseBuilder.() -> Unit) {
        val useCase = object : UseCase {
            override val name: String? = name
            override fun perform(useCaseBuilder: UseCaseBuilder) =
                useCaseBuilder.runUseCase()
        }
        val useCaseExecutable = UseCaseExecutable(context, useCase)
        context.executionContext.registerExecutable(useCaseExecutable)
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
}

private class UseCaseExecutable(
    private val context: ConnektContext,
    private val useCase: UseCase
) : Executable<Unit>() {
    override fun execute() {
        val executionStrategy = context.executionContext.getExecutionStrategy(this, context)
        executionStrategy.executeUseCase(useCase)
    }
}

interface UseCase {
    val name: String?
    fun perform(useCaseBuilder: UseCaseBuilder)
}

internal val KeycloakOAuthParameters.tokenUrl: String
    get() = "$serverBaseUrl/realms/$realm/protocol/$protocol/token"

interface TokenProvider {
    fun getToken(): String
}