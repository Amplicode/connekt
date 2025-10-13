package io.amplicode.connekt

import io.amplicode.connekt.dsl.ConnektBuilder
import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.dsl.POST
import io.amplicode.connekt.test.utils.server.TestServer
import io.amplicode.connekt.test.utils.server.TestServerParamResolver
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.resources.serialization.*
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestServerParamResolver::class)
abstract class TestWithServer(server: TestServer) {

    val host: String = server.host

    protected fun ConnektBuilder.getCounterRequest(counterKey: String) = GET("$host/counter/{counter}") {
        pathParam("counter", counterKey)
    }

    protected fun ConnektBuilder.incCounterRequest(counterKey: String): RequestHolder =
        POST("$host/counter/{counter}/inc") {
            pathParam("counter", counterKey)
        }

    /**
     * Creates a path from the ktor resource object
     */
    inline fun <reified T : Any> resourcePath(resource: T): String {
        val builder = URLBuilder(host)
        href(ResourcesFormat(), resource, builder)
        return builder.buildString()
    }
}