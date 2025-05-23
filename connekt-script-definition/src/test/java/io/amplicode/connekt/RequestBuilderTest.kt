package io.amplicode.connekt

import io.amplicode.connekt.dsl.RequestBuilder
import okhttp3.Request
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestBuilderTest {

    @Test
    fun `test valid url with placeholders`() {
        val builder = RequestBuilder(
            "GET",
            "http://localhost/api/{orderId}/{bar-bar}",
            null
        ).apply {
            pathParam("orderId", "102")
            pathParam("bar-bar", "2")
            queryParam("q1", 1)
            queryParam("q2", "2")
        }
        val actualRequest = builder.build()

        val expectedRequest = Request.Builder()
            .method("GET", null)
            .url("http://localhost/api/102/2?q1=1&q2=2")
            .header("User-Agent", "connekt/0.0.1")
            .build()

        assertEquals(
            expectedRequest.toString(),
            actualRequest.toString()
        )
    }

    @Test
    fun `test missing path param`() {
        val exception = assertThrows<MissingPathParameterException> {
            RequestBuilder(
                "GET",
                "http://localhost/api/{foo}/{bar}/{missing-path-param}",
                null
            ).apply {
                pathParam("foo", "1")
                pathParam("bar", "2")
            }.build()
        }

        assertEquals(
            "missing-path-param",
            exception.parameterName
        )
    }
}