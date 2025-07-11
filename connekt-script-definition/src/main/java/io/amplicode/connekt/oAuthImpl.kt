package io.amplicode.connekt

import com.fasterxml.jackson.module.kotlin.readValue
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.dsl.RequestBuilder
import io.ktor.http.*
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
)

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

internal val KeycloakOAuthParameters.tokenUrl: String
    get() = "$serverBaseUrl/realms/$realm/protocol/$protocol/token"

abstract class KeycloakOAuth(
    private val context: ConnektContext,
    private val oAuthParams: KeycloakOAuthParameters
) : TokenProvider {

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
        return tokenResponse.toOauthState()
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

    private fun refreshToken(refreshToken: String): KeycloakOAuthState {
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
        return tokenResponse.toOauthState()
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

    fun KeycloakOAuthState.isRefreshTokenExpired(): Boolean {
        val expiresAt = refreshExpiresIn * 1000 + refreshIssuedAt
        val timeToLive = expiresAt - System.currentTimeMillis()
        context.printer.debugln("Refresh token time to live: ${timeToLive}ms")
        return timeToLive < 0
    }

    fun KeycloakOAuthState.isAccessTokenExpired(): Boolean {
        val expiresAt = accessExpiresIn * 1000 + accessIssuedAt
        val timeToLive = expiresAt - System.currentTimeMillis()
        context.printer.debugln("Access token time to live: ${timeToLive}ms")
        return timeToLive < 0
    }
}

interface TokenProvider {
    fun getToken(): String
}