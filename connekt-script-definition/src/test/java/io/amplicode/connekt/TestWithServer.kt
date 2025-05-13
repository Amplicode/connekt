package io.amplicode.connekt

import io.amplicode.connekt.dsl.ConnektBuilder
import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.dsl.POST
import io.amplicode.connekt.test.utils.server.TestServer
import io.amplicode.connekt.test.utils.server.TestServerParamResolver
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestServerParamResolver::class)
abstract class TestWithServer(server: TestServer) {
    protected val host: String = server.host

    protected fun ConnektBuilder.getCounterRequest(counterKey: String) = GET("$host/counter/{counter}") {
        pathParam("counter", counterKey)
    }

    protected fun ConnektBuilder.incCounterRequest(counterKey: String): RequestHolder =
        POST("$host/counter/{counter}/inc") {
            pathParam("counter", counterKey)
        }
}