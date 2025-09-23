package io.amplicode.connekt.test.utils

import io.amplicode.connekt.ConnektBuilder
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.context.execution.ExecutionScenario
import io.amplicode.connekt.dsl.ConnektBuilder
import io.amplicode.connekt.test.utils.components.TestPrinter
import io.amplicode.connekt.test.utils.components.testConnektContext

/**
 * @param requestNumber request number starting from `0`
 */
fun runScript(
    requestNumber: Int? = null,
    context: ConnektContext = testConnektContext(),
    scriptBuilder: ConnektBuilder.() -> Unit = { }
): String {
    return ScriptStatement(context)
        .applyScript(scriptBuilder)
        .evaluate(requestNumber)
}

class ScriptStatement(val context: ConnektContext = testConnektContext()) {

    constructor(configureContext: (ConnektContext) -> Unit) : this() {
        applyToContext(configureContext)
    }

    private var isScriptApplied = false

    fun evaluate(requestNumber: Int? = null): String {
        context.use {
            val scenario = when {
                requestNumber == null || requestNumber < 0 -> ExecutionScenario.File
                else -> ExecutionScenario.SingleExecution(requestNumber)
            }
            it.executionContext.execute(scenario)
        }
        return (context.printer as TestPrinter).stringPrinter.asString()
    }

    /**
     * Applies the script provided in [buildScript] without execution
     */
    fun applyScript(buildScript: ConnektBuilder.() -> Unit): ScriptStatement {
        require(!isScriptApplied) {
            "Script is already applied"
        }
        ConnektBuilder(context).buildScript()
        isScriptApplied = true
        return this
    }

    fun applyToContext(configureContext: (ConnektContext) -> Unit): ScriptStatement {
        configureContext(context)
        return this
    }
}