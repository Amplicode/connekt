/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt.execution

import io.amplicode.connekt.Connekt
import io.amplicode.connekt.connektVersion
import io.amplicode.connekt.dsl.ConnektBuilder
import java.io.File
import java.io.File.pathSeparator
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import kotlin.script.experimental.jvm.compilationCache
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

class ConnektScriptingHost(
    private val useCompilationCache: Boolean,
    private val compileOnly: Boolean,
    private val enablePowerAssert: Boolean,
    /**
     * A jvm target version used for script compilation.
     * This version also determines a minimal JDK version
     * that should be used to run the Evaluator.
     */
    private val jvmTarget: String = "1.8"
) {
    fun evalScript(
        connektBuilder: ConnektBuilder,
        scriptSourceCode: SourceCode
    ): ResultWithDiagnostics<EvaluationResult> {
        val scriptingHost = createScriptingHost()
        return scriptingHost.eval(
            scriptSourceCode,
            createJvmCompilationConfigurationFromTemplate<Connekt> {
                compilerOptions(buildCompilerOptions())
            },
            ScriptEvaluationConfiguration {
                implicitReceivers(connektBuilder)
            }
        )
    }

    private fun buildCompilerOptions() = buildList {
        addAll(
            "use-fast-jar-file-system",
            "false",
            "-Xadd-modules=ALL-MODULE-PATH",
            "-jvm-target=$jvmTarget"
        )
        if (enablePowerAssert) {
            addAll(
                "-Xplugin=${findPowerAssertJarInClasspath().absolutePath}",
                "-P",
                "plugin:org.jetbrains.kotlin.powerassert:function=kotlin.assert",
                "plugin:org.jetbrains.kotlin.powerassert:function=kotlin.require",
            )
        }
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

    private fun MutableList<String>.addAll(vararg values: String) = addAll(values)
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

private fun findPowerAssertJarInClasspath(): File = System.getProperty("java.class.path")
    .split(pathSeparator)
    .map(::File)
    .firstOrNull { it.name.startsWith("kotlin-power-assert-compiler-plugin-embeddable") && it.extension == "jar" }
    ?: error("Power-Assert plugin jar not found on classpath")