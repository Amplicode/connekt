package io.amplicode.connekt.test.utils

import io.amplicode.connekt.RequestExecutor
import io.amplicode.connekt.dsl.ConnektBuilder

/**
 * @param requestNumber request number starting from `0`
 */
fun runScript(
    requestNumber: Int? = null,
    connektBuilder: ConnektBuilder = createConnektBuilder(),
    configure: ConnektBuilder.() -> Unit = { }
) = connektBuilder.runScript(requestNumber, configure)

fun ConnektBuilder.runScript(
    requestNumber: Int? = null,
    configure: ConnektBuilder.() -> Unit = { }
): String {
    this.configure()
    RequestExecutor.execute(this, requestNumber)
    return (connektContext.printer as TestPrinter).stringPrinter.asString()
}