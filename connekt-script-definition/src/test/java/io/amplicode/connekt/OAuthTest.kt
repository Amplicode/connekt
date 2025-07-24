package io.amplicode.connekt

import io.amplicode.connekt.context.persistence.InMemoryStorage
import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.dsl.KeycloakOAuthParameters
import io.amplicode.connekt.dsl.bearerAuth
import io.amplicode.connekt.dsl.oauth
import io.amplicode.connekt.test.utils.components.testConnektContext
import io.amplicode.connekt.test.utils.runScript
import io.amplicode.connekt.test.utils.server.TestServer
import kotlin.test.Ignore
import kotlin.test.Test

class OAuthTest(server: TestServer) : TestWithServer(server) {

    @Test
    @Ignore
    fun `do smth`() {
        val storage = InMemoryStorage()

        val oAuthParameters = KeycloakOAuthParameters(
            "http://localhost:9081",
            "petclinic",
            "openid-connect",
            "sb",
            null,
            "openid",
            8080,
            "/callback"
        )
        runScript(1, context = testConnektContext(storage)) {
            val keycloakOAuth by oauth(oAuthParameters)

            GET("$host/foo") {
                bearerAuth(keycloakOAuth.accessToken)
            }
        }

        runScript(1, context = testConnektContext(storage)) {
            val keycloakOAuth by oauth(oAuthParameters)

            GET("$host/foo") {
                bearerAuth(keycloakOAuth.accessToken)
            }
        }
    }
}
