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
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.mapdb.DB
import org.mapdb.DBMaker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DslTest {

    lateinit var connektOutput: StringContentHolder

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
        runWithServer {  host ->
            createConnektBuilder().runScript {
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
    }

    @Test
    fun testEnvSyntax() {
        runWithServer { host ->
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
    }

    @Test
    fun testRunEntireScript() {
        runScriptWithServer { host ->
            GET("$host/foo")
            GET("$host/bar")
        }
    }

    @Test
    fun testDelegatedPropertiesRequest() {
        val (host) = runScriptWithServer(1) { host ->
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
            connektOutput.asString()
        )
    }

    private fun runScriptWithServer(
        requestNumber: Int? = null,
        configure: ConnektBuilder.(host: String) -> Unit
    ): TestEnvironment {
        lateinit var evnHost: String
        runWithServer { host ->
            evnHost = host
            val connektBuilder = createConnektBuilder()
            connektBuilder.configure(host)
            connektBuilder.runScript(requestNumber)
        }
        return TestEnvironment(evnHost)
    }

    private fun runWithServer(block: (host: String) -> Unit) {
        val server = embeddedServer(
            Netty,
            port = 0
        ) {
            routing {
                get("foo") {
                    call.respondText("foo")
                }
                get("bar") {
                    call.respondText("bar")
                }
            }
        }

        try {
            server.start(false)
            val port = runBlocking {
                server.engine.resolvedConnectors().first().port
            }
            block("http://localhost:$port")
        } finally {
            server.stop()
        }
    }

    private fun createConnektBuilder(
        db: DB = DBMaker.memoryDB().make(),
        environmentStore: EnvironmentStore = NoOpEnvironmentStore,
    ): ConnektBuilder {
        val intoStringPrinter = StringBuilderPrinter()
        connektOutput = intoStringPrinter
        val connektContext = ConnektContext(
            db,
            environmentStore,
            VariablesStore(db),
            MultiPrinter(SystemOutPrinter, intoStringPrinter)
        )
        val connektBuilder = ConnektBuilder(connektContext)
        return connektBuilder
    }

    class MultiPrinter(private val printers: List<Printer>) : Printer {

        constructor(vararg printers: Printer) : this(printers.toList())

        override fun print(text: String, color: Printer.Color?) {
            printers.forEach { printer ->
                printer.print(text, color)
            }
        }
    }

    class StringBuilderPrinter : BaseNonColorPrinter(), StringContentHolder {
        private val sb = StringBuilder()

        override fun print(s: String) {
            sb.append(s)
        }

        override fun asString(): String = sb.toString()
    }

    interface StringContentHolder {
        fun asString(): String
    }

}

fun ConnektBuilder.runScript(
    requestNumber: Int? = null,
    configure: ConnektBuilder.() -> Unit = { }
) {
    this.configure()
    RequestExecutor.execute(this, requestNumber)
}

data class TestEnvironment(val serverHost: String)
