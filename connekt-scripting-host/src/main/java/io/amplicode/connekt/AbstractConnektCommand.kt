package io.amplicode.connekt

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.transformAll
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
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

    val version by option(help = "Connekt version")
        .flag(default = false)

    val executionMode by option(help = "Execution mode")
        .enum<ExecutionMode>()
        .default(ExecutionMode.DEFAULT)
        .validate { mode ->
            when (mode) {
                ExecutionMode.CURL -> require(requestNumber != null) {
                    "CURL execution mode is available for a single request execution only"
                }
                else -> {
                    // Do nothing
                }
            }
        }
}

enum class ExecutionMode {
    DEFAULT,
    CURL
}

private val connektHome = run {
    val userHome = System.getProperty("user.home")
    requireNotNull(userHome) {
        "System property 'user.home' must be set"
    }
    Paths.get(userHome).resolve(".connekt")
}
