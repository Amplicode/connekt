package io.amplicode.connekt.test.utils

import io.amplicode.connekt.ConnektBuilder
import io.amplicode.connekt.RequestExecutor
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.dsl.ConnektBuilder

/**
 * @param requestNumber request number starting from `0`
 */
fun runScript(
    requestNumber: Int? = null,
    context: ConnektContext = ConnektContext(),
    configureBuilder: ConnektBuilder.() -> Unit = { }
): String {
    val connektBuilder = ConnektBuilder(context)
    connektBuilder.configureBuilder()
    RequestExecutor.execute(context, requestNumber)
    return (context.printer as TestPrinter).stringPrinter.asString()
}

fun Any?.asUnit() = Unit