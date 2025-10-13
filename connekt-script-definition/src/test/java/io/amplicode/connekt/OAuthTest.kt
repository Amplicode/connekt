package io.amplicode.connekt

import io.amplicode.connekt.auth.OAuthRunner
import io.amplicode.connekt.context.persistence.InMemoryStorage
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
        runScript(1, testConnektContext(storage), myScript)
        runScript(1, testConnektContext(storage), myScript)
    }

    @Test
    fun `test synthetic oauth`() {
        val storage = InMemoryStorage()
        var codeAuthCalls = 0
        val myScript: ConnektBuilder.() -> Unit = {
            val keycloakOAuth by oauth(
                resourcePath(OAuth.Auth()),
                "sb",
                null,
                "openid",
                resourcePath(OAuth.Token()),
                "http://localhost:8080/callback"
            ).also {
                it.addListener(object : OAuthRunner.Listener {
                    override fun onWaitAuthCode(authUrl: String) {
                        codeAuthCalls++
                        val request = Request.Builder()
                            .url(authUrl)
                            .build()
                        OkHttpClient().newCall(request)
                            .execute()
                    }
                })
            }

            GET("$host/foo") {
                bearerAuth(keycloakOAuth.accessToken)
            }
        }
        runScript(0, testConnektContext(storage), myScript)
        runScript(1, testConnektContext(storage), myScript)
        runScript(1, testConnektContext(storage), myScript)
        assertEquals(
            1,
            codeAuthCalls,
            "Code auth calls must be called only once"
        )
    }
}
