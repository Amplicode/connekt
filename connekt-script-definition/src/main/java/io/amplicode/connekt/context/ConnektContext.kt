package io.amplicode.connekt.context

import io.amplicode.connekt.Printer
import io.amplicode.connekt.SystemOutPrinter
import org.mapdb.DB

class ConnektContext(
    private val db: DB,
    val env: EnvironmentStore,
    val vars: VariablesStore,
    val printer: Printer = SystemOutPrinter,
    val clientContext: ClientContext = ClientContextImpl(printer),
    val jsonContext: JsonContext = JsonContext(),
    val requestsContext: RequestsContext = RequestsContext(),
    val cookiesContext: CookiesContext = CookiesContext(db),
    val responseValuesContext: ResponseValuesContext = ResponseValuesContext(db),
) : AutoCloseable {

    override fun close() {
        try {
            clientContext.close()
            db.close()
        } catch (_: Exception) {
        }
    }
}