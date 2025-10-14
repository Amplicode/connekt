package io.amplicode.connekt.execution

import io.amplicode.connekt.ConnektBuilder
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.debugln
import io.amplicode.connekt.execution.ExecutionMode.COMPILE_ONLY
import io.amplicode.connekt.println
import java.lang.reflect.Modifier
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.util.isError
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.measureTime

class ConnektScript(
    private val context: ConnektContext,
    private val sourceCode: SourceCode
) {
    fun run(options: EvaluatorOptions): ResultWithDiagnostics<EvaluationResult> {
        val result: ResultWithDiagnostics<EvaluationResult>
        val executionDuration = measureTime {
            result = context.use {
                doRun(options)
            }
        }
        if (options.debugLog) {
            printExecutionTimeInfo(executionDuration, context)
        }
        return result
    }

    private fun doRun(options: EvaluatorOptions): ResultWithDiagnostics<EvaluationResult> {
        val (
            executionScenario,
            debugLog,
            compilationCache,
            executionMode
        ) = options
        if (debugLog) printOptions(options)
        val connektScriptingHost = ConnektScriptingHost(
            compilationCache,
            executionMode == COMPILE_ONLY,
        )
        val result = connektScriptingHost.evalScript(
            ConnektBuilder(context),
            sourceCode
        )
        handleErrors(result)
        when (executionMode) {
            COMPILE_ONLY -> {
                context.printer.println("The script was compiled without evaluation")
            }

            ExecutionMode.DEFAULT,
            ExecutionMode.CURL -> {
                context.executionContext.execute(executionScenario)
            }
        }
        return result
    }

    private fun handleErrors(result: ResultWithDiagnostics<EvaluationResult>) {
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
    }

    private fun printOptions(options: EvaluatorOptions) {
        options::class.java
            .declaredFields
            .asSequence()
            .filter { it.modifiers == Modifier.PUBLIC }
            .map { field ->
                field.name to field.get(options)
            }
            .map { (name, value) -> "$name = $value" }
            .forEach(context.printer::debugln)
    }

    private fun printExecutionTimeInfo(executionDuration: Duration, context: ConnektContext) {
        val durationString = executionDuration.toComponents { minutes, seconds, nanoseconds ->
            sequence {
                if (minutes > 0) {
                    yield("${minutes}m")
                }
                if (minutes > 0 || seconds > 0) {
                    yield("${seconds}s")
                }
                yield("${nanoseconds / 1_000_000}ms")
            }.joinToString(" ")
        }

        val durationMsString = executionDuration.inWholeMilliseconds
            .toString()
            .let { s ->
                if (s.length > 4) s.chunked(3).joinToString("_")
                else s
            }

        context.printer.debugln(
            "Executed in $durationString ($durationMsString ms)"
        )
    }
}