package io.amplicode.connekt

import io.amplicode.connekt.dsl.ConnektBuilder
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.mapdb.DBMaker
import java.io.File
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
            1
        )
        val error = result.returnValueAsError?.error
        assertIs<NoEnvironmentException>(error)

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
                1
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
                1
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
        requestNumber: Int? = null
    ): ResultWithDiagnostics<EvaluationResult> {
        val db = DBMaker.memoryDB().make()
        val connektContext = ConnektContext(
            db,
            NoOpEnvironmentStore,
            VariablesStore(db),
            storageFile = File("storage.json")
        )
        val connektBuilder = ConnektBuilder(connektContext)
        val evaluator = Evaluator(false)
        return connektContext.use {
            evaluator.evalScript(
                connektBuilder,
                StringScriptSource(scriptText),
                requestNumber
            )
        }
    }

    private fun evaluateThrowing(
        @Language("kotlin") scriptText: String,
        requestNumber: Int? = null
    ) {
        val result = evaluate(scriptText, requestNumber)
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
