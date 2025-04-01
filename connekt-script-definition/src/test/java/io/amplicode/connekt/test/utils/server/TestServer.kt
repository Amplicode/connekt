package io.amplicode.connekt.test.utils.server

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking

class TestServer(val sslParams: ServerSslParams = ServerSslParams()) {
    private val server: EmbeddedServer<*, *> = createTestServer(sslParams)

    init {
        server.start(wait = false)
    }

    val port = runBlocking {
        server.engine.resolvedConnectors().first { it.type == ConnectorType.HTTP }.port
    }

    val portHttps = runBlocking {
        server.engine.resolvedConnectors().first { it.type == ConnectorType.HTTPS }.port
    }

    val host = "http://localhost:$port"
    val hostHttps = "https://localhost:$portHttps"

    fun stop() {
        server.stop()
    }
}

private fun createTestServer(sslParams: ServerSslParams) = embeddedServer(
    factory = Netty,
    environment = applicationEnvironment {

    },
    configure = {
        configureConnectors(sslParams)
    },
    module = {
        install(ContentNegotiation) {
            json()
        }

        configureRouting()
    }
)

private fun NettyApplicationEngine.Configuration.configureConnectors(sslParams: ServerSslParams) {
    connector {
        port = 0
    }

    sslConnector(
        keyStore = sslParams.keyStore,
        keyAlias = sslParams.alias,
        keyStorePassword = sslParams.keystorePass::toCharArray,
        privateKeyPassword = sslParams.privateKeyPassword::toCharArray
    ) {
        port = 0
        keyStorePath = sslParams.keyStoreFile
    }
}
