package io.amplicode.connekt

import com.fasterxml.jackson.module.kotlin.readValue
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.dsl.RequestBuilder
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors


data class TokenResponse(
    val access_token: String,
    val expires_in: Int,
    val refresh_expires_in: Int,
    val refresh_token: String,
    val token_type: String,
    val id_token: String? = null,
    val session_state: String,
    val scope: String
)

class KeycloakOAuthExecutable(private val context: ConnektContext) : ExecutableWithResult<KeycloakOAuth>() {

    override fun doExecute(): KeycloakOAuth {
        // TODO build auth link on params
        val authLink = "http://localhost:9081/realms/petclinic/protocol/openid-connect/auth" +
                "?client_id=sb" +
                "&redirect_uri=http://localhost:8080/callback" +
                "&response_type=code" +
                "&scope=openid"
        context.printer.println(authLink)

        data class AuthCodeResponse(val code: String)
        val authCodeFuture = CompletableFuture<AuthCodeResponse>()
        val server = embeddedServer(
            Netty,
            port = 8080
        ) {
            routing {
                // TODO extract path into params
                get("/callback") {
                    val code = call.parameters.getOrFail("code")
                    call.respondText("Authentication successful 🤟😎🤟")
                    authCodeFuture.complete(AuthCodeResponse(code))
                }
            }
        }
        server.start()

        val authCodeResponse: AuthCodeResponse = try {
            authCodeFuture.get()
        } finally {
            // TODO move TPE into `context`?
            Executors.newSingleThreadExecutor().submit {
                server.stop()
            }
        }

        // TODO all params should be taken from fun params
        val credentialsRequestBuilder = RequestBuilder(
            "POST",
            "http://localhost:9081/realms/petclinic/protocol/openid-connect/token",
            context
        ).apply {
            formData {
                field("client_id", "sb")
                field("grant_type", "authorization_code")
                field("code", authCodeResponse.code)
                field("redirect_uri", "http://localhost:8080/callback")
            }
        }

        val executionStrategy = context.executionContext
            .getExecutionStrategy(this, context)

        val credentialsResponse = executionStrategy.executeRequest(credentialsRequestBuilder)

        val credentialsString = credentialsResponse.body?.string()
        requireNotNull(credentialsString) {
            "Unexpected credentials response: $credentialsResponse"
        }

        val tokenResponse = context.jsonContext
            .objectMapper
            .readValue<TokenResponse>(credentialsString)

        return KeycloakOAuth(tokenResponse)
    }
}

class KeycloakOAuth(private val tokenResponse: TokenResponse) : TokenProvider {
    override fun getToken(): String {
        return tokenResponse.access_token
    }
}

interface TokenProvider {
    fun getToken(): String
}