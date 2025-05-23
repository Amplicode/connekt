package io.amplicode.connekt.context

import io.amplicode.connekt.CallHandler
import io.amplicode.connekt.Printer
import io.amplicode.connekt.SystemOutPrinter
import org.mapdb.DB
import java.nio.file.Path
import kotlin.io.path.Path

class ConnektContext(
    val env: EnvironmentStore,
    val vars: VariablesStore,
    val cookiesContext: CookiesContext,
    val responseValuesContext: ResponseValuesContext,
    val clientContext: ClientContext,
    val printer: Printer = SystemOutPrinter,
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
    cookiesContext: CookiesContext,
    printer: Printer = SystemOutPrinter,
    responseStorageDir: Path? = defaultDownloadsDir,
): ConnektContext {
    val callHandler = CallHandler(printer, responseStorageDir)
    return ConnektContext(
        environmentStore,
        VariablesStore(db),
        cookiesContext,
        ResponseValuesContext(db),
        ClientContextImpl(callHandler),
        printer
    ).onClose {
        db.close()
    }
}

private val defaultDownloadsDir = Path(System.getProperty("user.home"))
    .resolve(".connekt")
    .resolve("response")