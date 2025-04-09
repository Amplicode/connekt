package io.amplicode.connekt.context

import io.amplicode.connekt.Printer
import io.amplicode.connekt.SystemOutPrinter
import org.mapdb.DB

class ConnektContext(
    val env: EnvironmentStore,
    val vars: VariablesStore,
    val cookiesContext: CookiesContext,
    val responseValuesContext: ResponseValuesContext,
    val printer: Printer = SystemOutPrinter,
    val clientContext: ClientContext = ClientContextImpl(printer),
    val jsonContext: JsonContext = JsonContext(),
    val requestsContext: RequestsContext = RequestsContext(),
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
    db: DB,
    environmentStore: EnvironmentStore,
    printer: Printer = SystemOutPrinter,
): ConnektContext = ConnektContext(
    environmentStore,
    VariablesStore(db),
    CookiesContext(db),
    ResponseValuesContext(db),
    printer
).onClose {
    db.close()
}