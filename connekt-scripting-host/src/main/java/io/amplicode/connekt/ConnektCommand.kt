/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import io.amplicode.connekt.dsl.ConnektBuilder
import org.mapdb.DBMaker
import java.io.File
import java.nio.file.Paths
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.host.FileScriptSource

internal class ConnektCommand : AbstractConnektCommand() {

    override fun run() {
        // DBMaker can't create file in non-existent folder
        // so ensure it exists
        globalEnvFile.parentFile.mkdirs()

        val db = DBMaker.fileDB(globalEnvFile)
            .closeOnJvmShutdown()
            .fileChannelEnable()
            .checksumHeaderBypass()
            .make()

        ConnektContext(
            db,
            createEnvStore(),
            VariablesStore(db)
        ).use { ctx ->
            val connektBuilder = ConnektBuilder(ctx)
            val evaluator = Evaluator(useCompilationCache)

            val res = evaluator.evalScript(
                connektBuilder,
                FileScriptSource(script),
                requestNumber?.minus(1)
            )

            res.returnValueAsError
                ?.error
                ?.printStackTrace()

            res.reports.forEach {
                it.exception?.printStackTrace()
                if (it.severity > ScriptDiagnostic.Severity.DEBUG) {
                    println(" : ${it.message}" + if (it.exception == null) "" else ": ${it.exception}")
                }
            }
        }
    }

    private fun createEnvStore(): EnvironmentStore {
        val envName = envName
        return if (envFile.exists() && !envName.isNullOrBlank())
            FileEnvironmentStore(envFile, envName) else
            NoOpEnvironmentStore
    }
}

abstract class AbstractConnektCommand : CliktCommand("Connekt") {
    val script by option(help = "Script file path")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)
        .required()

    val requestNumber by option(help = "Request number")
        .int()

    val envFile by option(help = "Environment file")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)
        .defaultLazy {
            script.parentFile.resolve("connekt.env.json")
        }

    val globalEnvFile by option(help = "Environment global file")
        .file(mustExist = false, canBeDir = false, mustBeReadable = true)
        .defaultLazy {
            globalEnvDefaultFile()
        }

    val envName by option(help = "Environment name")

    val useCompilationCache by option(help = "Use compilation cache")
        .boolean()
        .default(true)

    private fun globalEnvDefaultFile(): File {
        val userHome = System.getProperty("user.home")
        return Paths.get(userHome)
            .resolve(".connekt")
            .resolve("connekt-global-env.db")
            .toFile()
    }
}