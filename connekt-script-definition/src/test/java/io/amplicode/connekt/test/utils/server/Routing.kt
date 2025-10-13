package io.amplicode.connekt.test.utils.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicInteger

fun Application.configureRouting() {
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
        cookiesApi()
        staticResources(
            "/static",
            "testserver",
            index = null
        )
        downloadApi()
        oauthApi()
    }
}

private fun Routing.downloadApi() {
    get("/download") {
        val filename = call.queryParameters.getOrFail("filename")
        val length = call.queryParameters.getOrFail<Int>("length")

        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(
                ContentDisposition.Parameters.FileName,
                filename
            ).toString()
        )

        val contentType = call.parameters["contentType"]
            ?.let { ContentType.parse(it) }

        call.respondOutputStream(contentType) {
            val chars = ('a'..'z')
            (0 until length).asSequence()
                .map { chars.random().code }
                .forEach<Int>(::write)
        }
    }
}

private fun Routing.cookiesApi() = route("/cookies") {
    post("/set") {
        val setCookieRequest = call.receive<SetCookieRequest>()
        call.response.cookies.apply {
            setCookieRequest.cookieRequests.forEach(::append)
        }
        call.respond(HttpStatusCode.OK)
    }
}


private fun Routing.jsonApi() {
    route("/json") {
        get("one-line-object") {
            call.respondText(
                //language=json
                """{"foo": "f", "bar": "b", "baz": 3}""",
                contentType = ContentType.Application.Json
            )
        }
        get("one-line-array") {
            call.respondText(
                //language=json
                """[1,2,3]""",
                contentType = ContentType.Application.Json
            )
        }
        get("invalid-object") {
            call.respondText(
                "foo bar",
                contentType = ContentType.Application.Json
            )
        }
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

@Serializable
data class SetCookieRequest(val cookieRequests: List<Cookie>)