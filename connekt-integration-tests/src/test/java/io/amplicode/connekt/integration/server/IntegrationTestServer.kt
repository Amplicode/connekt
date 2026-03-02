package io.amplicode.connekt.integration.server

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.resources.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking

class IntegrationTestServer {
    private val server: EmbeddedServer<*, *> = createIntegrationTestServer()

    init {
        server.start(wait = false)
    }

    val port = runBlocking {
        server.engine.resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
    }

    val host: String = "http://localhost:$port"

    fun stop() {
        server.stop()
    }
}

private fun createIntegrationTestServer() = embeddedServer(
    factory = Netty,
    environment = applicationEnvironment {
    },
    configure = {
        connector {
            port = 0
        }
    },
    module = {
        install(ContentNegotiation) {
            json()
        }
        install(Resources)
        configureIntegrationRouting()
    }
)
