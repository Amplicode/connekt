package io.amplicode.connekt.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import io.amplicode.connekt.context.execution.DeclarationCoordinates
import io.amplicode.connekt.context.execution.ExecutionScenario
import io.amplicode.connekt.execution.ExecutionMode
import java.nio.file.Path
import java.nio.file.Paths

abstract class AbstractConnektCommand : CliktCommand("Connekt") {
    val script by option(help = "Script file path")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)

    val requestNumber by option(
        help = "Number of request to be executed. " +
                "Requests are counted starting from 1. " +
                "If omitted, all requests will be executed sequentially. " +
                "If less than or equal to 0, the script will be compiled and executed, ignoring requests."
    ).int()

    val requestName by option(help = "Name of the request to be executed")

    val envFile by option(help = "Environment file")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)

    val storageFile by option(help = "Storage file")
        .path(mustExist = false, canBeDir = true, canBeFile = false, mustBeReadable = true)
        .default(connektHome.resolve("storage"))

    val cookiesFile by option(help = "Cookies file")
        .path(mustExist = false, canBeDir = false, mustBeReadable = true)
        .default(connektHome.resolve("cookies.db"))

    val responseDir by option(help = "Responses directory")
        .path(mustExist = false, canBeDir = true, canBeFile = false, mustBeReadable = true)
        .default(connektHome.resolve("response"))

    val envName by option(help = "Environment name")

    val envParams by option("--env-param")
        .transformAll { input ->
            input.map {
                val split = it.split("=", limit = 2)
                split[0] to split[1]
            }
        }

    val compilationCache by option(
        names = arrayOf("--compilation-cache", "-c"),
        help = "Use compilation cache"
    ).flag(
        secondaryNames = arrayOf("--no-compilation-cache"),
        default = true
    )

    val debugLog by option(
        names = arrayOf("--debug-log", "-d"),
        help = "Enable debug logging"
    ).flag(default = false)

    val compileOnly by option(
        names = arrayOf("--compile-only"),
        help = "Run in compile only mode. The script will be compiled but will not be evaluated"
    ).flag(default = false)
        .deprecated("Use execution mode 'COMPILE_ONLY' instead")

    val version by option(help = "Connekt version")
        .flag(default = false)

    val executionMode by option(help = "Execution mode")
        .enum<ExecutionMode>()
        .default(ExecutionMode.DEFAULT)
        .validate { mode ->
            when (mode) {
                ExecutionMode.CURL -> {
                    require(executionScenario is ExecutionScenario.SingleExecution) {
                        "CURL execution mode is available for a single request execution only"
                    }
                }

                else -> {
                    // Do nothing
                }
            }
        }
}

val AbstractConnektCommand.executionScenario: ExecutionScenario
    get() {
        requestNumber?.let { number ->
            if (number > 0) {
                return ExecutionScenario.SingleExecution(DeclarationCoordinates(number - 1))
            }
            return ExecutionScenario.File
        }

        requestName?.let { name ->
            return ExecutionScenario.SingleExecution(DeclarationCoordinates(name))
        }

        return ExecutionScenario.File
    }

val AbstractConnektCommand.effectiveExecutionMode
    get() = if (compileOnly) ExecutionMode.COMPILE_ONLY else executionMode

private val connektHome: Path by lazy {
    val userHome = System.getProperty("user.home")
    requireNotNull(userHome) {
        "System property 'user.home' must be set"
    }
    Paths.get(userHome).resolve(".connekt")
}