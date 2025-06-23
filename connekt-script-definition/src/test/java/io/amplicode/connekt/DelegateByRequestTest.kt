package io.amplicode.connekt

import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.test.utils.components.TemporaryPersistenceStoreProvider
import io.amplicode.connekt.test.utils.components.testConnektContext
import io.amplicode.connekt.test.utils.runScript
import io.amplicode.connekt.test.utils.server.TestServer
import io.amplicode.connekt.test.utils.thenBodyInt
import io.amplicode.connekt.test.utils.thenBodyString
import io.amplicode.connekt.test.utils.uuid
import org.junit.jupiter.api.Test
import kotlin.test.Ignore
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class DelegateByRequestTest(server: TestServer) : TestWithServer(server) {

    @Test
    @Ignore("Delegates testing issue")
    fun `test caching of value delegated by request`() {
        val dbProvider = TemporaryPersistenceStoreProvider()

        // Run the script twice and make sure `counterResponse`
        // is not overwritten on the second run
        repeat(5) { timeNumber ->
            runScript(
                requestNumber = 1,
                context = testConnektContext(storage = dbProvider.getPersistenceStore())
            ) {
                val counterResponse by incCounterRequest("delegator-caching-test")
                    .thenBodyString()

                GET("$host/foo") {
                    // 1 means that the request above was called only once
                    assertEquals(
                        "1",
                        counterResponse,
                        "Request counter: $timeNumber"
                    )
                }
            }
        }
    }

    @Test
    @Ignore("Delegates testing issue")
    fun `check delegated variable overwrite`() {
        val counterKey = uuid()
        val dbProvider = TemporaryPersistenceStoreProvider()

        println("1st run")
        runScript(
            context = testConnektContext(
                storage = dbProvider.getPersistenceStore()
            )
        ) {
            // 1
            val request = incCounterRequest(counterKey).thenBodyInt()

            val counterVar by request

            assertEquals(1, counterVar)
            counterVar
            assertEquals(1, counterVar)
        }

        // Run a delegated request directly to trigger the variable update
        println("2nd run")
        runScript(
            requestNumber = 0,
            context = testConnektContext(storage = dbProvider.getPersistenceStore())
        ) {
            val counterVar by incCounterRequest(counterKey).thenBodyInt()
            // Stays 1 before execution fase
            assertEquals(1, counterVar, "Second run")
        }

        println("3d run")
        runScript(
            context = testConnektContext(storage = dbProvider.getPersistenceStore())
        ) {
            val counterVar by incCounterRequest(counterKey).thenBodyInt()
            // Should be updated to 2 due before execution fase
            assertEquals(2, counterVar, "Third run")
        }
    }

    @Test
    fun `test delegated var with then`() {
        runScript {
            val value by GET("$host/foo").then {
                body?.string()
            }

            assertEquals("foo", value)
        }
    }

    @Test
    fun `test run delegated request by number`() {
        val responses = ArrayDeque<String>()
        runScript(0) {
            @Suppress("UnusedVariable")
            val delegatedRequest by GET("$host/echo-text") {
                queryParam("text", 0)
            }.then {
                val bodyString = body!!.string()
                responses.addFirst(bodyString)
                bodyString
            }

            GET("$host/echo-text") {
                queryParam("text", 1)
            }.then {
                responses.addFirst(body!!.string())
            }
        }

        assertContentEquals(
            listOf("0"),
            responses.toList()
        )
    }
}