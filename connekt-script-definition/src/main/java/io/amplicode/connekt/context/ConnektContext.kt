package io.amplicode.connekt.context

import io.amplicode.connekt.ConnektAuthExtensionsImpl
import io.amplicode.connekt.JsonExtensionsProviderImpl
import io.amplicode.connekt.Printer
import io.amplicode.connekt.context.execution.ExecutionContext
import io.amplicode.connekt.context.persistence.Storage
import io.amplicode.connekt.dsl.AuthExtensions
import io.amplicode.connekt.dsl.JsonPathExtensionsProvider
import kotlin.reflect.KClass

class ConnektContext(private val parent: ConnektContext? = null) : AutoCloseable {

    private val registry = LinkedHashMap<KClass<*>, Lazy<Any>>()

    fun <T : Any> register(type: KClass<T>, factory: ConnektContext.() -> T) {
        registry[type] = lazy { this.factory() }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: KClass<T>): T {
        val value = registry[type]?.value
        if (value != null) return value as T
        return parent?.get(type) ?: error("No provider registered for ${type.simpleName}")
    }

    /**
     * Creates a child context that delegates to this context for any service not explicitly
     * registered in the child. Useful for isolated execution scopes (e.g. imported scripts)
     * that need a fresh [ExecutionContext] but share everything else.
     */
    fun fork(configure: ConnektContext.() -> Unit = {}): ConnektContext =
        ConnektContext(parent = this).apply(configure)

    // ----- Typed convenience accessors -----

    val environmentStore get() = get(EnvironmentStore::class)
    val variablesStore get() = get(VariablesStore::class)
    val cookiesContext get() = get(CookiesContext::class)
    val clientContext get() = get(ClientContext::class)
    val printer get() = get(Printer::class)
    val jsonContext get() = get(JsonContext::class)
    val executionContext get() = get(ExecutionContext::class)
    val connektBuilderFactory get() = get(ConnektBuilderFactory::class)
    val jsonPathExtensionsProvider get() = get(JsonPathExtensionsProvider::class)
    val authExtensions get() = get(AuthExtensions::class)
    val storage get() = get(Storage::class)

    override fun close() {
        registry.values
            .filter { it.isInitialized() }
            .mapNotNull { it.value as? AutoCloseable }
            .forEach { component ->
                try {
                    component.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
    }
}

fun createConnektContext(
    storage: Storage,
    environmentStore: EnvironmentStore,
    cookiesContext: CookiesContext,
    clientContext: ClientContext,
    printer: Printer,
    builderFactory: (ConnektContext) -> ConnektBuilderFactory = ::ConnektBuilderFactoryImpl,
): ConnektContext = ConnektContext().apply {
    register(Storage::class) { storage }
    register(EnvironmentStore::class) { environmentStore }
    register(VariablesStore::class) { VariablesStore(storage) }
    register(CookiesContext::class) { cookiesContext }
    register(ClientContext::class) { clientContext }
    register(Printer::class) { printer }
    register(JsonContext::class) { JsonContext() }
    register(ExecutionContext::class) { ExecutionContext() }
    register(ConnektBuilderFactory::class) { builderFactory(this) }
    register(JsonPathExtensionsProvider::class) { JsonExtensionsProviderImpl(this) }
    register(AuthExtensions::class) { ConnektAuthExtensionsImpl(this) }
}
