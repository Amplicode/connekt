package io.amplicode.connekt

import com.fasterxml.jackson.databind.JsonNode
import com.jayway.jsonpath.ReadContext
import com.jayway.jsonpath.TypeRef
import io.amplicode.connekt.context.ClientConfigurer
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.context.DelegateProvider
import io.amplicode.connekt.dsl.ConnektBuilder
import io.amplicode.connekt.dsl.RequestBuilder
import io.amplicode.connekt.dsl.RequestDelegate
import io.amplicode.connekt.dsl.UseCaseBuilder
import okhttp3.Response
import kotlin.reflect.KProperty

fun ConnektBuilder(context: ConnektContext): ConnektBuilder = ConnektBuilderImpl(context)

internal class ConnektBuilderImpl(
    private val context: ConnektContext
) : ConnektBuilder {
    override val env = context.env
    override val vars = context.vars

    override fun <T> variable(): DelegateProvider<T> {
        return DelegateProvider(context.responseValuesContext.values)
    }

    override fun configureClient(configure: ClientConfigurer) {
        context.clientContext.globalConfigurer = configure
    }

    @RequestBuilderCall
    override fun useCase(
        name: String?,
        build: UseCaseBuilder.() -> Unit
    ) {
        val useCaseBuilder = UseCaseBuilder(context)

        context.requestsContext.addRequest(
            object : Executable<Unit>() {
                override fun execute() {
                    context.printer.println("Running flow [${name}]")
                    useCaseBuilder.build()
                }
            }
        )
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
        val connektRequest = ConnektRequest(context) {
            RequestBuilder(method, path, context).apply(configure)
        }
        val thenable = RequestHolder(connektRequest)
        context.requestsContext.addRequest(thenable)
        return thenable
    }

    override operator fun <R> ConnektRequestHolder<R>.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): RequestDelegate<R> = PersistentRequestDelegate(
        context,
        this,
        prop.name
    )
}

class PersistentRequestDelegate<T>(
    private val connektContext: ConnektContext,
    private val requestHolder: ConnektRequestHolder<T>,
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