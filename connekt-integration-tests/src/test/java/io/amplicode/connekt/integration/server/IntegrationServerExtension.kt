package io.amplicode.connekt.integration.server

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource

class IntegrationServerExtension : BeforeAllCallback, AfterAllCallback {

    override fun beforeAll(context: ExtensionContext) {
        getOrCreateServer(context)
    }

    override fun afterAll(context: ExtensionContext) {
        // The server is stored as a CloseableResource in the global namespace,
        // so JUnit will close it automatically when the root context is closed.
        // Nothing to do here explicitly.
    }

    companion object {
        private const val SERVER_KEY = "integration-test-server"

        fun getServer(context: ExtensionContext): IntegrationTestServer {
            return getOrCreateServer(context).server
        }

        private fun getOrCreateServer(context: ExtensionContext): IntegrationTestServerResource {
            return context.root
                .getStore(Namespace.GLOBAL)
                .getOrComputeIfAbsent(
                    SERVER_KEY,
                    { _ -> IntegrationTestServerResource(IntegrationTestServer()) },
                    IntegrationTestServerResource::class.java
                )
        }
    }
}

class IntegrationTestServerResource(val server: IntegrationTestServer) : CloseableResource {
    override fun close() {
        server.stop()
    }
}
