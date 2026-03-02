package io.amplicode.connekt.integration

import io.amplicode.connekt.integration.server.IntegrationServerExtension
import io.amplicode.connekt.integration.server.IntegrationTestServer
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File

@ExtendWith(IntegrationServerExtension::class)
abstract class IntegrationTest {

    protected val host: String
        get() = serverRef.host

    protected fun scriptFile(name: String): File {
        val resource = javaClass.classLoader.getResource("scripts/$name")
            ?: error("Script resource not found: scripts/$name")
        return File(resource.toURI())
    }

    companion object {

        private lateinit var serverRef: IntegrationTestServer

        /**
         * Registered alongside the class-level [IntegrationServerExtension] to capture the
         * server reference after it has been started.
         */
        @JvmField
        @RegisterExtension
        val serverCapture: BeforeAllCallback = BeforeAllCallback { context ->
            serverRef = IntegrationServerExtension.getServer(context)
        }
    }
}
