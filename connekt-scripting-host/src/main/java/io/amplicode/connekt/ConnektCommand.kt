/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import com.github.ajalt.clikt.core.terminal
import io.amplicode.connekt.context.ConnektContext
import kotlin.script.experimental.host.FileScriptSource
import kotlin.time.Duration
import kotlin.time.measureTime

internal class ConnektCommand : AbstractConnektCommand() {

    override fun run() {
        if (version) {
            terminal.println(connektVersion)
            return
        }

        val script = script
        if (script != null) {
            val context = createContextFactory().createContext(this)
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
    }

    override val printHelpOnEmptyArgs: Boolean = true

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

    private fun createContextFactory(): ConnektContextFactory {
        return if (!compileOnly)
            DefaultContextFactory() else
            CompileOnlyContextFactory()
    }
}