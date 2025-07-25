package io.amplicode.connekt.context

import io.amplicode.connekt.Printer
import io.amplicode.connekt.SystemOutPrinter
import io.amplicode.connekt.context.persistence.Storage
import io.amplicode.connekt.debugln

class ConnektContext(
    val env: EnvironmentStore,
    val vars: VariablesStore,
    val cookiesContext: CookiesContext,
    val clientContext: ClientContext,
    val printer: Printer = SystemOutPrinter,
    val jsonContext: JsonContext = JsonContext(),
    val executionContext: ExecutionContext = ExecutionContext()
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
                }.onFailure {
                    it.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
    storage: Storage,
    environmentStore: EnvironmentStore,
    cookiesContext: CookiesContext,
    clientContext: ClientContext,
    printer: Printer,
): ConnektContext {
    return ConnektContext(
        environmentStore,
        VariablesStore(storage),
        cookiesContext,
        clientContext,
        printer,
        jsonContext = JsonContext(),
        executionContext = ExecutionContext()
    ).onClose {
        storage.close()
        storage.close()
    }
}
