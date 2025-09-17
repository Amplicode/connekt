package io.amplicode.connekt

import io.amplicode.connekt.context.ConnektContext
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.util.isError
import kotlin.system.exitProcess

data class EvaluatorOptions(
    val requestNumber: Int?,
    val compileOnly: Boolean,
    val debugLog: Boolean,
    val compilationCache: Boolean
) {
    fun allParams() = sequenceOf(
        "Request number" to requestNumber,
        "Compile only" to compileOnly,
        "With debug log" to debugLog,
        "Compilation cache" to compilationCache
    )

    fun isNegativeRequestNumber() =
        requestNumber?.let { it < 0 } == true
}

fun runScript(
    options: EvaluatorOptions,
    context: ConnektContext,
    scriptSourceCode: SourceCode,
): ResultWithDiagnostics<EvaluationResult> {
    val isCompileOnly = options.compileOnly || options.isNegativeRequestNumber()

    if (options.debugLog) {
        options.allParams()
            .map { (name, value) ->
                "$name: $value"
            }
            .forEach(context.printer::debugln)
    }

    val result = ConnektScriptingHost(
        options.compilationCache,
        isCompileOnly,
    ).evalScript(
        ConnektBuilder(context),
        scriptSourceCode
    )

    result.returnValueAsError
        ?.error
        ?.printStackTrace()

    result.reports.forEach { diagnostic ->
        if (diagnostic.severity > ScriptDiagnostic.Severity.DEBUG) {
            val message = diagnostic.render()
            System.err.println(message)
        }
    }

    if (result.isError()) {
        exitProcess(1)
    }

    when {
        isCompileOnly -> {
            val message = buildString {
                append("The script was compiled without evaluation")
                if (options.isNegativeRequestNumber()) {
                    append(" due to negative request number '${options.requestNumber}'")
                }
            }
            context.printer.println(message)
        }

        result.returnValueAsError == null && !result.isError() -> {
            context.executionContext.execute(options.requestNumber)
        }
    }

    return result
}