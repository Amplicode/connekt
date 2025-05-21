/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */
@file:Suppress("HttpUrlsUsage")

package io.amplicode.connekt

import io.amplicode.connekt.context.VariablesStore
import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.dsl.POST
import io.amplicode.connekt.dsl.contentType
import io.amplicode.connekt.test.utils.components.InMemoryEnvironmentStore
import io.amplicode.connekt.test.utils.components.testConnektContext
import io.amplicode.connekt.test.utils.extractBodyString
import io.amplicode.connekt.test.utils.runScript
import io.amplicode.connekt.context.InMemoryPersistenceStore
import io.amplicode.connekt.test.utils.server.TestServer
import io.ktor.serialization.kotlinx.json.*
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.opentest4j.AssertionFailedError
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DslTest(server: TestServer) : TestWithServer(server) {

    @Test
    fun simpleTest() {
        val persistenceStore = InMemoryPersistenceStore()
        val varStore = VariablesStore(persistenceStore)

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

        runScript(context = testConnektContext(environmentStore = envStore)) {
            val one: Int by env
            val two: String by env
            GET("$host/foo?p=$one") {
                assertEquals(1, one)
                assertEquals("2", two)
            }
        }
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
    fun `test assertion in then {}`() {
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
    fun `test configure client with empty value`() {
        runScript {
            configureClient {

            }
        }
    }
}
