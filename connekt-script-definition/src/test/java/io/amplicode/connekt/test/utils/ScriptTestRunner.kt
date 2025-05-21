package io.amplicode.connekt.test.utils

import io.amplicode.connekt.ConnektBuilder
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.dsl.ConnektBuilder
import io.amplicode.connekt.test.utils.components.TestPrinter
import io.amplicode.connekt.test.utils.components.testConnektContext

/**
 * @param requestNumber request number starting from `0`
 */
fun runScript(
    requestNumber: Int? = null,
    context: ConnektContext = testConnektContext(),
    configureBuilder: ConnektBuilder.() -> Unit = { }
): String {
    val connektBuilder = ConnektBuilder(context)
    connektBuilder.configureBuilder()
    context.use {
        it.executionContext.execute(requestNumber)
    }
    return (context.printer as TestPrinter).stringPrinter.asString()
}