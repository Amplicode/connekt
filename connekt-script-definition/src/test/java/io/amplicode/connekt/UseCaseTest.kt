package io.amplicode.connekt

import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.dsl.POST
import io.amplicode.connekt.test.utils.asInt
import io.amplicode.connekt.test.utils.asUnit
import io.amplicode.connekt.test.utils.server.TestServer
import io.amplicode.connekt.test.utils.runScript
import io.amplicode.connekt.test.utils.thenBodyString
import io.amplicode.connekt.test.utils.uuid
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import kotlin.test.Ignore
import kotlin.test.Test
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
                    val request = POST("$host/counter/{counter}/inc") {
                        pathParam("counter", counterKey)
                    }.asInt()
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
                    POST("$host/counter/{counter}/inc") {
                        pathParam("counter", counterKey)
                    }.then {
                        // just return some value
                        i
                    }
                }
            }

            GET("${host}/counter/{counter}") {
                pathParam("counter", counterKey)
            }.then {
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
                    POST("$host/counter/{counter}/inc") {
                        pathParam("counter", counterKey)
                    }
                }

                GET("$host/counter/{counter}") {
                    pathParam("counter", counterKey)
                }.asInt().let(counterResults::add)

                // call to trigger request
                prop

                GET("$host/counter/{counter}") {
                    pathParam("counter", counterKey)
                }.asInt().let(counterResults::add)
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

    @Test
    fun `vars capture`() = runScript {
        useCase {
            var counter = 0
            repeat(5) { iteration ->
                POST("$host/echo-body") {
                    body(counter.toString())
                }.let {
                    // Assert that it sends value that `counter` had at time the request closure is applied
                    assertEquals(
                        iteration,
                        it.body?.string()?.toInt()
                    )
                }
                ++counter
            }
        }
    }.asUnit()

    @Test
    fun `delegated var in useCase`() = runScript {
        val requestValue by POST("$host/echo-body") {
            body("foo")
        }.then {
            body!!.string()
        }

        useCase {
            POST("$host/echo-body") {
                body("$requestValue bar1")
            }.let {
                assertEquals(
                    "foo bar1",
                    it.body!!.string()
                )
            }
        }

        useCase {
            POST("$host/echo-body") {
                body("$requestValue bar2")
            }.let {
                assertEquals(
                    "foo bar2",
                    it.body!!.string()
                )
            }
        }
    }.asUnit()

    @Test
    @Ignore
    fun `return value in useCase`() = runScript {
        val requestValue by POST("$host/echo-body") {
            body("foo")
        }.then {
            body!!.string()
        }

        val useCaseResult by useCase {
            POST("$host/echo-body") {
                body("$requestValue bar1")
            } then {
                assertEquals(
                    "foo bar1",
                    body!!.string()
                )

                body!!.string()
            }

            return@useCase ""
        }

        useCase {
            assertEquals(
                "foo bar1",
                useCaseResult
            )
        }
    }.asUnit()
}

