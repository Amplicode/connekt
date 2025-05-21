package io.amplicode.connekt

import com.fasterxml.jackson.databind.JsonNode
import com.jayway.jsonpath.ReadContext
import com.jayway.jsonpath.TypeRef
import io.amplicode.connekt.context.ClientConfigurer
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.context.DelegateProvider
import io.amplicode.connekt.dsl.ConnektBuilder
import io.amplicode.connekt.dsl.RequestBuilder
import io.amplicode.connekt.dsl.ValueDelegate
import io.amplicode.connekt.dsl.UseCaseBuilder
import okhttp3.Response
import kotlin.reflect.KProperty

fun ConnektBuilder(context: ConnektContext): ConnektBuilder =
    ConnektBuilderImpl(context)

internal class ConnektBuilderImpl(
    private val context: ConnektContext
) : ConnektBuilder {
    override val env = context.env
    override val vars = context.vars
    override fun <T> variable(): DelegateProvider<T> = vars.obj()

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

    override fun Response.jsonPath(): ReadContext {
        return context.jsonContext.getReadContext(this)
    }

    override fun <T> ReadContext.readList(path: String, clazz: Class<T>): List<T> {
        val nodes: List<JsonNode> = read(path, object : TypeRef<List<JsonNode>>() {})
        return nodes.map {
            context.jsonContext.objectMapper.treeToValue(it, clazz)
        }
    }

    override fun request(
        method: String,
        path: String,
        configure: RequestBuilder.() -> Unit
    ): RequestHolder {
        val requestBuilderProvider = RequestBuilderProvider {
            RequestBuilder(method, path, context).apply(configure)
        }
        val requestsContext = context.executionContext
        val requestHolder = RequestHolder(requestBuilderProvider, context)
        requestsContext.registerExecutable(requestHolder)
        return requestHolder
    }

    override operator fun <R> ConnektRequestExecutable<R>.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<R> {
        val storedValue = StoredValueImpl<R>(prop, this)
        return RequestValueDelegate(
            context,
            this,
            storedValue
        )
    }

    inner class StoredValueImpl<R>(
        prop: KProperty<*>,
        requestHolder: ConnektRequestExecutable<R>
    ) : RequestValueDelegate.StoredValue<R> {

        private val key = prop.name
        private val storeMap = context.persistenceStore
            .getMap("StoredValueImpl")

        override var value: R?
            get() = storeMap[key] as R?
            set(value) {
                storeMap[key] = value
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