package io.amplicode.connekt.auth

import com.sun.net.httpserver.HttpServer
import io.amplicode.connekt.Executable
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.dsl.RequestBuilder
import io.amplicode.connekt.dsl.doRead
import io.amplicode.connekt.println
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import java.util.concurrent.CompletableFuture

private data class AccessTokenResponse(
    val access_token: String,
    val expires_in: Long,
    val refresh_token: String,
    val scope: String,
    val token_type: String,
    val refresh_token_expires_in: Long
)

private data class RefreshTokenResponse(
    val access_token: String,
    val expires_in: Long,
    val scope: String,
    val token_type: String,
    val refresh_token_expires_in: Long
)

class GoogleAuthRunner(
    private val authorizeEndpoint: String,
    private val clientId: String,
    private val scope: String,
    private val tokenEndpoint: String,
    private val clientSecret: String,
    private val redirectUri: String,
    private val connektContext: ConnektContext
) : AuthRunner, Executable<Auth>() {

    override fun refresh(auth: Auth): Auth {
        val response = connektContext.executionContext.getExecutionStrategy(this, connektContext)
            .executeRequest(
                RequestBuilder("POST", tokenEndpoint, connektContext).apply {
                    headers("Content-Type" to "application/x-www-form-urlencoded")
                    body(
                        "client_id=$clientId&client_secret=$clientSecret&refresh_token=${auth.refreshToken}&grant_type=refresh_token"
                    )
                }
            )

        val refreshTokenResponse = connektContext.jsonContext.getReadContext(response).doRead<RefreshTokenResponse>("$")

        println(refreshTokenResponse)

        return Auth(
            refreshTokenResponse.access_token,
            auth.refreshToken,
            System.currentTimeMillis() + refreshTokenResponse.expires_in * 1000,
            System.currentTimeMillis() + refreshTokenResponse.refresh_token_expires_in * 1000
        )
    }

    override fun authorize(): Auth = execute()

    override fun execute(): Auth {
        val redirectUrl = URL(redirectUri)

        val redirectUri = java.net.URLEncoder.encode(
            redirectUri,
            "UTF-8"
        )


        val authUrl = "$authorizeEndpoint?client_id=$clientId&response_type=code&redirect_uri=$redirectUri&scope=${
            java.net.URLEncoder.encode(
                scope,
                "UTF-8"
            )
        }&access_type=offline&prompt=consent"

        connektContext.printer.println(authUrl)

        val authCode = waitForAuthCode(redirectUrl.port, redirectUrl.path)

        val response = connektContext.executionContext.getExecutionStrategy(this, connektContext)
            .executeRequest(
                RequestBuilder("POST", tokenEndpoint, connektContext).apply {
                    headers("Content-Type" to "application/x-www-form-urlencoded")
                    body(
                        "client_id=$clientId&client_secret=$clientSecret&code=${
                            java.net.URLEncoder.encode(
                                authCode,
                                "UTF-8"
                            )
                        }&redirect_uri=${redirectUri}&grant_type=authorization_code"
                    )
                }
            )

        val accessTokenResponse = connektContext.jsonContext.getReadContext(response).doRead<AccessTokenResponse>("$")

        println(accessTokenResponse)

        return Auth(
            accessTokenResponse.access_token,
            accessTokenResponse.refresh_token,
            System.currentTimeMillis() + accessTokenResponse.expires_in * 1000,
            System.currentTimeMillis() + accessTokenResponse.refresh_token_expires_in * 1000
        )
    }
}

private fun waitForAuthCode(port: Int = 8080, path: String): String {
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

    val code = future.get() // блокируется, пока не получит код
    server.stop(0)
    return code
}

