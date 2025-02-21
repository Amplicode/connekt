/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

@file:Suppress("HttpUrlsUsage")

package io.amplicode.connekt

import io.amplicode.connekt.test.utils.createConnektBuilder
import io.amplicode.connekt.test.utils.createTestServer
import io.amplicode.connekt.test.utils.runScript
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.engine.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.mapdb.DBMaker
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DslTest {

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
        val connektBuilder = createConnektBuilder(environmentStore = envStore)
        envStore["one"] = 1
        envStore["two"] = "2"

        connektBuilder.runScript {
            val one: Int by env
            val two: String by env
            GET("$host/foo?p=$one") {
                assertEquals(1, one)
                assertEquals("2", two)
            }
        }
    }

    @Test
    fun testRunEntireScript() {
        runScript {
            GET("$host/foo")
            GET("$host/bar")
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
    fun testJsonFormatting() {
        runScript(0) {
            GET("$host/one-line-json-object")
        }.let { output ->
            assertEquals(
                //language=json
                """
                    {
                      "foo" : "f",
                      "bar" : "b",
                      "baz" : 3
                    }
                    """.trimIndent(),
                extractBodyString(output)
            )
        }

        runScript(0) {
            GET("$host/one-line-json-array")
        }.let { output ->
            assertEquals(
                //language=json
                """
                        [ 1, 2, 3 ]
                    """.trimIndent(),
                extractBodyString(output)
            )
        }

        runScript(0) {
            GET("$host/invalid-json-object")
        }.let { output ->
            assertEquals(
                "foo bar",
                extractBodyString(output)
            )
        }
    }

    @Test
    fun `test repeated headers`() {
        val output = runScript {
            GET("$host/headers-test") {
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
        val dbTempFile = createTempFile("connekt-test.db")
            .toFile()
            .also {
                it.delete()
                it.deleteOnExit()
            }

        // Run the script twice and make sure `counterResponse`
        // is not overwritten on second run
        repeat(2) { timeNumber ->
            runScript(
                1,
                createConnektBuilder(DBMaker.fileDB(dbTempFile).make())
            ) {
                val counterResponse: String by GET("$host/counter") {
                    // make sure that counter is set to '0' on very first run
                    if (timeNumber == 0) {
                        queryParam("reset", "true")
                    }
                } then {
                    body!!.string()
                }

                GET("$host/foo") {
                    assertEquals("0", counterResponse)
                }
            }
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
    fun `test flow`() {
        runScript {
            flow("my-flow") {
                val toSend = listOf(
                    "foo", "bar", "baz"
                )
                toSend.forEach { payload ->
                    val result = POST("$host/echo-body") {
                        body(payload)
                    }.then {
                        body?.string()
                    }.execute()

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
    fun `test path params`() {
        runScript {
            GET("$host/echo-path/{foo}/bar/{baz}") {
                pathParams("foo", 1)
                pathParams("baz", 2)
            }.then {
                assertEquals(
                    "/1/bar/2",
                    body?.string()
                )
            }
        }
    }

    lateinit var server: EmbeddedServer<*, *>
    lateinit var host: String

    @BeforeAll
    fun before() {
        server = createTestServer()
        server.start(false)
        val port = runBlocking {
            server.engine.resolvedConnectors().first().port
        }
        host = "http://localhost:$port"
    }

    @AfterAll
    fun after() {
        server.stop()
    }
}

fun extractBodyString(s: String): String = s.split("\n\n")
    .last()
    .removeSuffix("\n")
