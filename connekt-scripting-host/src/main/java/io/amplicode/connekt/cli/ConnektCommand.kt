package io.amplicode.connekt.cli

import com.github.ajalt.clikt.core.terminal
import io.amplicode.connekt.connektVersion
import io.amplicode.connekt.execution.ConnektScript
import io.amplicode.connekt.execution.EvaluatorOptions
import kotlin.script.experimental.host.FileScriptSource

internal class ConnektCommand : AbstractConnektCommand() {

    override fun run() {
        if (version) {
            terminal.println(connektVersion)
            return
        }
        val scriptFile = script ?: return
        val connektScript = ConnektScript(
            createConnektContext(this),
            FileScriptSource(scriptFile)
        )
        val options = EvaluatorOptions(
            executionScenario,
            debugLog,
            compilationCache,
            effectiveExecutionMode,
            kotlinPowerAssert
        )
        connektScript.run(options)
    }

    override val printHelpOnEmptyArgs: Boolean = true
}