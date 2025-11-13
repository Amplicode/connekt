package io.amplicode.connekt

import io.amplicode.connekt.auth.Auth
import io.amplicode.connekt.auth.OAuthRunner
import io.amplicode.connekt.context.persistence.InMemoryStorage
import io.amplicode.connekt.context.persistence.Storage
import io.amplicode.connekt.dsl.AuthExtensions
import io.amplicode.connekt.dsl.ConnektBuilder
import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.dsl.bearerAuth
import io.amplicode.connekt.test.utils.components.testConnektContext
import io.amplicode.connekt.test.utils.runScript
import io.amplicode.connekt.test.utils.server.OAuth
import io.amplicode.connekt.test.utils.server.TestServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class OAuthTest(server: TestServer) : TestWithServer(server) {

    @Test
    @EnabledIfSystemProperty(named = "oauth.manual.test", matches = "true")
    fun `do smth`() {
        val storage = InMemoryStorage()
        val myScript: ConnektBuilder.() -> Unit = {
            val keycloakOAuth by oauth(
                "http://localhost:9081/realms/petclinic/protocol/openid-connect/auth",
                "sb",
                null,
                "openid",
                "http://localhost:9081/realms/petclinic/protocol/openid-connect/token",
                "http://localhost:8080/callback"
            )

            GET("$host/foo") {
                bearerAuth(keycloakOAuth.accessToken)
            }
        }
        runScript(0, testConnektContext(storage), myScript)
        runScript(0, testConnektContext(storage), myScript)
        runScript(1, testConnektContext(storage), myScript)
        runScript(1, testConnektContext(storage), myScript)
    }

    @Test
    fun `test synthetic oauth`() {
        val storage = InMemoryStorage()
        val myScript: ConnektBuilder.() -> Unit = {
            val keycloakOAuth by oauth(
                resourcePath(OAuth.Auth()),
                "sb",
                null,
                "openid",
                resourcePath(OAuth.Token()),
                "http://localhost:8080/callback"
            )

            GET("$host/foo") {
                bearerAuth(keycloakOAuth.accessToken)
            }
        }

        class AuthRunnerActionsCounter {
            var browserAuthCalls: Int = 0
            var refreshTokenCalls: Int = 0
            var authCalls: Int = 0

            fun assert(
                browserAuthCalls: Int,
                refreshTokenCalls: Int,
                authCalls: Int
            ) {
                assertEquals(browserAuthCalls, this.browserAuthCalls)
                assertEquals(refreshTokenCalls, this.refreshTokenCalls)
                assertEquals(authCalls, this.authCalls)
            }
        }

        val listener = object : OAuthRunner.Listener {
            var counter = AuthRunnerActionsCounter()
                private set

            val obtainedAuthentications = ArrayDeque<Auth>()

            fun resetCounter() {
                counter = AuthRunnerActionsCounter()
            }

            override fun onWaitAuthCode(authUrl: String) {
                counter.browserAuthCalls++
            }

            override fun onAuthorized(auth: Auth) {
                counter.authCalls++
                obtainedAuthentications.addLast(auth)
            }

            override fun beforeRefreshTokenCall() {
                counter.refreshTokenCalls++
            }
        }

        fun runScript(number: Int) = runScript(
            number,
            createContext(storage, listener),
            myScript
        )

        // Call auth. Expect: +1 browser auth, +1 auth
        runScript(0)
        listener.counter.assert(1, 0, 1)
        listener.resetCounter()
        // Call request. Expect state unchanged
        runScript(1)
        listener.counter.assert(0, 0, 0)
        listener.resetCounter()
        // Call request again. Expect state unchanged
        runScript(1)
        listener.counter.assert(0, 0, 0)
        listener.resetCounter()
        // Call auth again. Expect token refresh
        runScript(0)
        listener.counter.assert(0, 1, 0)
        listener.resetCounter()
        // Call request again. Expect state unchanged
        runScript(1)
        listener.counter.assert(0, 0, 0)
        listener.resetCounter()
        listener.obtainedAuthentications
            .zipWithNext()
            .forEach { (a, b) -> assertNotEquals(a, b) }
    }

    /**
     * Creates a context with a custom OAuth runner that makes a code request by itself
     * instead of delegating it to the user.
     *
     * @param storage the storage to be used between script runs.
     */
    fun createContext(
        storage: Storage,
        oauthListener: OAuthRunner.Listener
    ) = testConnektContext(storage = storage) {
        it.authExtensions = UserlessOAuthExtensions(it.authExtensions, oauthListener)
    }

    class UserlessOAuthExtensions(
        private val original: AuthExtensions,
        private val listener: OAuthRunner.Listener,
    ) : AuthExtensions by original {
        override fun oauth(
            authorizeEndpoint: String,
            clientId: String,
            clientSecret: String?,
            scope: String,
            tokenEndpoint: String,
            redirectUri: String
        ): OAuthRunner {
            val authRunner = original.oauth(
                authorizeEndpoint,
                clientId,
                clientSecret,
                scope,
                tokenEndpoint,
                redirectUri
            )
            // Do auth request as it was the user
            authRunner.addListener(object : OAuthRunner.Listener {
                override fun onWaitAuthCode(authUrl: String) {
                    val request = Request.Builder()
                        .url(authUrl)
                        .build()
                    OkHttpClient().newCall(request)
                        .execute()
                }
            })
            authRunner.addListener(listener)
            return authRunner
        }
    }
}
