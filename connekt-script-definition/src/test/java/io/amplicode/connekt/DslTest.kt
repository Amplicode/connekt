/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

@file:Suppress("HttpUrlsUsage")

package io.amplicode.connekt

import io.amplicode.connekt.console.BaseNonColorPrinter
import io.amplicode.connekt.console.Printer
import io.amplicode.connekt.console.SystemOutPrinter
import io.amplicode.connekt.dsl.ConnektBuilder
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.mapdb.DB
import org.mapdb.DBMaker
import kotlin.io.path.createTempFile
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

private fun createTestServer() = embeddedServer(
    Netty,
    port = 0
) {
    var counter = 0

    install(ContentNegotiation) {
        json()
    }

    routing {
        get("foo") {
            call.respondText("foo")
        }
        get("bar") {
            call.respondText("bar")
        }
        get("one-line-json-object") {
            call.respondText(
                //language=json
                """{"foo": "f", "bar": "b", "baz": 3}""",
                contentType = ContentType.Application.Json
            )
        }
        get("one-line-json-array") {
            call.respondText(
                //language=json
                """[1,2,3]""",
                contentType = ContentType.Application.Json
            )
        }
        get("invalid-json-object") {
            call.respondText(
                "foo bar",
                contentType = ContentType.Application.Json
            )
        }
        // mirrors headers from request
        get("headers-test") {
            val headersMap = call.request.headers.toMap()
            call.respond(headersMap)
        }
        get("counter") {
            if (call.queryParameters["reset"]?.toBoolean() == true) {
                counter = 0
            }
            call.respond(counter++)
        }
    }
}

class TestPrinter : Printer {
    val stringPrinter = StringBuilderPrinter()

    override fun print(text: String, color: Printer.Color?) {
        sequenceOf(SystemOutPrinter, stringPrinter).forEach { printer ->
            printer.print(text, color)
        }
    }
}

class StringBuilderPrinter : BaseNonColorPrinter() {
    private val sb = StringBuilder()
    override fun print(s: String) {
        sb.append(s)
    }

    fun asString(): String = sb.toString()
}

fun extractBodyString(s: String): String = s.split("\n\n")
    .last()
    .removeSuffix("\n")

private fun runScript(
    requestNumber: Int? = null,
    connektBuilder: ConnektBuilder = createConnektBuilder(),
    configure: ConnektBuilder.() -> Unit = { }
) = connektBuilder.runScript(requestNumber, configure)

private fun createConnektBuilder(
    db: DB = DBMaker.memoryDB().make(),
    environmentStore: EnvironmentStore = NoOpEnvironmentStore,
): ConnektBuilder {
    val connektContext = ConnektContext(
        db,
        environmentStore,
        VariablesStore(db),
        TestPrinter()
    )
    val connektBuilder = ConnektBuilder(connektContext)
    return connektBuilder
}

fun ConnektBuilder.runScript(
    requestNumber: Int? = null,
    configure: ConnektBuilder.() -> Unit = { }
): String {
    this.configure()
    RequestExecutor.execute(this, requestNumber)
    return (connektContext.printer as TestPrinter).stringPrinter.asString()
}
