/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.context.EnvironmentStore
import io.amplicode.connekt.context.FileEnvironmentStore
import io.amplicode.connekt.context.NoOpEnvironmentStore
import io.amplicode.connekt.context.VariablesStore
import org.mapdb.DBMaker
import kotlin.script.experimental.host.FileScriptSource
import kotlin.time.Duration
import kotlin.time.measureTime

internal class ConnektCommand : AbstractConnektCommand() {

    override fun run() {
        val context = createContext()

        context.use { context ->
            val executionDuration = measureTime {
                runScript(
                    EvaluatorOptions(
                        requestNumber?.minus(1),
                        compileOnly,
                        debugLog,
                        compilationCache
                    ),
                    context,
                    FileScriptSource(script)
                )
            }
            if (debugLog) {
                printExecutionTimeInfo(executionDuration, context)
            }
        }
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

    private fun createContext(): ConnektContext {
        // DBMaker can't create file in non-existent folder
        // so ensure it exists
        globalEnvFile.parentFile.mkdirs()

        val db = DBMaker.fileDB(globalEnvFile)
            .closeOnJvmShutdown()
            .fileChannelEnable()
            .checksumHeaderBypass()
            .make()

        val context = ConnektContext(
            db,
            createEnvStore(),
            VariablesStore(db)
        )
        return context
    }

    private fun createEnvStore(): EnvironmentStore {
        val envName = envName
        return if (envFile.exists() && !envName.isNullOrBlank())
            FileEnvironmentStore(envFile, envName) else
            NoOpEnvironmentStore
    }
}