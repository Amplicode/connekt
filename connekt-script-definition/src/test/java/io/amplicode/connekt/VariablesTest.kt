package io.amplicode.connekt

import io.amplicode.connekt.context.persistence.JavaSerializationFilePersistenceStore
import io.amplicode.connekt.context.persistence.InMemoryPersistenceStore
import io.amplicode.connekt.context.VariablesStore
import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.test.utils.components.testConnektContext
import io.amplicode.connekt.test.utils.runScript
import io.amplicode.connekt.test.utils.server.TestServer
import java.io.Serializable
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VariablesTest(server: TestServer) : TestWithServer(server) {

    @Test
    fun `teat persistent variables store`() {
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
    fun `test by var syntax`() {
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
    fun `test strings persistence`() {
        val persistenceDir = createTempDirectory("connekt-persistence-test")

        fun runMyScript(requestNumber: Int) = runScript(
            requestNumber = requestNumber,
            context = testConnektContext(
                persistenceStore = JavaSerializationFilePersistenceStore(persistenceDir)
            )
        ) {
            val myVar by variable<String>()

            GET("$host/foo").then {
                myVar.set(body!!.string())
            }

            GET("$host/echo-text?text=$myVar").then {
                assertEquals("foo", body!!.string())
            }
        }

        runMyScript(0)
        runMyScript(1)
    }

    @Test
    fun `test objects persistence`() {
        val persistenceDir = createTempDirectory("connekt-persistence-test")

        fun runMyScript(requestNumber: Int) = runScript(
            requestNumber = requestNumber,
            context = testConnektContext(
                persistenceStore = JavaSerializationFilePersistenceStore(persistenceDir)
            )
        ) {
            data class MyObject(val name: String, val age: Int) : Serializable

            val myVar by variable<MyObject>()

            GET("$host/foo").then {
                myVar.set(MyObject("Foo", 20))
            }

            GET("$host/echo-text?text=$myVar").then {
                assertEquals(
                    MyObject("Foo", 20),
                    myVar.get()
                )
            }
        }

        runMyScript(0)
        runMyScript(1)
    }
}