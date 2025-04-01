package io.amplicode.connekt

import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.test.utils.server.TestServer
import io.amplicode.connekt.test.utils.runScript
import okhttp3.Response
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals

class RunByNumberTest(server: TestServer) : TestWithServer(server) {

    @Test
    fun testRunEntireScript() {
        runScript {
            GET("$host/foo")
            GET("$host/bar")
        }
    }

    @Test
    fun `test inner request builder is ignored`() {
        val responses = ArrayDeque<String>()

        fun Response.registerResponse(): String {
            val response = body!!.string()
            responses.add(response)
            return response
        }

        runScript(1) {
            // 0
            @Suppress("UnusedVariable")
            val echo0 by GET("$host/echo-text") {
                queryParam("text", 0)

                // 0_1
                @Suppress("UnusedVariable")
                val echo0_1 by GET("$host/echo-text") {
                    queryParam("text", "0_1")
                }.then(Response::registerResponse)
            }.then(Response::registerResponse)

            // 1
            GET("$host/echo-text") {
                queryParam("text", 1)
            }.then(Response::registerResponse)
        }

        assertContentEquals(
            listOf("1"),
            responses.toList()
        )
    }
}