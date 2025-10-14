package io.amplicode.connekt.execution

import io.amplicode.connekt.ConnektInterceptor
import io.amplicode.connekt.SystemOutPrinter
import io.amplicode.connekt.context.*
import io.amplicode.connekt.context.execution.ExecutionScenario
import io.amplicode.connekt.context.persistence.InMemoryStorage
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.isError
import kotlin.script.experimental.host.StringScriptSource
import kotlin.script.experimental.jvm.util.isError
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.fail

class EvaluatorTest {

    @Test
    fun `test using env var when there is no environment store`() {
        val result = evaluate(
            """
                val z: String by env

                GET("${'$'}z/hello") {

                }
            """.trimIndent(),
            ExecutionScenario.File
        )
        val error = result.returnValueAsError?.error
        assertIs<NoEnvironmentPropertyException>(error)

        assert(!result.isError()) {
            result.reports
                .filter { it.isError() }
                .joinToString(separator = "\n") {
                    it.message
                }
        }
    }

    @Test
    fun `test persistent variables type overwrite`() {
        evaluateThrowing(
            """
                val z by vars.string()
                z.set("uno")
                z.get()
            """.trimIndent()
        )

        evaluateThrowing(
            """
                val z by vars.int()
                z.set(1)
                val y = z.get()
            """.trimIndent()
        )
    }

    @Test
    fun `test N request evaluation`() {
        runWithServer { port ->
            evaluateThrowing(
                """
                GET("http://localhost:$port/foo") {

                }

                GET("http://localhost:$port/bar") {

                }
                """.trimIndent(),
                ExecutionScenario.Companion.SingleExecution(1)
            )
        }
    }

    @Test
    fun `test delegates`() {
        runWithServer { port ->
            evaluateThrowing(
                """
                val fooRequest by GET("http://localhost:$port/foo") {

                } then {
                    body!!.string()
                }

                GET("http://localhost:$port/bar") {
                    queryParam("my-param", fooRequest)
                }
                """.trimIndent(),
                ExecutionScenario.Companion.SingleExecution(1)
            )
        }
    }

    private fun runWithServer(block: (Int) -> Unit) {
        val server = embeddedServer(
            Netty,
            port = 0
        ) {
            routing {
                get("foo") {
                    call.respondText("foo")
                }
                get("bar") {
                    call.respondText("bar")
                }
            }
        }

        try {
            server.start(false)
            val port = runBlocking {
                server.engine.resolvedConnectors().first().port
            }
            block(port)
        } finally {
            server.stop()
        }
    }

    private fun evaluate(
        @Language("kotlin") scriptText: String,
        executionScenario: ExecutionScenario
    ): ResultWithDiagnostics<EvaluationResult> {
        val persistenceStore = InMemoryStorage()
        val printer = SystemOutPrinter
        val context = createConnektContext(
            persistenceStore,
            NoopEnvironmentStore,
            NoopCookiesContext,
            ClientContextImpl(ConnektInterceptor(printer, null)),
            printer
        )

        return ConnektScript(
            context,
            StringScriptSource(scriptText)
        ).run(
            EvaluatorOptions(
                executionScenario,
                debugLog = false,
                compilationCache = false,
                executionMode = ExecutionMode.DEFAULT,
                false
            )
        )
    }

    private fun evaluateThrowing(
        @Language("kotlin") scriptText: String,
        executionScenario: ExecutionScenario = ExecutionScenario.File
    ) {
        val result = evaluate(scriptText, executionScenario)
        result.assertNoCompileAndRuntimeErrors()
    }

    private fun ResultWithDiagnostics<EvaluationResult>.assertNoCompileAndRuntimeErrors() {
        val error = returnValueAsError?.error
        if (error != null) {
            fail("Result contains runtime error", error)
        }

        if (isError()) {
            val failCause = reports.filter { it.isError() }
                .joinToString("\n")
            fail(failCause)
        }
    }
}