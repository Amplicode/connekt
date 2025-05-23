/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import io.amplicode.connekt.dsl.ConnektBuilder
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import kotlin.script.experimental.jvm.compilationCache
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

class ConnektScriptingHost(
    private val useCompilationCache: Boolean = true,
    private val compileOnly: Boolean = false,
) {
    // A jvm target version used for script compilation.
    // This version also determines minimal JDK version
    // that should be used to run the Evaluator.
    private val jvmTarget = "1.8"

    private val compilationConfiguration =
        createJvmCompilationConfigurationFromTemplate<Connekt> {
            compilerOptions(
                listOf(
                    "use-fast-jar-file-system",
                    "false",
                    "-Xadd-modules=ALL-MODULE-PATH",
                    "-jvm-target=$jvmTarget"
                )
            )
        }

    fun evalScript(
        connektBuilder: ConnektBuilder,
        scriptSourceCode: SourceCode
    ): ResultWithDiagnostics<EvaluationResult> {
        val scriptingHost = createScriptingHost()
        return scriptingHost.eval(
            scriptSourceCode,
            compilationConfiguration,
            ScriptEvaluationConfiguration {
                implicitReceivers(connektBuilder)
            }
        )
    }

    private fun createScriptingHost(): BasicJvmScriptingHost {
        val hostConfiguration = if (useCompilationCache) {
            ScriptingHostConfiguration {
                jvm.compilationCache(createCompiledScriptJarsCache())
            }
        } else {
            null
        }

        val evaluator = if (compileOnly) {
            NoopScriptEvaluator()
        } else {
            BasicJvmScriptEvaluator()
        }

        return BasicJvmScriptingHost(
            baseHostConfiguration = hostConfiguration,
            evaluator = evaluator,
        )
    }

    private fun createCompiledScriptJarsCache() =
        CompiledScriptJarsCache { sourceCode, _ ->
            val cacheKeyValues = sequenceOf(
                connektVersion,
                sourceCode.text.hashCode(),
                sourceCode.locationId.hashCode(),
                jvmTarget.replace('.', '-')
            )
            val cacheFileName = cacheKeyValues.joinToString(
                separator = "_",
                postfix = ".connekt.cache"
            )
            File(
                System.getProperty("java.io.tmpdir"),
                cacheFileName
            )
        }
}

val ResultWithDiagnostics<EvaluationResult>.returnValueAsError
    get() = valueOrNull()?.returnValue as? ResultValue.Error

private class NoopScriptEvaluator(
    private val evaluationConfiguration: ScriptEvaluationConfiguration? = null
) : ScriptEvaluator {

    override suspend fun invoke(
        compiledScript: CompiledScript,
        scriptEvaluationConfiguration: ScriptEvaluationConfiguration
    ): ResultWithDiagnostics<EvaluationResult> {
        return ResultWithDiagnostics.Success(
            EvaluationResult(
                ResultValue.NotEvaluated,
                evaluationConfiguration
            )
        )
    }
}