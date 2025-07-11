package io.amplicode.connekt

import io.amplicode.connekt.context.persistence.InMemoryStorage
import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.dsl.bearerAuth
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

        runScript(0, context = testConnektContext(storage)) {
            val keycloakOAuth = keycloakOAuth()

            GET("$host/foo") {
                bearerAuth(keycloakOAuth)
            }
        }

        runScript(0, context = testConnektContext(storage)) {
            val keycloakOAuth = keycloakOAuth()

            GET("$host/foo") {
                bearerAuth(keycloakOAuth)
            }
        }
    }
}