package io.amplicode.connekt

import io.amplicode.connekt.context.CookiesContextImpl
import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.dsl.POST
import io.amplicode.connekt.test.utils.components.testConnektContext
import io.amplicode.connekt.test.utils.runScript
import io.amplicode.connekt.test.utils.server.TestServer
import kotlin.io.path.createTempFile
import kotlin.test.Test

class CookiesTest(server: TestServer) : TestWithServer(server) {

    @Test
    fun `check cookies are stored into file`() {
        val storageFile = createTempFile("connekt-cookie-test-")
        val context = testConnektContext(
            cookiesContextFactory = { CookiesContextImpl(storageFile, it) }
        )

        runScript(
            context = context,
        ) {
            POST("$host/cookies/foo") {

            }
            GET("$host/foo") {

            }
        }
    }
}