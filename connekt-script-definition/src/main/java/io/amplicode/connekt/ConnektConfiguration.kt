/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import io.amplicode.connekt.dsl.ConnektBuilder
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.dependencies.*
import kotlin.script.experimental.dependencies.maven.MavenDependenciesResolver
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

object ConnektConfiguration : ScriptCompilationConfiguration({
    defaultImports(
        DependsOn::class, Random::class
    )

    defaultImports(
        "kotlin.script.experimental.dependencies.DependsOn",
        "kotlin.random.Random",
        "org.apache.http.HttpHeaders",
        "io.amplicode.connekt.dsl.*",
        "io.amplicode.connekt.Import"
    )

    displayName(
        "Connekt"
    )

    jvm {
        dependenciesFromCurrentContext(wholeClasspath = true)
    }

    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }

    refineConfiguration {
        onAnnotations(
            DependsOn::class,
            Repository::class,
            handler = ::configureMavenDepsOnAnnotations
        )
        onAnnotations(Import::class, handler = ::configureImportsOnAnnotation)
    }
}) {
    @Suppress("unused")
    private fun readResolve(): Any = HttpScriptEvaluationConfiguration
}

object HttpScriptEvaluationConfiguration : ScriptEvaluationConfiguration({
    scriptsInstancesSharing(false)
    constructorArgs()
}) {
    @Suppress("unused")
    private fun readResolve(): Any = HttpScriptEvaluationConfiguration
}

fun configureMavenDepsOnAnnotations(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
    val annotations = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)?.takeIf { it.isNotEmpty() }
        ?: return context.compilationConfiguration.asSuccess()

    return runBlocking {
        resolver.resolveFromScriptSourceAnnotations(annotations)
    }.onSuccess {
        context.compilationConfiguration.with {
            dependencies.append(JvmDependency(it))
        }.asSuccess()
    }
}

fun configureImportsOnAnnotation(
    context: ScriptConfigurationRefinementContext
): ResultWithDiagnostics<ScriptCompilationConfiguration> {
    val annotations = context.collectedData
        ?.get(ScriptCollectedData.collectedAnnotations)
        ?.map { it.annotation }
        ?.filterIsInstance<Import>()
        ?: return context.compilationConfiguration.asSuccess()

    val scriptBaseDir = (context.script as? FileScriptSource)?.file?.parentFile
        ?: context.script.locationId?.let { File(it) }?.let { if (it.isFile) it.parentFile else it }

    val importedSources = annotations.flatMap { importAnnotation ->
        importAnnotation.paths.map { path ->
            val file = if (File(path).isAbsolute) File(path)
            else scriptBaseDir?.resolve(path) ?: File(path)
            FileScriptSource(file)
        }
    }

    return if (importedSources.isEmpty()) {
        context.compilationConfiguration.asSuccess()
    } else {
        ScriptCompilationConfiguration(context.compilationConfiguration) {
            importScripts.append(importedSources)
        }.asSuccess()
    }
}

private val resolver = CompoundDependenciesResolver(
    FileSystemDependenciesResolver(),
    MavenDependenciesResolver()
)


