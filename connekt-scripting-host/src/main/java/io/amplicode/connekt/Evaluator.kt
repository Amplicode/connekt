/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import com.github.ajalt.clikt.core.main
import io.amplicode.connekt.dsl.ConnektBuilder
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.compilationCache
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.util.isError
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

class Evaluator(
    private val useCompilationCache: Boolean = true
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

    /**
     * @param requestNumber request number starting from `0`
     */
    fun evalScript(
        connektBuilder: ConnektBuilder,
        scriptSourceCode: SourceCode,
        requestNumber: Int?
    ): ResultWithDiagnostics<EvaluationResult> {
        val scriptingHost = createScriptingHost()
        val eval = scriptingHost.eval(
            scriptSourceCode,
            compilationConfiguration,
            ScriptEvaluationConfiguration {
                implicitReceivers(connektBuilder)
            }
        )

        if (eval.returnValueAsError != null || eval.isError()) {
            return eval
        }

        RequestExecutor.execute(connektBuilder, requestNumber)
        return eval
    }

    private fun createScriptingHost(): BasicJvmScriptingHost {
        val hostConfiguration = if (useCompilationCache) {
            ScriptingHostConfiguration {
                jvm.compilationCache(createCompiledScriptJarsCache())
            }
        } else {
            null
        }
        return BasicJvmScriptingHost(baseHostConfiguration = hostConfiguration)
    }

    private fun createCompiledScriptJarsCache() =
        CompiledScriptJarsCache { sourceCode, _ ->
            val evaluatorVersion = "0.0.2"

            val cacheKeyValues = sequenceOf(
                evaluatorVersion,
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

fun main(vararg args: String) = ConnektCommand().main(args)