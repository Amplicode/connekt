package io.amplicode.connekt

import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.test.utils.server.TestServer
import io.amplicode.connekt.test.utils.runScript
import io.amplicode.connekt.test.utils.thenBodyString
import io.amplicode.connekt.test.utils.uuid
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals

class UseCaseTest(server: TestServer) : TestWithServer(server) {

    @Test
    fun `run var in flow twice`() {
        assertDoesNotThrow {
            runScript(0) {
                useCase("my-flow") {
                    val request = GET("$host/foo").then { body!!.string() }
                    request
                    request
                }
            }
        }
    }

    @Test
    fun `cyclic delegators in flow with no then { }`() {
        val counterKey = uuid()
        var counterResponse: Int? = null

        runScript {
            useCase("my-flow") {
                repeat(8) { i ->
                    val request = POST("$host/counter/{counter}/inc") {
                        pathParam("counter", counterKey)
                    }
                    @Suppress("UnusedVariable")
                    val prop by request
                }
            }
            GET("$host/counter/{counter}") {
                pathParam("counter", counterKey)
            }.then {
                counterResponse = body!!.string().toInt()
            }
        }

        assertEquals(counterResponse, 8)
    }

    @Test
    fun `cyclic delegators in flow with then { }`() {
        val counterKey = uuid()
        var counterResponse: Int? = null
        val executionTimes = 1

        runScript {
            useCase("my-flow") {
                repeat(executionTimes) { i ->
                    val request = incCounterRequest(counterKey)
                        .thenBodyString()
                    val prop by request
                    prop
                    prop
                    prop
                }
            }
            getCounterRequest(counterKey).thenBodyString {
                counterResponse = it.toInt()
            }
        }

        assertEquals(executionTimes, counterResponse)
    }

    @Test
    fun `cyclic requests in flow with no then { }`() {
        var finalCounterResponse: Int? = null

        runScript {
            val counterKey = uuid()
            useCase("my-flow") {
                repeat(5) { i ->
                    POST("$host/counter/{counter}/inc") {
                        pathParam("counter", counterKey)
                    }
                }
            }

            GET("$host/counter/{counter}") {
                pathParam("counter", counterKey)
            }.then {
                finalCounterResponse = body?.string()?.toInt()
            }
        }

        assertEquals(5, finalCounterResponse)
    }

    @Test
    fun `cyclic requests in flow with then { }`() {
        var finalCounterResponse: Int? = null

        runScript {
            val counterKey = uuid()
            useCase("my-flow") {
                repeat(5) { i ->
                    incCounterRequest(counterKey).then {
                        // just return some value
                        i
                    }
                }
            }

            getCounterRequest(counterKey).then {
                finalCounterResponse = body?.string()?.toInt()
            }
        }

        assertEquals(5, finalCounterResponse)
    }

    @Test
    fun `lazy vars in flow`() {
        val counterKey = uuid()

        val counterResults = mutableListOf<Int?>()
        runScript(0) {
            useCase("my-flow") {
                val prop by lazy {
                    incCounterRequest(counterKey)
                }

                getCounterRequest(counterKey, counterResults::add)

                // call to trigger request
                prop

                getCounterRequest(counterKey, counterResults::add)
            }
        }
    }

    @Test
    fun `test by variables are not cached in flow`() {
        runScript {
            useCase("my-flow") {
                val toSend = listOf(
                    "foo", "bar", "baz"
                )
                toSend.forEach { payload ->
                    val result by POST("$host/echo-body") {
                        body(payload)
                    }.then {
                        body!!.string()
                    }

                    assertEquals(payload, result)
                }
            }
        }
    }
}

