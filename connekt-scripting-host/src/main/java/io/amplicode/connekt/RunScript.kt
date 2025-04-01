package io.amplicode.connekt

import io.amplicode.connekt.context.ConnektContext
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.util.isError

fun runScript(
    context: ConnektContext,
    scriptSourceCode: SourceCode,
    requestNumber: Int?,
    useCompilationCache: Boolean
): ResultWithDiagnostics<EvaluationResult> {
    val result = ConnektScriptingHost(
        useCompilationCache,
        requestNumber != null && requestNumber < 0,
    ).evalScript(
        ConnektBuilder(context),
        scriptSourceCode
    )

    if (result.returnValueAsError == null && !result.isError()) {
        RequestExecutor.execute(context, requestNumber)
    }

    result.returnValueAsError
        ?.error
        ?.printStackTrace()

    result.reports.forEach {
        it.exception?.printStackTrace()
        if (it.severity > ScriptDiagnostic.Severity.DEBUG) {
            println(" : ${it.message}" + if (it.exception == null) "" else ": ${it.exception}")
        }
    }

    return result
}