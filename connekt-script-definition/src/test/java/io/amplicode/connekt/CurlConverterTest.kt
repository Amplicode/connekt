package io.amplicode.connekt

import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.context.execution.DeclarationCoordinates
import io.amplicode.connekt.context.execution.hasCoordinates
import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.dsl.POST
import io.amplicode.connekt.test.utils.ScriptStatement
import io.amplicode.connekt.test.utils.components.testConnektContext
import io.amplicode.connekt.test.utils.runScript
import io.amplicode.connekt.test.utils.server.TestServer
import io.amplicode.connekt.test.utils.uuid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class CurlConverterTest(testServer: TestServer) : TestWithServer(testServer) {

    @Test
    fun `test curl execution make no real request`() {
        val counterId = uuid()
        var isGetCounterCalled = false
        ScriptStatement {
            it.withCurlExecutionStrategy(0, 2)
        }.applyScript {
            val counterPath = "$host/counter/$counterId"
            POST("$counterPath/inc")
            GET(counterPath) then {
                isGetCounterCalled = true
                // Check the real `/inc` request was not called
                assertEquals(body!!.string().toInt(), 0)
            }
            GET(counterPath)
        }.evaluate()
        assertTrue(
            isGetCounterCalled,
            "Expected 'getCounter' to be called, but it wasn't"
        )
    }

    @Test
    fun `test curl command text`() {
        val output = ScriptStatement {
            it.withCurlExecutionStrategy(0)
        }.applyScript {
            GET("${host}/foo")
        }.evaluate()
        assertEquals(
            """
                |curl -X GET -H "User-Agent:connekt/0.0.1" "$host/foo"
                |
            """.trimMargin(),
            output
        )
    }

    @Test
    fun `test then is ignored`() {
        ScriptStatement {
            it.withCurlExecutionStrategy(0)
        }.applyScript {
            GET("${host}/foo") then {
                fail("Then should not be called")
            }
        }.evaluate()
    }

    @Test
    fun `test curl command text for useCase`() {
        val context = testConnektContext().withCurlExecutionStrategy(0)
        val output = runScript(context = context) {
            useCase("my-use-case") {
                GET("${host}/foo")
                GET("${host}/bar")
            }
        }

        assertEquals(
            """
                |curl -X GET -H "User-Agent:connekt/0.0.1" "$host/foo";
                |curl -X GET -H "User-Agent:connekt/0.0.1" "$host/bar";
                |
            """.trimMargin(),
            output
        )
    }

    @Test
    fun `test not fail with empty useCase`() {
        val context = testConnektContext().withCurlExecutionStrategy(0)
        // noop
        runScript(context = context) {
            // noop
            // noop
            useCase {
                // noop
            }
        }
    }

    private fun ConnektContext.withCurlExecutionStrategy(vararg requestNumbers: Int) = this.apply {
        val coordinates = requestNumbers.map(::DeclarationCoordinates).toTypedArray()
        executionContext.addRegistrationCustomizer { registration ->
            if (registration.hasCoordinates(*coordinates)) {
                registration.executionStrategy =
                    _root_ide_package_.io.amplicode.connekt.context.execution.CurlExecutionStrategy()
            }
            registration
        }
    }
}