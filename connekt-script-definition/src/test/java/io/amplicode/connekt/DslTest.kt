/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */
@file:Suppress("HttpUrlsUsage")

package io.amplicode.connekt

import io.amplicode.connekt.context.VariablesStore
import io.amplicode.connekt.dsl.*
import io.amplicode.connekt.test.utils.ConnektContext
import io.amplicode.connekt.test.utils.TestServer
import io.amplicode.connekt.test.utils.runScript
import io.ktor.serialization.kotlinx.json.*
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mapdb.DBMaker
import org.opentest4j.AssertionFailedError
import java.util.UUID
import kotlin.collections.ArrayDeque
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DslTest(server: TestServer) : TestWithServer(server) {

    @Test
    fun simpleTest() {
        val db = DBMaker.memoryDB().make()
        val varStore = VariablesStore(db)

        val oneAsStr by varStore.string()
        oneAsStr.set("one")
        assertEquals("one", oneAsStr.get())

        val oneAsInt by varStore.int()
        // The value must become `null` because the variable type had been changed
        // so the variable can't be rad as Int now
        assertNull(oneAsInt.get())
        oneAsInt.set(1)
        assertEquals(1, oneAsInt.get())
    }

    @Test
    fun testVarSyntax() {
        runScript {
            val myVar by variable<String>()
            val myInt by vars.int()

            GET("$host/foo") then {
                myVar.set(body!!.string())
                myInt.set(1)
            }
            GET("$host/bar") then {
                assertEquals("foo", myVar.get())
                assertEquals(1, myInt.get())
            }
        }
    }

    @Test
    fun testEnvSyntax() {
        val envStore = InMemoryEnvironmentStore()
        envStore["one"] = 1
        envStore["two"] = "2"

        runScript(context = ConnektContext(environmentStore = envStore)) {
            val one: Int by env
            val two: String by env
            GET("$host/foo?p=$one") {
                assertEquals(1, one)
                assertEquals("2", two)
            }
        }
    }

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
    fun `test repeated headers`() {
        val output = runScript {
            GET("$host/echo-headers") {
                header("a-header", "a-1")
                header("a-header", "a-2")
            }
        }
        val body = extractBodyString(output)
        val responseObject: Map<String, List<String>> = DefaultJson.decodeFromString(body)
        assertContains(responseObject, "a-header")
        assertContentEquals(
            listOf("a-1", "a-2"),
            responseObject["a-header"]
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
                context = ConnektContext(db = dbProvider.getDb())
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
            context = ConnektContext(dbProvider.getDb())
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
            context = ConnektContext(dbProvider.getDb())
        ) {
            val counterVar by incCounterRequest(counterKey).thenBodyInt()
            // Stays 1 before execution fase
            assertEquals(1, counterVar, "Second run")
        }

        println("3d run")
        runScript(
            context = ConnektContext(dbProvider.getDb())
        ) {
            val counterVar by incCounterRequest(counterKey).thenBodyInt()
            // Should be updated to 2 due before execution fase
            assertEquals(2, counterVar, "Third run")
        }
    }

    @Test
    // TODO add assertions
    fun `test multipart request`() {
        runScript {
            // Data to be sent
            val firstInputFile = createTempFile().let {
                it.writeText("first file input")
                it.toFile()
            }
            val secondInputText = "second file input"
            val thirdInputFile = createTempFile().let {
                it.writeText("third file input")
                it.toFile()
            }

            POST("$host/multipart-test") {
                contentType("multipart/form-data; boundary=boundary")
                multipart {
                    // First
                    file("first", "input.txt", firstInputFile)

                    // Second
                    val secondInputFile = createTempFile()
                        .let {
                            it.writeText(secondInputText)
                            it.toFile()
                        }
                    file("second", "input-second.txt", secondInputFile)

                    // Third
                    part("third") {
                        val bodyText = thirdInputFile.readText()
                        body(bodyText)
                    }
                }
            }
        }
    }

    @Test
    fun `test form data request`() {
        runScript {
            POST("$host/echo-form-params") {
                formData {
                    field("id", 999)
                    field("value", "content")
                    field("fact", "IntelliJ + HTTP Client = <3")
                }
            } then {
                val echoedFormParams = jsonPath().json<Map<String, List<String>>>()
                assertEquals(
                    mapOf(
                        "id" to listOf("999"),
                        "value" to listOf("content"),
                        "fact" to listOf("IntelliJ + HTTP Client = <3"),
                    ),
                    echoedFormParams
                )
            }
        }
    }

    @Test
    fun `test store request into file`() {
        val outputFile = createTempFile().toFile().also {
            it.deleteOnExit()
        }

        val text = """
            lorem ipsum dolor sit amet
        """.trimIndent()
        runScript {
            POST("$host/echo-body") {
                body(text)
            }.then {
                outputFile.writeText(body?.string() ?: "")
            }
        }
        assertEquals(text, outputFile.readText())
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
    fun `test assertion in then {} of delegated request`() {
        assertThrows<AssertionFailedError> {
            runScript {
                GET("$host/foo").then {
                    // Trigger assertion error
                    Assertions.assertThat(2 + 2).isEqualTo(5)
                }
            }
        }
    }

    @Test
    fun `test request with then`() {
        var response: String? = null

        runScript {
            GET("$host/foo").then {
                response = body?.string()
            }
        }
        assertEquals("foo", response)
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
    fun `test query params`() {
        runScript {
            GET("$host/echo-query-params") {
                queryParam("foo", 1)
                queryParam("bar", 2)
                queryParam("baz", 3)
            }.then {
                val params = jsonPath().json<Map<String, List<String>>>()
                assertEquals(
                    mapOf(
                        "foo" to "1",
                        "bar" to "2",
                        "baz" to "3"
                    ),
                    params.mapValues { (_, value) -> value.single() }
                )
            }
        }
    }

    @Test
    fun `test run delegated request by number`() {
        val responses = ArrayDeque<String>()
        runScript(0) {
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

    private fun ConnektBuilder.getCounterRequest(counterKey: String) = GET("$host/counter/{counter}") {
        pathParam("counter", counterKey)
    }

    private fun UseCaseBuilder.getCounterRequest(
        counterKey: String,
        handleValue: (Int) -> Unit = { }
    ) = GET("$host/counter/{counter}") {
        pathParam("counter", counterKey)
    }.then {
        handleValue(body!!.string().toInt())
    }

    private fun ConnektBuilder.incCounterRequest(counterKey: String): RequestHolder =
        POST("$host/counter/{counter}/inc") {
            pathParam("counter", counterKey)
        }

    private fun UseCaseBuilder.incCounterRequest(counterKey: String): RequestHolder =
        POST("$host/counter/{counter}/inc") {
            pathParam("counter", counterKey)
        }
}

fun extractBodyString(s: String): String = s.split("\n\n")
    .last()
    .removeSuffix("\n")

private fun uuid() = UUID.randomUUID().toString()

fun RequestHolder.thenBodyString(handleString: (String) -> Unit = { }) =
    then {
        val bodyString = body!!.string()
        handleString(bodyString)
        bodyString
    }

fun RequestHolder.thenBodyInt() =
    then { body!!.string().toInt() }

class TempFileDbProvider {
    val dbTempFile = createTempFile("connekt-test.db")
        .toFile()
        .also {
            it.delete()
            it.deleteOnExit()
        }

    fun getDb() = DBMaker.fileDB(dbTempFile).make()
}