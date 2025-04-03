package io.amplicode.connekt

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.transformValues
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import java.io.File
import java.nio.file.Paths

abstract class AbstractConnektCommand : CliktCommand("Connekt") {
    val script by option(help = "Script file path")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)
        .required()

    val requestNumber by option(
        help = "Number of request to be executed. " +
                "Requests are counted starting from 1. " +
                "If omitted, all requests will be executed sequentially. " +
                "If less than or equal to 0, the script will be compiled and executed, ignoring requests."
    ).int()

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

    private fun globalEnvDefaultFile(): File {
        val userHome = System.getProperty("user.home")
        return Paths.get(userHome)
            .resolve(".connekt")
            .resolve("connekt-global-env.db")
            .toFile()
    }
}