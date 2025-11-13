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

    /**
     * Stored auth used to get new access tokens.
     * If provided Auth is `null` then browser authentication will be started.
     */
    var storedAuthProvider: () -> Auth? = { null }

    private val jsonContext = connektContext.jsonContext

    private fun refresh(auth: Auth): Auth {
        callListeners {
            it.beforeRefreshTokenCall()
        }
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
        callListeners {
            it.onTokenRefreshed(newAuth)
        }
        return newAuth
    }

    private fun authorize(): Auth {
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

        val authCode = waitForAuthCode(redirectUri, authUrl)

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
        callListeners { it.onAuthorized(auth) }
        return auth
    }

    /**
     * @param withAccessTokenRefresh if true then access token from stored auth will also be refreshed
     * even if it is not expired.
     */
    fun getAuth(withAccessTokenRefresh: Boolean = false): Auth {
        val storedAuth = storedAuthProvider()
        return when {
            storedAuth == null -> authorize()
            storedAuth.isRefreshTokenExpired() -> authorize()
            storedAuth.isAccessTokenExpired() -> refresh(storedAuth)
            withAccessTokenRefresh -> refresh(storedAuth)
            else -> storedAuth
        }
    }

    override fun execute(): Auth {
        // Always refresh the token if the Runner is called directly
        return getAuth(true)
    }

    private fun waitForAuthCode(
        redirectUri: String,
        authUrl: String
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
        callListeners { it.onWaitAuthCode(authUrl) }

        val code = future.get()
        server.stop(0)
        return code
    }

    private fun callListeners(action: (Listener) -> Unit) =
        listeners.asReversed().forEach(action)

    private val listeners: MutableList<Listener> = mutableListOf()

    fun addListener(listener: Listener) {
        listeners += listener
    }

    interface Listener {
        fun onAuthorized(auth: Auth) {}

        /**
         * Executed when authenticator is turned into waiting for authorization code state.
         */
        fun onWaitAuthCode(authUrl: String) {}

        fun beforeRefreshTokenCall() {}
        fun onTokenRefreshed(auth: Auth) {}
    }
}

private fun Auth.isAccessTokenExpired() = System.currentTimeMillis() >= accessTokenExpirationTs
private fun Auth.isRefreshTokenExpired() = System.currentTimeMillis() >= refreshTokenExpirationTs