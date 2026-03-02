/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt.execution

import io.amplicode.connekt.Connekt
import io.amplicode.connekt.connektVersion
import io.amplicode.connekt.context.ConnektContext
import java.io.File
import java.io.File.pathSeparator
import java.security.MessageDigest
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.CompiledJvmScriptsCache
import kotlin.script.experimental.jvm.compilationCache
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvmhost.loadScriptFromJar
import kotlin.script.experimental.jvmhost.saveToJar

class ConnektScriptingHost(
    private val useCompilationCache: Boolean,
    private val enablePowerAssert: Boolean,
    /**
     * A jvm target version used for script compilation.
     * This version also determines a minimal JDK version
     * that should be used to run the Evaluator.
     */
    private val jvmTarget: String = "1.8"
) {
    fun compileScript(
        scriptSourceCode: SourceCode
    ): ResultWithDiagnostics<CompiledScript> {
        val scriptingHost = createScriptingHost()
        return scriptingHost.runInCoroutineContext {
            scriptingHost.compiler(
                scriptSourceCode,
                createJvmCompilationConfigurationFromTemplate<Connekt> {
                    compilerOptions(buildCompilerOptions())
                }
            )
        }
    }

    fun evalScript(
        context: ConnektContext,
        scriptSourceCode: SourceCode
    ): ResultWithDiagnostics<EvaluationResult> {
        val scriptingHost = createScriptingHost()

        return scriptingHost.eval(
            scriptSourceCode,
            createJvmCompilationConfigurationFromTemplate<Connekt> {
                compilerOptions(buildCompilerOptions())
            },
            ScriptEvaluationConfiguration {
                refineConfigurationBeforeEvaluate { refinementContext ->
                    val isRootScript = scriptSourceCode.locationId == refinementContext.compiledScript.sourceLocationId

                    refinementContext.evaluationConfiguration.with {
                        constructorArgs(
                            if (isRootScript) {
                                context.connektBuilderFactory.createConnektBuilder()
                            } else {
                                context.connektBuilderFactory.createForImportedScript()
                            }
                        )
                    }.asSuccess()
                }
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

        return BasicJvmScriptingHost(
            baseHostConfiguration = hostConfiguration,
        )
    }

    private fun createCompiledScriptJarsCache() =
        object : CompiledJvmScriptsCache {
            override fun get(
                script: SourceCode,
                scriptCompilationConfiguration: ScriptCompilationConfiguration
            ): CompiledScript? {
                val cacheFile = scriptToFile(script, scriptCompilationConfiguration)
                if (!cacheFile.exists()) return null

                if (!isCacheValid(cacheFile)) {
                    cacheFile.delete()
                    metaFile(cacheFile).delete()
                    return null
                }

                return cacheFile.loadScriptFromJar() ?: run {
                    cacheFile.delete()
                    null
                }
            }

            override fun store(
                compiledScript: CompiledScript,
                script: SourceCode,
                scriptCompilationConfiguration: ScriptCompilationConfiguration
            ) {
                val cacheFile = scriptToFile(script, scriptCompilationConfiguration)

                val jvmScript = compiledScript as? KJvmCompiledScript
                    ?: throw IllegalArgumentException("Unsupported script type ${compiledScript::class.java.name}")

                jvmScript.saveToJar(cacheFile)

                // Store imported scripts' timestamps so get() can detect changes later
                val importedFiles = collectAllOtherScripts(compiledScript)
                    .mapNotNull { it.sourceLocationId?.let(::File) }
                metaFile(cacheFile).writeText(
                    importedFiles.joinToString("\n") { "${it.path}|${it.lastModified()}" }
                )
            }

            private fun isCacheValid(cacheFile: File): Boolean {
                return metaFile(cacheFile).readLines()
                    .filter { it.isNotBlank() }
                    .all { line ->
                        val (path, timestamp) = line.split("|", limit = 2)
                        File(path).lastModified().toString() == timestamp
                    }
            }

            private fun metaFile(cacheFile: File) =
                cacheFile.resolveSibling("${cacheFile.nameWithoutExtension}.connekt.meta")

            private fun scriptToFile(
                script: SourceCode,
                scriptCompilationConfiguration: ScriptCompilationConfiguration
            ): File {
                val cacheName = sequenceOf(
                    connektVersion,
                    script.text,
                    script.locationId,
                    jvmTarget,
                    scriptCompilationConfiguration.notTransientData,
                ).joinToString().sha256()
                return File(System.getProperty("java.io.tmpdir"), "$cacheName.connekt.cache")
            }
        }

    private fun collectAllOtherScripts(
        script: CompiledScript,
        visited: MutableSet<String> = mutableSetOf()
    ): List<CompiledScript> =
        script.otherScripts
            .filter { visited.add(it.sourceLocationId ?: return@filter false) }
            .flatMap { listOf(it) + collectAllOtherScripts(it, visited) }

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(this.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun MutableList<String>.addAll(vararg values: String) = addAll(values)
}

val ResultWithDiagnostics<EvaluationResult>.returnValueAsError
    get() = valueOrNull()?.returnValue as? ResultValue.Error

private fun findPowerAssertJarInClasspath(): File = System.getProperty("java.class.path")
    .split(pathSeparator)
    .map(::File)
    .firstOrNull { it.name.startsWith("kotlin-power-assert-compiler-plugin-embeddable") && it.extension == "jar" }
    ?: error("Power-Assert plugin jar not found on classpath")
