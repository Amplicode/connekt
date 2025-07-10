package io.amplicode.connekt

import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.dsl.bearerAuth
import io.amplicode.connekt.test.utils.runScript
import io.amplicode.connekt.test.utils.server.TestServer
import kotlin.test.Test

class OAuthTest(server: TestServer) : TestWithServer(server) {
    @Test
    fun `do smth`() {
        runScript(1) {
            val keycloakOAuth by keycloakOAuth()

            GET("$host/foo") {
                bearerAuth(keycloakOAuth)
            }
        }
    }
}