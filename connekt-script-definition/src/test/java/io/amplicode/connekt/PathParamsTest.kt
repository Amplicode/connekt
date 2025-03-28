package io.amplicode.connekt

import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.test.utils.TestServer
import io.amplicode.connekt.test.utils.runScript
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PathParamsTest(server: TestServer) : TestWithServer(server) {

    @Test
    fun `test path params`() {
        runScript {
            GET("$host/echo-path/{foo}/bar/{baz}") {
                pathParam("foo", 1)
                pathParam("baz", 2)
            }.then {
                assertEquals(
                    "/1/bar/2",
                    body?.string()
                )
            }
        }
    }
}