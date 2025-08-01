package io.amplicode.connekt

import io.amplicode.connekt.context.VariablesStore
import io.amplicode.connekt.context.persistence.InMemoryStorage
import io.amplicode.connekt.context.persistence.defaultStorage
import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.test.utils.components.testConnektContext
import io.amplicode.connekt.test.utils.runScript
import io.amplicode.connekt.test.utils.server.TestServer
import java.io.Serializable
import kotlin.io.path.createTempDirectory
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VariablesTest(server: TestServer) : TestWithServer(server) {

    @Test
    fun `test persistent variables store`() {
        val persistenceStore = InMemoryStorage()
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
    fun `test by var syntax`() {
        runScript {
            data class MyObject(val name: String, val age: Int) : Serializable

            var myVar: String by variable()
            var myInt: Int? by variable()
            var myObject: MyObject by variable()

            GET("$host/foo") then {
                myVar = body!!.string()
                myInt = 1
                myObject = MyObject("foo", 42)
            }
            GET("$host/bar") then {
                assertEquals("foo", myVar)
                assertEquals(1, myInt)
                assertEquals(MyObject("foo", 42), myObject)
            }
        }
    }

    @Test
    fun `test delegated var usage in url other request string`() {
        runScript(1) {
            data class MyObject(val name: String, val age: Int)

            val firstResponse by GET("$host/foo") then {
                MyObject("fooz", 22)
            }

            GET("$host/echo-text?text=${firstResponse.name}") then {
                assertEquals("fooz", body!!.string())
            }
        }
    }

    @Test
    @Ignore("Delegates testing issue")
    fun `test strings persistence`() {
        val persistenceDir = createTempDirectory("connekt-persistence-test")

        fun runMyScript(requestNumber: Int) = runScript(
            requestNumber = requestNumber,
            context = testConnektContext(
                storage = defaultStorage(persistenceDir)
            )
        ) {
            var myVar: String by vars.variable()

            GET("$host/foo").then {
                myVar = body!!.string()
            }

            GET("$host/echo-text") {
                queryParam("text", myVar)
            }.then {
                assertEquals("foo", body!!.string())
            }
        }

        runMyScript(0)
        runMyScript(1)
    }

    @Test
    @Ignore("Delegates testing issue")
    fun `test objects persistence`() {
        val persistenceDir = createTempDirectory("connekt-persistence-test")

        fun runMyScript(requestNumber: Int) = runScript(
            requestNumber = requestNumber,
            context = testConnektContext(
                storage = defaultStorage(persistenceDir)
            )
        ) {
            data class MyObject(val name: String, val age: Int) : Serializable

            var myVar: MyObject by variable()

            GET("$host/foo").then {
                myVar = MyObject("Foo", 20)
            }

            GET("$host/bar").then {
                assertEquals(
                    MyObject("Foo", 20),
                    myVar
                )
            }
        }

        runMyScript(0)
        runMyScript(1)
    }
}