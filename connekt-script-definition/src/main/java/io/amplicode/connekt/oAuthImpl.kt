package io.amplicode.connekt

import com.fasterxml.jackson.module.kotlin.readValue
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.dsl.RequestBuilder
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import okhttp3.Response
import java.util.concurrent.CompletableFuture


data class TokenResponse(
    val access_token: String,
    val expires_in: Long,
    val refresh_expires_in: Long,
    val refresh_token: String,
    val token_type: String,
    val id_token: String? = null,
    val session_state: String,
    val scope: String
) {
    fun toOauthState(): KeycloakOAuthState {
        val timeNow = System.currentTimeMillis()
        return KeycloakOAuthState(
            access_token,
            expires_in,
            timeNow,
            refresh_token,
            refresh_expires_in,
            timeNow
        )
    }
}

data class KeycloakOAuthState(
    val accessToken: String,
    val accessExpiresIn: Long,
    val accessIssuedAt: Long,

    val refreshToken: String,
    val refreshExpiresIn: Long,
    val refreshIssuedAt: Long
) {
    fun isAccessTokenExpired(): Boolean {
        val expiresAt = accessExpiresIn + accessIssuedAt
        return expiresAt < System.currentTimeMillis()
    }

    fun isRefreshTokenExpired(): Boolean {
        val expiresAt = refreshExpiresIn + refreshIssuedAt
        return expiresAt < System.currentTimeMillis()
    }
}

abstract class KeycloakOAuth(private val context: ConnektContext) : TokenProvider {

    protected abstract var storedOAuthState: KeycloakOAuthState?

    override fun getToken(): String {
        val oAuthState = getOAuthState()
        return oAuthState.accessToken
    }

    fun getOAuthState(): KeycloakOAuthState {
        try {
            var oauthState = storedOAuthState
            if (oauthState == null) {
                oauthState = startBrowserAuthentication()
            }
            if (oauthState.isRefreshTokenExpired()) {
                oauthState = startBrowserAuthentication()
            }
            if (oauthState.isAccessTokenExpired()) {
                oauthState = refreshToken(oauthState.refreshToken)
            }
            storedOAuthState = oauthState
            return oauthState
        } catch (e: Exception) {
            throw RuntimeException("Could not obtain token", e)
        }
    }

    private fun startBrowserAuthentication(): KeycloakOAuthState {
        // TODO build auth link on params
        val authLink = "http://localhost:9081/realms/petclinic/protocol/openid-connect/auth" +
                "?client_id=sb" +
                "&redirect_uri=http://localhost:8080/callback" +
                "&response_type=code" +
                "&scope=openid"
        context.printer.println("Please open this link in browser: $authLink")

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
            server.stop()
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

        val response = credentialsRequestBuilder.executeRequest()
        val tokenResponse = response.toTokenResponse()
        return tokenResponse.toOauthState()
    }

    private fun refreshToken(refreshToken: String): KeycloakOAuthState {
        val requestBuilder = RequestBuilder(
            "POST",
            "http://localhost:9081/realms/petclinic/protocol/openid-connect/token",
            context
        ).apply {
            formData {
                field("client_id", "sb")
                field("grant_type", "refresh_token")
                field("refresh_token", refreshToken)
            }
        }

        val response = requestBuilder.executeRequest()
        val tokenResponse = response.toTokenResponse()
        return tokenResponse.toOauthState()
    }

    private fun RequestBuilder.executeRequest() = DefaultExecutionStrategy(context).executeRequest(this)

    private fun Response.toTokenResponse(): TokenResponse {
        val credentialsString = this.body?.string()

        // TODO ~~
        println("Credentials String: $credentialsString")

        requireNotNull(credentialsString) {
            "Unexpected credentials response: $this"
        }

        return context.jsonContext
            .objectMapper
            .readValue<TokenResponse>(credentialsString)
    }
}

interface TokenProvider {
    fun getToken(): String
}