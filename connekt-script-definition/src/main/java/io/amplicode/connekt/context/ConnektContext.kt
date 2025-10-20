package io.amplicode.connekt.context

import io.amplicode.connekt.ConnektAuthExtensionsImpl
import io.amplicode.connekt.JsonExtensionsProviderImpl
import io.amplicode.connekt.Printer
import io.amplicode.connekt.context.execution.ExecutionContext
import io.amplicode.connekt.context.persistence.Storage
import io.amplicode.connekt.dsl.AuthExtensions
import io.amplicode.connekt.dsl.JsonPathExtensionsProvider

interface ConnektContext : AutoCloseable, ComponentProvider {
    val environmentStore: EnvironmentStore
    val variablesStore: VariablesStore
    val cookiesContext: CookiesContext
    val clientContext: ClientContext
    val printer: Printer
    val jsonContext: JsonContext
    val executionContext: ExecutionContext
    val connektBuilderFactory: ConnektBuilderFactory
    val jsonPathExtensionsProvider: JsonPathExtensionsProvider
    val authExtensions: AuthExtensions
}

class ConnektContextImpl(
    private val registry: ComponentRegistry = ComponentsRegistryImpl()
) : ConnektContext,
    AutoCloseable,
    ComponentRegistry by registry {

    override var environmentStore: EnvironmentStore by registry
    override var variablesStore: VariablesStore by registry
    override var cookiesContext: CookiesContext by registry
    override var clientContext: ClientContext by registry
    override var printer: Printer by registry
    override var jsonContext: JsonContext by registry
    override var executionContext: ExecutionContext by registry
    override var connektBuilderFactory: ConnektBuilderFactory by registry
    override var jsonPathExtensionsProvider: JsonPathExtensionsProvider by registry
    override var authExtensions: AuthExtensions by registry

    override fun close() {
        try {
            registry.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun createConnektContext(
    storage: Storage,
    environmentStore: EnvironmentStore,
    cookiesContext: CookiesContext,
    clientContext: ClientContext,
    printer: Printer,
): ConnektContextImpl {
    val contextImpl = ConnektContextImpl()
    contextImpl.apply {
        this.environmentStore = environmentStore
        this.variablesStore = VariablesStore(storage)
        this.cookiesContext = cookiesContext
        this.clientContext = clientContext
        this.printer = printer
        jsonContext = JsonContext()
        executionContext = ExecutionContext()
        connektBuilderFactory = ConnektBuilderFactoryImpl(contextImpl)
        jsonPathExtensionsProvider = JsonExtensionsProviderImpl(contextImpl)
        authExtensions = ConnektAuthExtensionsImpl(contextImpl)
    }
    return contextImpl
}
