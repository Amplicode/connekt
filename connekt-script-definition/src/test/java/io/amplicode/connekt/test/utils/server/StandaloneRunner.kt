package io.amplicode.connekt.test.utils.server

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    val infoAsJson = json.encodeToString(
        ServerInfo(
            host = testServer.host,
            hostHttps = testServer.host,
            cert = sslParams.certPemFile.absolutePath,
            keystore = sslParams.keyStoreFile.absolutePath,
            keystorePass = sslParams.keystorePass
        )
    )

    println("Server Params")
    println(infoAsJson)
}

private val json = Json { prettyPrint = true }

@Serializable
data class ServerInfo(
    val host: String,
    val hostHttps: String,
    val cert: String,
    val keystorePass: String,
    val keystore: String
)