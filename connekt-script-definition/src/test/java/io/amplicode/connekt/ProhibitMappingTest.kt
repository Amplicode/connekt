package io.amplicode.connekt

import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.dsl.POST
import io.amplicode.connekt.test.utils.components.testConnektContext
import io.amplicode.connekt.test.utils.runScript
import io.amplicode.connekt.test.utils.server.TestServer
import io.amplicode.connekt.test.utils.uuid
import kotlin.test.Test
import kotlin.test.assertTrue

class ProhibitMappingTest(testServer: TestServer) : TestWithServer(testServer) {

    @Test
    fun `curl execution strategy test`() {
        val context = testConnektContext()
        context.requestsContext.apply {
            registerExecutionStrategyForRequest(0, CurlExecutionStrategy(context))
            registerExecutionStrategyForRequest(2, CurlExecutionStrategy(context))
        }

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
}