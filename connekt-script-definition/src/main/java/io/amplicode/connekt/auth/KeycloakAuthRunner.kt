package io.amplicode.connekt.auth

import com.fasterxml.jackson.module.kotlin.readValue
import io.amplicode.connekt.DefaultExecutionStrategy
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.dsl.RequestBuilder
import io.amplicode.connekt.println
import io.amplicode.connekt.tokenUrl
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import okhttp3.Response
import java.util.concurrent.CompletableFuture

class KeycloakAuthRunner(
    private val context: ConnektContext,
    private val oAuthParams: KeycloakOAuthParameters
) : BaseAuthRunner() {

    override fun refresh(auth: Auth): Auth {
        return refreshToken(auth.refreshToken)
    }

    override fun authorize(): Auth {
        return startBrowserAuthentication()
    }

    private fun startBrowserAuthentication(): Auth {
        val authLink = createBrowserAuthLink()
        context.printer.println("Please open this link in browser: $authLink")

        val authCodeFuture = CompletableFuture<String>()
        val server = createCallbackServer(authCodeFuture)

        val authCode = try {
            server.start()
            authCodeFuture.get()
        } finally {
            // TODO move TPE into `context`?
            server.stop()
        }

        val authByCodeRequest = buildTokenRequest(authCode)
        val response = authByCodeRequest.executeRequest()
        val tokenResponse = response.toTokenResponse()
        return tokenResponse.toAuth()
    }

    private fun createCallbackServer(authCodeFuture: CompletableFuture<String>): EmbeddedServer<*, *> = embeddedServer(
        Netty,
        port = oAuthParams.callbackPort
    ) {
        routing {
            get(oAuthParams.callbackPath) {
                runCatching {
                    val code = call.parameters.getOrFail("code")
                    call.respondText("Authentication successful")
                    code
                }.onSuccess(authCodeFuture::complete)
                    .onFailure(authCodeFuture::completeExceptionally)
            }
        }
    }

    private fun buildTokenRequest(authCode: String): RequestBuilder = RequestBuilder(
        "POST",
        oAuthParams.tokenUrl,
        context
    ).apply {
        formData {
            with(oAuthParams) {
                field("client_id", clientId)
                field("grant_type", "authorization_code")
                field("code", authCode)
                field("redirect_uri", redirectUrl)
            }
        }
    }

    private fun createBrowserAuthLink() = with(oAuthParams) {
        "$serverBaseUrl/realms/$realm/protocol/$protocol/auth" +
                "?client_id=$clientId" +
                "&redirect_uri=$redirectUrl" +
                "&response_type=code" +
                "&scope=$scope"
    }

    private fun refreshToken(refreshToken: String): Auth {
        val requestBuilder = RequestBuilder(
            "POST",
            oAuthParams.tokenUrl,
            context
        ).apply {
            formData {
                field("client_id", oAuthParams.clientId)
                field("grant_type", "refresh_token")
                field("refresh_token", refreshToken)
            }
        }

        val response = requestBuilder.executeRequest()
        val tokenResponse = response.toTokenResponse()
        return tokenResponse.toAuth()
    }

    private fun RequestBuilder.executeRequest() = DefaultExecutionStrategy(context).executeRequest(this)

    private fun Response.toTokenResponse(): TokenResponse {
        val credentialsString = this.body?.string()
        requireNotNull(credentialsString) {
            "Unexpected credentials response: $this"
        }

        return context.jsonContext
            .objectMapper
            .readValue<TokenResponse>(credentialsString)
    }
}

data class TokenResponse(
    val access_token: String,
    val expires_in: Long,
    val refresh_expires_in: Long,
    val refresh_token: String,
    val token_type: String,
    val id_token: String? = null,
    val session_state: String,
    val scope: String
)

fun TokenResponse.toAuth(): Auth {
    val timeNow = System.currentTimeMillis()
    return Auth(
        access_token,
        refresh_token,
        timeNow + expires_in * 1000,
        timeNow + refresh_expires_in * 1000,
    )
}

data class KeycloakOAuthParameters(
    val serverBaseUrl: String,
    val realm: String,
    val protocol: String,
    val clientId: String,
    val scope: String,

    val callbackPort: Int,
    val callbackPath: String
)

internal val KeycloakOAuthParameters.redirectUrl: String
    get() = buildUrl {
        protocol = URLProtocol.HTTP
        host = "localhost"
        port = callbackPort
        path(callbackPath)
    }.toString()