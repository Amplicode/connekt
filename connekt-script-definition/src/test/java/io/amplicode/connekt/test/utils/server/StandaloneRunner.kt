package io.amplicode.connekt.test.utils.server

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration


/**
 * Used to run the test server without tests
 */
fun main() {
    val sslParams = ServerSslParams()
    val testServer = TestServer(sslParams)

    printServerParams(testServer, sslParams)

    runBlocking {
        delay(Duration.INFINITE)
    }
}

private fun printServerParams(
    testServer: TestServer,
    sslParams: ServerSslParams
) {
    val serverParams = sequence {
        with(testServer) {
            yield("HTTP host" to host)
            yield("HTTPS host" to hostHttps)
        }

        with(sslParams) {
            yield("JKS" to keyStoreFile.absolutePath)
            yield("JKS pass" to keystorePass)
            yield("PEM" to certPemFile.absolutePath)
        }
    }

    println("Server Params")
    serverParams.forEach { (param, value) ->
        println("  $param: $value")
    }
}