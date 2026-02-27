package io.amplicode.connekt

import io.amplicode.connekt.context.*
import io.amplicode.connekt.context.execution.DeclarationCoordinates
import io.amplicode.connekt.context.execution.ExecutionScenario
import io.amplicode.connekt.context.persistence.InMemoryStorage
import io.amplicode.connekt.context.persistence.defaultStorage
import io.amplicode.connekt.daemon.*
import io.amplicode.connekt.execution.ConnektScriptingHost
import io.amplicode.connekt.execution.returnValueAsError
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintStream
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.notExists
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.jvm.util.isError
import kotlin.script.experimental.host.FileScriptSource
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val idleTimeoutSeconds = args
        .firstOrNull { it.startsWith("--idle-timeout=") }
        ?.removePrefix("--idle-timeout=")
        ?.toLongOrNull()
        ?: 300L

    val startTime = System.currentTimeMillis()

    // Track last compile activity for idle timeout
    val lastCompileActivityMs = AtomicLong(System.currentTimeMillis())

    // Wrap stdout in a buffered, UTF-8 PrintStream before anything else writes to it
    val stdout = PrintStream(System.out, true, Charsets.UTF_8)
    val writeMutex = Mutex()

    // Map of requestId -> Job for cancellable run operations
    val runJobs = ConcurrentHashMap<String, Job>()

    // Stop signal: completed when a shutdown request is received
    val stopSignal = CompletableDeferred<Unit>()

    // Connekt home directory for persistent storage/cookies
    val connektHome = Paths.get(System.getProperty("user.home")).resolve(".connekt")

    suspend fun writeLine(response: DaemonResponse) {
        val encoded = DaemonCodec.encode(response)
        writeMutex.withLock {
            stdout.println(encoded)
            stdout.flush()
        }
    }

    suspend fun handlePing(req: PingRequest) {
        writeLine(PongResponse(requestId = req.requestId, uptimeMs = System.currentTimeMillis() - startTime))
    }

    suspend fun handleShutdown(req: ShutdownRequest) {
        writeLine(ShutdownAckResponse(requestId = req.requestId))
        stopSignal.complete(Unit)
    }

    suspend fun handleCompile(req: CompileRequest) {
        val requestId = req.requestId
        val scriptFile = java.io.File(req.scriptPath)
        if (!scriptFile.exists()) {
            writeLine(ErrorResponse(requestId = requestId, code = "SCRIPT_NOT_FOUND", message = "Script not found: ${req.scriptPath}"))
            return
        }

        val startMs = System.currentTimeMillis()

        val result = withContext(Dispatchers.IO) {
            val host = ConnektScriptingHost(
                useCompilationCache = req.compilationCache ?: true,
                compileOnly = true,
                enablePowerAssert = true
            )
            val context = createConnektContext(
                InMemoryStorage(),
                NoopEnvironmentStore,
                NoopCookiesContext,
                ClientContextImpl(ConnektInterceptor(SystemOutPrinter, null)),
                SystemOutPrinter
            )
            context.use {
                host.evalScript(
                    context.connektBuilderFactory.createConnektBuilder(),
                    FileScriptSource(scriptFile)
                )
            }
        }

        val durationMs = System.currentTimeMillis() - startMs
        lastCompileActivityMs.set(System.currentTimeMillis())

        val stderrLines = StringBuilder()
        val stdoutLines = StringBuilder()

        result.reports.forEach { diagnostic ->
            if (diagnostic.severity > ScriptDiagnostic.Severity.DEBUG) {
                stderrLines.appendLine(diagnostic.render())
            }
        }

        result.returnValueAsError?.error?.let { error ->
            stderrLines.appendLine(error.toString())
        }

        val exitCode = if (result.isError()) 1 else 0

        writeLine(
            CompileResultResponse(
                requestId = requestId,
                exitCode = exitCode,
                stdout = stdoutLines.toString(),
                stderr = stderrLines.toString(),
                durationMs = durationMs,
                cached = false
            )
        )
    }

    suspend fun handleRun(req: RunRequest) {
        val requestId = req.requestId
        val scriptFile = java.io.File(req.scriptPath)
        if (!scriptFile.exists()) {
            writeLine(ErrorResponse(requestId = requestId, code = "SCRIPT_NOT_FOUND", message = "Script not found: ${req.scriptPath}"))
            return
        }

        // Custom printer that sends output chunks to the client
        val daemonPrinter = object : BaseNonColorPrinter() {
            override fun print(s: String) {
                // Schedule sending the output chunk; use runBlocking since print() is not suspend
                runBlocking {
                    writeLine(OutputChunkResponse(requestId = requestId, stream = "stdout", data = s))
                }
            }
        }

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Build execution scenario from request
                val executionScenario: ExecutionScenario = when {
                    req.requestNumber != null && req.requestNumber > 0 ->
                        ExecutionScenario.SingleExecution(DeclarationCoordinates(req.requestNumber - 1))
                    req.requestName != null ->
                        ExecutionScenario.SingleExecution(DeclarationCoordinates(req.requestName))
                    else -> ExecutionScenario.File
                }

                // Build environment store
                val envStore: EnvironmentStore = if (req.envName != null) {
                    val envFile = scriptFile.parentFile?.resolve("connekt.env.json")
                    if (envFile != null && envFile.exists()) {
                        CompoundEnvironmentStore(
                            listOf(
                                ValuesEnvironmentStore(emptyMap()),
                                FileEnvironmentStore(envFile, req.envName)
                            )
                        )
                    } else {
                        ValuesEnvironmentStore(emptyMap())
                    }
                } else {
                    ValuesEnvironmentStore(emptyMap())
                }

                // Set up persistent storage and cookies (using connekt home)
                val storageFile = connektHome.resolve("storage")
                storageFile.createParentDirectories()

                val cookiesFile = connektHome.resolve("cookies.db")
                if (cookiesFile.notExists()) {
                    cookiesFile.createFile()
                }

                val context = createConnektContext(
                    defaultStorage(storageFile),
                    envStore,
                    CookiesContextImpl(cookiesFile),
                    ClientContextImpl(ConnektInterceptor(daemonPrinter, null)),
                    daemonPrinter
                )

                val result = context.use {
                    val host = ConnektScriptingHost(
                        useCompilationCache = true,
                        compileOnly = false,
                        enablePowerAssert = true
                    )
                    val evalResult = host.evalScript(
                        context.connektBuilderFactory.createConnektBuilder(),
                        FileScriptSource(scriptFile)
                    )

                    // Report diagnostic errors as stderr chunks
                    evalResult.reports.forEach { diagnostic ->
                        if (diagnostic.severity > ScriptDiagnostic.Severity.DEBUG) {
                            writeLine(OutputChunkResponse(requestId = requestId, stream = "stderr", data = diagnostic.render() + "\n"))
                        }
                    }

                    evalResult.returnValueAsError?.error?.let { error ->
                        writeLine(OutputChunkResponse(requestId = requestId, stream = "stderr", data = error.toString() + "\n"))
                    }

                    if (!evalResult.isError()) {
                        context.executionContext.execute(executionScenario)
                    }

                    evalResult
                }

                val exitCode = if (result.isError()) 1 else 0
                writeLine(RunCompleteResponse(requestId = requestId, exitCode = exitCode))
            } catch (_: CancellationException) {
                writeLine(RunCompleteResponse(requestId = requestId, exitCode = -1))
            } catch (e: Exception) {
                writeLine(OutputChunkResponse(requestId = requestId, stream = "stderr", data = e.toString() + "\n"))
                writeLine(RunCompleteResponse(requestId = requestId, exitCode = 1))
            } finally {
                runJobs.remove(requestId)
            }
        }

        runJobs[requestId] = job
    }

    fun handleCancel(req: CancelRequest) {
        // Cancel the job — the job's finally block writes run_complete
        runJobs[req.requestId]?.cancel()
        // No direct response from handleCancel per spec
    }

    runBlocking {
        val scope = this

        // Read stdin in a separate coroutine
        val readerJob = launch(Dispatchers.IO) {
            val reader = BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8))
            try {
                var line = reader.readLine()
                while (line != null && isActive) {
                    when (val req = DaemonCodec.decode(line)) {
                        is CompileRequest -> launch { handleCompile(req) }
                        is RunRequest -> launch { handleRun(req) }
                        is CancelRequest -> handleCancel(req)
                        is PingRequest -> launch { handlePing(req) }
                        is ShutdownRequest -> launch { handleShutdown(req) }
                        null -> {
                            val requestId = DaemonCodec.decodeRequestId(line) ?: ""
                            val type = DaemonCodec.decodeType(line) ?: "<unparseable>"
                            launch {
                                writeLine(
                                    ErrorResponse(
                                        requestId = requestId,
                                        code = "UNKNOWN_MESSAGE_TYPE",
                                        message = "Unknown message type: '$type'"
                                    )
                                )
                            }
                        }
                    }
                    line = reader.readLine()
                }
            } catch (_: Exception) {
                // stdin closed or interrupted — fall through to cleanup
            }
        }

        // Watchdog coroutine: self-terminate after idleTimeoutSeconds with no compile activity
        launch {
            while (isActive) {
                delay(10_000L)
                val elapsed = System.currentTimeMillis() - lastCompileActivityMs.get()
                if (elapsed > idleTimeoutSeconds * 1000L) {
                    System.err.println("[connekt-daemon] idle timeout after ${idleTimeoutSeconds}s, shutting down")
                    exitProcess(0)
                }
            }
        }

        // Wait for either stdin EOF or a shutdown request
        select {
            readerJob.onJoin { }
            stopSignal.onAwait { }
        }

        // Cancel all active run jobs
        for ((_, job) in runJobs) {
            job.cancel()
        }

        // Wait up to 10 seconds for running jobs to complete
        withTimeoutOrNull(10_000L) {
            for ((_, job) in runJobs) {
                job.join()
            }
        }

        // Cancel the reader if still running (in case we got shutdown signal first)
        readerJob.cancel()

        // Cancel remaining coroutines in this scope
        scope.coroutineContext[Job]?.cancelChildren()

        exitProcess(0)
    }
}
