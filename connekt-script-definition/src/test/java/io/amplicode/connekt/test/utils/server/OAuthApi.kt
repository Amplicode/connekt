package io.amplicode.connekt.test.utils.server

import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal fun Routing.oauthApi() {
    get<OAuth.Auth> {
        val url = it.redirect_uri!!
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("code", "42")
            .build()
        val request = Request.Builder()
            .method("GET", null)
            .url(url)
            .build()
        OkHttpClient().newCall(request).execute()
    }

    post<OAuth.Token> {
        val grantType = call.receiveParameters()["grant_type"] ?: error("grant_type is required")
        val response = when (grantType) {
            "authorization_code" -> {
                AccessTokenResponse(
                    "foo",
                    Int.MAX_VALUE.toLong(),
                    "bar",
                    "baz",
                    "qux",
                    Int.MAX_VALUE.toLong(),
                    Int.MAX_VALUE.toLong()
                )
            }

            "refresh_token" -> {
                RefreshTokenResponse(
                    "foo",
                    Int.MAX_VALUE.toLong(),
                    "baz",
                    "qux",
                    Int.MAX_VALUE.toLong(),
                    Int.MAX_VALUE.toLong()
                )
            }

            else -> error("Unsupported grant type: $grantType")
        }
        call.respond(response)
    }
}

@Suppress("PropertyName")
@Resource("/oauth")
class OAuth {

    @Resource("auth")
    class Auth(
        val parent: OAuth = OAuth(),
        val response_type: String? = null,
        val redirect_uri: String? = null,
        val client_id: String? = null
    )

    @Resource("token")
    class Token()
}

// TODO copy-pasted from OAuth implementation instead of being re-used.
//  Need to introduce an `impl` module not visible from DSL but visible from tests
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

@Serializable
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

@Serializable
@Suppress("PropertyName")
private data class RefreshTokenResponse(
    val access_token: String,
    val expires_in: Long,
    val scope: String,
    val token_type: String,
    override val refresh_token_expires_in: Long?,
    override val refresh_expires_in: Long?,
) : RefreshTokenInfo