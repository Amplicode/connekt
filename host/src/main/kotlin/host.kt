package io.amplicode.host

import io.amplicode.connekt.*
import org.mapdb.DBMaker
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.compilationCache
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.CompiledScriptJarsCache
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

fun main(vararg args: String) {
    if (args.size != 2) {
        println("usage: <script file> <request_number>")
    } else {
        val scriptFile = File(args[0])
        val requestNumber = args.getOrNull(1)
            ?.let(String::toInt)
            ?.let { it - 1 }

        val res = evalFile(scriptFile, requestNumber)
        res.reports.forEach {
            it.exception?.printStackTrace()
            if (it.severity > ScriptDiagnostic.Severity.DEBUG) {
                println(" : ${it.message}" + if (it.exception == null) "" else ": ${it.exception}")
            }
        }
    }
}

private val compiledScriptJarsCache = CompiledScriptJarsCache({ sourceCode, _ ->
    File(
        System.getProperty("java.io.tmpdir"),
        "${sourceCode.text.hashCode()}_${sourceCode.locationId.hashCode()}.connekt.cache"
    )
})

fun evalFile(scriptFile: File, requestNumber: Int?): ResultWithDiagnostics<EvaluationResult> {
    val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<Connekt> {
        compilerOptions(listOf("use-fast-jar-file-system", "false"))
    }

    val requests = mutableListOf<Executable<*>>()
    val db = DBMaker.fileDB("env").make()


    db.use {
        val connektContext = ConnektContext(db)
        connektContext.use {
            val connektBuilder = ConnektBuilder(
                connektContext,
                requests = requests,
                env = FileEnvironmentStore(File("connekt.env.json"), "default")
            )

            val eval = BasicJvmScriptingHost(
                baseHostConfiguration = ScriptingHostConfiguration {
                    jvm.compilationCache(compiledScriptJarsCache)
                }
            ).eval(
                FileScriptSource(scriptFile),
                compilationConfiguration,
                ScriptEvaluationConfiguration {
                    implicitReceivers(connektBuilder)
                }
            )

            if (eval is ResultWithDiagnostics.Success) {
                if (requestNumber != null) {
                    RequestExecutor.execute(
                        requests[requestNumber]
                    )
                } else {
                    requests.forEach { RequestExecutor.execute(it) }
                }

            }
            return eval
        }
    }
}
