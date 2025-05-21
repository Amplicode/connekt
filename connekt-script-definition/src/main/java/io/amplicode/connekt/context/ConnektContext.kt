package io.amplicode.connekt.context

import io.amplicode.connekt.Printer
import io.amplicode.connekt.SystemOutPrinter

class ConnektContext(
    val env: EnvironmentStore,
    val vars: VariablesStore,
    val cookiesContext: CookiesContext,
    val clientContext: ClientContext,
    val printer: Printer = SystemOutPrinter,
    val jsonContext: JsonContext = JsonContext(),
    val executionContext: ExecutionContext = ExecutionContext(),
    val persistenceStore: PersistenceStore = InMemoryPersistenceStore(),
) : AutoCloseable {

    private val listeners: MutableList<Listener> = mutableListOf()

    fun addListener(listener: Listener) {
        listeners += listener
    }

    override fun close() {
        try {
            clientContext.close()
            listeners.forEach {
                runCatching {
                    it.onClose()
                }
            }
        } catch (_: Exception) {

        }
    }

    interface Listener {
        fun onClose()
    }
}

fun ConnektContext.onClose(operation: () -> Unit): ConnektContext {
    addListener(object : ConnektContext.Listener {
        override fun onClose() = operation()
    })
    return this
}

fun createConnektContext(
    persistenceStore: PersistenceStore,
    environmentStore: EnvironmentStore,
    cookiesContext: CookiesContext,
    clientContext: ClientContext,
    printer: Printer,
): ConnektContext {
    return ConnektContext(
        environmentStore,
        VariablesStore(persistenceStore),
        cookiesContext,
        clientContext,
        printer,
        jsonContext = JsonContext(),
        executionContext = ExecutionContext(),
        persistenceStore = persistenceStore
    ).onClose {
        persistenceStore.close()
    }
}