package io.amplicode.connekt

import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.dsl.ConnektBuilder
import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.dsl.POST
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
        val context = testConnektContext()
            .curlRequest(0)
            .curlRequest(2)

        val counterId = uuid()
        var isGetCounterCalled = false
        runScript(context = context) {
            val counterPath = "$host/counter/$counterId"

            POST("$counterPath/inc")
            GET(counterPath) then {
                isGetCounterCalled = true
                // Check the real `/inc` request was not called
                assertTrue(body!!.string().toInt() == 0)
            }
            GET(counterPath)
        }

        assertTrue(
            isGetCounterCalled,
            "Expected 'getCounter' to be called, but it wasn't"
        )
    }

    @Test
    fun `test curl command text`() {
        val output = runSingleCurl {
            GET("$host/foo")
        }
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
        runSingleCurl {
            GET("$host/foo") then {
                fail("Then should not be called")
            }
        }
    }

    @Test
    fun `test curl command text for useCase`() {
        val output = runSingleCurl {
            useCase("my-use-case") {
                GET("$host/foo")
                GET("$host/bar")
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
        runSingleCurl {
            useCase {
                // noop
            }
        }
    }

    private fun ConnektContext.curlRequest(i: Int) = apply {
        val context = this
        requestsContext.apply {
            registerExecutionStrategyForRequest(i, CurlExecutionStrategy(context))
        }
    }

    private fun runSingleCurl(configureBuilder: ConnektBuilder.() -> Unit): String {
        val context = testConnektContext()
            .curlRequest(0)

        return runScript(context = context) {
            configureBuilder()
        }
    }
}