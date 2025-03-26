package io.amplicode.connekt.test.utils

import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail
import io.ktor.util.toMap
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

fun createTestServer() = embeddedServer(
    Netty,
    port = 0
) {
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
        jsonApi()
        counterApi()
        echoApi()
    }
}

private fun Routing.jsonApi() {
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
}

private fun Routing.echoApi() {
    // mirrors headers from request
    get("echo-headers") {
        val headersMap = call.request.headers.toMap()
        call.respond(headersMap)
    }
    post("echo-form-params") {
        val params = call.receiveParameters().toMap()
        call.respond(params)
    }
    post("echo-body") {
        val bodyText = call.receiveText()
        call.respondText(bodyText)
    }
    get("echo-query-params") {
        val queryParams = call.queryParameters.toMap()
        call.respond(queryParams)
    }
    get("echo-path/{...}") {
        val path = call.request
            .uri
            .substringAfter("echo-path", "")
        call.respond(path)
    }
    get("echo-text") {
        val text = call.parameters.getOrFail("text")
        call.respondText(text)
    }
}

private fun Routing.counterApi() {
    val counterService = object {
        private val counters =
            mutableMapOf<String, AtomicInteger>()

        fun getCounter(key: String?): AtomicInteger {
            return counters.getOrPut(key ?: "default", ::AtomicInteger)
        }
    }

    fun RoutingContext.getCounter() =
        counterService.getCounter(call.pathParameters["id"])

    route("counter/{id}") {
        get {
            call.respond(getCounter().get())
        }
        post("/inc") {
            call.respond(getCounter().incrementAndGet())
        }
        post("/reset") {
            getCounter().set(0)
        }
    }
}

class TestServer(
    private val server: EmbeddedServer<*, *> = createTestServer()
) {
    init {
        server.start(wait = false)
    }

    val port = runBlocking {
        server.engine.resolvedConnectors().first().port
    }

    val host = "http://localhost:$port"

    fun stop() {
        server.stop()
    }
}