package io.amplicode.connekt.auth

import com.sun.net.httpserver.HttpServer
import io.amplicode.connekt.Printer
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.context.execution.Executable
import io.amplicode.connekt.dsl.RequestBuilder
import io.amplicode.connekt.dsl.doRead
import io.amplicode.connekt.println
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.CompletableFuture

@Suppress("PropertyName")
interface RefreshTokenInfo {
    // Google
    val refresh_token_expires_in: Long?

    // Keycloak
    val refresh_expires_in: Long?

    fun getRefreshExpiresUnified(): Long {
        return refresh_token_expires_in
            ?: refresh_expires_in
            ?: error("No refresh expiration field")
    }
}

@Suppress("PropertyName")
private data class AccessTokenResponse(
    val access_token: String,
    val expires_in: Long,
    val refresh_token: String,
    val scope: String,
    val token_type: String,
    override val refresh_token_expires_in: Long?,
    override val refresh_expires_in: Long?
) : RefreshTokenInfo

@Suppress("PropertyName")
private data class RefreshTokenResponse(
    val access_token: String,
    val expires_in: Long,
    val scope: String,
    val token_type: String,
    override val refresh_token_expires_in: Long?,
    override val refresh_expires_in: Long?,
) : RefreshTokenInfo

class OAuthRunner(
    private val authorizeEndpoint: String,
    private val clientId: String,
    private val scope: String,
    private val tokenEndpoint: String,
    private val clientSecret: String?,
    private val redirectUri: String,
    private val connektContext: ConnektContext
) : Executable<Auth>() {

    private val jsonContext = connektContext.jsonContext

    fun refresh(auth: Auth): Auth {
        val response = connektContext.executionContext
            .getExecutionStrategy(this)
            .executeRequest(
                connektContext,
                RequestBuilder(
                    "POST",
                    tokenEndpoint,
                    connektContext
                ).apply {
                    headers("Content-Type" to "application/x-www-form-urlencoded")

                    formData {
                        field("client_id", clientId)

                        if (clientSecret != null) {
                            field("client_secret", clientSecret)
                        }

                        field("refresh_token", auth.refreshToken)
                        field("grant_type", "refresh_token")
                    }
                }
            )

        if (response.code != 200) {
            connektContext.printer.println("Failed to refresh token", color = Printer.Color.RED)
            throw RuntimeException("Execution exception")
        }

        val refreshTokenResponse = jsonContext
            .getReadContext(response)
            .doRead<RefreshTokenResponse>("$")

        println(refreshTokenResponse)

        val newAuth = Auth(
            refreshTokenResponse.access_token,
            auth.refreshToken,
            System.currentTimeMillis() + refreshTokenResponse.expires_in * 1000,
            System.currentTimeMillis() + refreshTokenResponse.getRefreshExpiresUnified() * 1000
        )
        fireOnResult(newAuth)
        return newAuth
    }

    fun authorize(): Auth = execute()

    override fun execute(): Auth {
        val authUrl = "$authorizeEndpoint?" +
                "client_id=$clientId" +
                "&response_type=code" +
                "&redirect_uri=${redirectUri}" +
                "&scope=${
                    URLEncoder.encode(
                        scope,
                        "UTF-8"
                    )
                }" +
                "&access_type=offline" +
                "&prompt=consent"

        connektContext.printer.println(authUrl)

        val authCode = waitForAuthCode(
            redirectUri,
            onStart = { fireWaitingAuthCode(authUrl) }
        )

        val response = connektContext.executionContext
            .getExecutionStrategy(this)
            .executeRequest(
                connektContext,
                RequestBuilder("POST", tokenEndpoint, connektContext).apply {
                    headers("Content-Type" to "application/x-www-form-urlencoded")

                    formData {
                        field("client_id", clientId)

                        if (clientSecret != null) {
                            field("client_secret", clientSecret)
                        }

                        field("code", authCode)
                        field("redirect_uri", redirectUri)
                        field("grant_type", "authorization_code")
                    }
                }
            )

        require(response.code == 200) {
            "Failed to get access token: $response"
        }
        val accessTokenResponse = jsonContext.getReadContext(response).doRead<AccessTokenResponse>("$")

        val auth = Auth(
            accessTokenResponse.access_token,
            accessTokenResponse.refresh_token,
            System.currentTimeMillis() + accessTokenResponse.expires_in * 1000,
            System.currentTimeMillis() + accessTokenResponse.getRefreshExpiresUnified() * 1000
        )
        fireOnResult(auth)
        return auth
    }

    private fun fireOnResult(auth: Auth) {
        callListeners { it.onResult(auth) }
    }

    private fun fireWaitingAuthCode(authUrl: String) {
        callListeners { it.onWaitAuthCode(authUrl) }
    }

    private fun callListeners(action: (Listener) -> Unit) =
        listeners.forEach(action)

    private val listeners: MutableList<Listener> = mutableListOf()

    fun addListener(listener: Listener) {
        listeners += listener
    }

    interface Listener {
        fun onResult(auth: Auth) {}
        fun onWaitAuthCode(authUrl: String) {}
    }
}

private fun waitForAuthCode(
    redirectUri: String,
    onStart: () -> Unit
): String {
    val redirectUrl = URL(redirectUri)
    val port = redirectUrl.port
    val path = redirectUrl.path

    val future = CompletableFuture<String>()
    val server = HttpServer.create(InetSocketAddress(port), 0)

    server.createContext(path) { exchange ->
        val uri: URI = exchange.requestURI
        val query = uri.rawQuery ?: ""
        val params = query.split("&").associate {
            val (k, v) = it.split("=")
            k to v
        }

        val code = params["code"]

        val response = if (code != null) {
            "Authorization successful! You can close this tab."
        } else {
            future.completeExceptionally(IllegalArgumentException("No 'code' in query"))
            "Missing 'code' parameter"
        }

        exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
        exchange.responseBody.use { it.write(response.toByteArray()) }

        if (code != null) {
            future.complete(code)
        }
    }

    server.start()
    println("Waiting for authorization ...")
    onStart()

    val code = future.get() // блокируется, пока не получит код
    server.stop(0)
    return code
}