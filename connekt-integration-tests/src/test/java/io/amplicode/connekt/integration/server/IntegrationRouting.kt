package io.amplicode.connekt.integration.server

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

fun Application.configureIntegrationRouting() {
    routing {
        get("foo") {
            call.respondText("foo")
        }
        get("bar") {
            call.respondText("bar")
        }
        get("protected") {
            val auth = call.request.headers["Authorization"]
            if (auth != null && auth.startsWith("Bearer ")) {
                call.respondText("ok")
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }
        }
        jsonApi()
        counterApi()
        echoApi()
        cookiesApi()
        oauthApi()
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

// OAuth test endpoints — mirrors OAuthApi from connekt-script-definition test sources

@Suppress("PropertyName")
@Serializable
@Resource("/oauth")
class OAuth {
    @Serializable
    @Resource("auth")
    class Auth(
        val parent: OAuth = OAuth(),
        val response_type: String? = null,
        val redirect_uri: String? = null,
        val client_id: String? = null
    )

    @Serializable
    @Resource("token")
    class Token(val parent: OAuth = OAuth())
}

private fun Routing.oauthApi() {
    get<OAuth.Auth> {
        val redirectUri = it.redirect_uri!!
        val url = URI("$redirectUri${if ('?' in redirectUri) "&" else "?"}code=42").toURL()
        url.openStream().close() // send the HTTP GET so the OAuthRunner's callback server receives the code
        call.respond(HttpStatusCode.OK)
    }

    post<OAuth.Token> {
        val grantType = call.receiveParameters()["grant_type"] ?: error("grant_type is required")
        val response = when (grantType) {
            "authorization_code" -> OAuthAccessTokenResponse(
                access_token = generateOAuthToken(),
                expires_in = Int.MAX_VALUE.toLong(),
                refresh_token = generateOAuthToken(),
                scope = "openid",
                token_type = "Bearer",
                refresh_token_expires_in = Int.MAX_VALUE.toLong(),
                refresh_expires_in = Int.MAX_VALUE.toLong()
            )
            "refresh_token" -> OAuthRefreshTokenResponse(
                access_token = generateOAuthToken(),
                expires_in = Int.MAX_VALUE.toLong(),
                scope = "openid",
                token_type = "Bearer",
                refresh_token_expires_in = Int.MAX_VALUE.toLong(),
                refresh_expires_in = Int.MAX_VALUE.toLong()
            )
            else -> error("Unsupported grant_type: $grantType")
        }
        call.respond(response)
    }
}

private fun generateOAuthToken(length: Int = 16): String {
    val chars = ('a'..'z').toList()
    return "gen-token::" + (1..length).map { chars.random() }.joinToString("")
}

@Serializable
@Suppress("PropertyName")
private data class OAuthAccessTokenResponse(
    val access_token: String,
    val expires_in: Long,
    val refresh_token: String,
    val scope: String,
    val token_type: String,
    val refresh_token_expires_in: Long?,
    val refresh_expires_in: Long?
)

@Serializable
@Suppress("PropertyName")
private data class OAuthRefreshTokenResponse(
    val access_token: String,
    val expires_in: Long,
    val scope: String,
    val token_type: String,
    val refresh_token_expires_in: Long?,
    val refresh_expires_in: Long?
)
