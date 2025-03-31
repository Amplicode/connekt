package io.amplicode.connekt.test.utils

import io.ktor.http.ContentType
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.saveToFile
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.ConnectorType
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail
import io.ktor.util.toCharArray
import io.ktor.util.toMap
import kotlinx.coroutines.delay
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.File
import java.io.StringWriter
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.spec.PKCS8EncodedKeySpec
import kotlinx.coroutines.runBlocking
import okhttp3.internal.wait
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

/**
 * Used to run the test server without tests
 */
fun main() {
    val testServer = TestServer()
    println(testServer.host)
    println(testServer.hostHttps)
    runBlocking {
        delay(Duration.INFINITE)
    }
}

fun createTestServer(sslParams: ServerSslParams) = embeddedServer(
    factory = Netty,
    environment = applicationEnvironment {

    },
    configure = {
        configureConnectors(sslParams)
    },
    module = Application::module
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

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    routing {
        get("foo") {
            call.respondText("foo")
        }
        get("bar") {
            call.respondText("bar")
        }
        jsonApi()
        counterApi()
        echoApi()
    }
}

private fun Routing.jsonApi() {
    route("/json") {
        get("one-line-object") {
            call.respondText(
                //language=json
                """{"foo": "f", "bar": "b", "baz": 3}""",
                contentType = ContentType.Application.Json
            )
        }
        get("one-line-array") {
            call.respondText(
                //language=json
                """[1,2,3]""",
                contentType = ContentType.Application.Json
            )
        }
        get("invalid-object") {
            call.respondText(
                "foo bar",
                contentType = ContentType.Application.Json
            )
        }
    }
}

private fun Routing.echoApi() {
    // mirrors headers from request
    get("echo-headers") {
        val headersMap = call.request.headers.toMap()
        call.respond(headersMap)
    }
    post("echo-form-params") {
        val params = call.receiveParameters().toMap()
        call.respond(params)
    }
    post("echo-body") {
        val bodyText = call.receiveText()
        call.respondText(bodyText)
    }
    get("echo-query-params") {
        val queryParams = call.queryParameters.toMap()
        call.respond(queryParams)
    }
    get("echo-path/{...}") {
        val path = call.request
            .uri
            .substringAfter("echo-path", "")
        call.respond(path)
    }
    get("echo-text") {
        val text = call.parameters.getOrFail("text")
        call.respondText(text)
    }
}

private fun Routing.counterApi() {
    val counterService = object {
        private val counters =
            mutableMapOf<String, AtomicInteger>()

        fun getCounter(key: String?): AtomicInteger {
            return counters.getOrPut(key ?: "default", ::AtomicInteger)
        }
    }

    fun RoutingContext.getCounter() =
        counterService.getCounter(call.pathParameters["id"])

    route("counter/{id}") {
        get {
            call.respond(getCounter().get())
        }
        post("/inc") {
            call.respond(getCounter().incrementAndGet())
        }
        post("/reset") {
            getCounter().set(0)
        }
    }
}

class TestServer {
    val sslParams: ServerSslParams = ServerSslParams()
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

class ServerSslParams {
    val keystorePass = "123456"
    val privateKeyPassword = "foobar"
    val alias = "sampleAlias"

    val keyStoreFile = File("build/keystore.jks")
    val certPemFile = File("build/cert.pem")
    val keyPemFile = File("build/key.pem")


    val keyStore = buildKeyStore {
        certificate(alias) {
            password = privateKeyPassword
            domains = listOf("127.0.0.1", "0.0.0.0", "localhost")
        }
    }

    init {
        keyStore.saveToFile(keyStoreFile, keystorePass)
        convertJksToPem()
    }

    private fun convertJksToPem() {
        // Get certificate
        val certificate: Certificate = keyStore.getCertificate(alias)
        certPemFile.writeText(certificateToPem(certificate))

        // Get private key
        val privateKey = keyStore.getKey(alias, privateKeyPassword.toCharArray()) as PrivateKey
        keyPemFile.writeText(privateKeyToPem(privateKey))
    }

    private fun certificateToPem(certificate: Certificate): String {
        return StringWriter().use { writer ->
            JcaPEMWriter(writer).use { it.writeObject(certificate) }
            writer.toString()
        }
    }

    private fun privateKeyToPem(privateKey: PrivateKey): String {
        val keySpec = PKCS8EncodedKeySpec(privateKey.encoded)
        val keyFactory = KeyFactory.getInstance(privateKey.algorithm)
        val privateKeyFormatted = keyFactory.generatePrivate(keySpec)

        return StringWriter().use { writer ->
            JcaPEMWriter(writer).use { it.writeObject(privateKeyFormatted) }
            writer.toString()
        }
    }
}