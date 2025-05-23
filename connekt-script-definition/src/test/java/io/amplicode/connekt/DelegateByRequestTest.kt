package io.amplicode.connekt

import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.test.utils.components.TempFileDbProvider
import io.amplicode.connekt.test.utils.components.testConnektContext
import io.amplicode.connekt.test.utils.runScript
import io.amplicode.connekt.test.utils.server.TestServer
import io.amplicode.connekt.test.utils.thenBodyInt
import io.amplicode.connekt.test.utils.thenBodyString
import io.amplicode.connekt.test.utils.uuid
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class DelegateByRequestTest(server: TestServer) : TestWithServer(server) {
    @Test
    fun testDelegatedPropertiesRequest() {
        val output = runScript(1) {
            val fooRequest by GET("$host/foo") then {
                body!!.string()
            }

            GET("$host/bar") {
                header("param-from-foo-request", fooRequest)
            }
        }

        val hostWithoutProtocol = host.removePrefix("http://")
        assertEquals(
            """
            Initializing value for property `fooRequest`
            GET $host/foo
            User-Agent: connekt/0.0.1 
            Host: $hostWithoutProtocol 
            Connection: Keep-Alive 
            Accept-Encoding: gzip

            HTTP/1.1 200 OK
            Content-Length: 3 
            Content-Type: text/plain; charset=UTF-8 
            Connection: keep-alive

            foo
            GET $host/bar
            param-from-foo-request: foo 
            User-Agent: connekt/0.0.1 
            Host: $hostWithoutProtocol 
            Connection: Keep-Alive 
            Accept-Encoding: gzip

            HTTP/1.1 200 OK
            Content-Length: 3 
            Content-Type: text/plain; charset=UTF-8 
            Connection: keep-alive

            bar
            
        """.trimIndent(),
            output
        )
    }

    @Test
    fun `test caching of value delegated by request`() {
        val dbProvider = TempFileDbProvider()

        // Run the script twice and make sure `counterResponse`
        // is not overwritten on second run
        repeat(5) { timeNumber ->
            runScript(
                requestNumber = 1,
                context = testConnektContext(db = dbProvider.getDb())
            ) {
                val counterResponse by incCounterRequest("delegator-caching-test")
                    .thenBodyString()

                GET("$host/foo") {
                    // 1 means that the request above was called only once
                    assertEquals("1", counterResponse)
                }
            }
        }
    }

    @Test
    fun `check delegated variable overwrite`() {
        val counterKey = uuid()
        val dbProvider = TempFileDbProvider()

        println("1st run")
        runScript(
            context = testConnektContext(dbProvider.getDb())
        ) {
            // 1
            val request = incCounterRequest(counterKey).thenBodyInt()

            val counterVar by request

            assertEquals(1, counterVar)
            counterVar
            assertEquals(1, counterVar)
        }

        // Run delegated request directly to trigger the variable update
        println("2nd run")
        runScript(
            requestNumber = 0,
            context = testConnektContext(dbProvider.getDb())
        ) {
            val counterVar by incCounterRequest(counterKey).thenBodyInt()
            // Stays 1 before execution fase
            assertEquals(1, counterVar, "Second run")
        }

        println("3d run")
        runScript(
            context = testConnektContext(dbProvider.getDb())
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