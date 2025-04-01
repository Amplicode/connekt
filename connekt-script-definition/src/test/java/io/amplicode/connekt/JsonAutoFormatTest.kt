package io.amplicode.connekt

import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.test.utils.extractBodyString
import io.amplicode.connekt.test.utils.server.TestServer
import io.amplicode.connekt.test.utils.runScript
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JsonAutoFormatTest(server: TestServer) : TestWithServer(server) {

    @Test
    fun testJsonFormatting() {
        runScript(0) {
            GET("$host/json/one-line-object")
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
            GET("$host/json/one-line-array")
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
            GET("$host/json/invalid-object")
        }.let { output ->
            assertEquals(
                "foo bar",
                extractBodyString(output)
            )
        }
    }
}