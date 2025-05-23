package io.amplicode.connekt

import io.amplicode.connekt.dsl.GET
import io.amplicode.connekt.dsl.addX509Certificate
import io.amplicode.connekt.dsl.addKeyStore
import io.amplicode.connekt.test.utils.server.ServerSslParams
import io.amplicode.connekt.test.utils.server.TestServer
import io.amplicode.connekt.test.utils.asUnit
import io.amplicode.connekt.test.utils.runScript
import org.junit.jupiter.api.Test
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.test.assertEquals

class SslTest(server: TestServer) : TestWithServer(server) {

    private val hostHttps: String = server.hostHttps
    private val sslParams: ServerSslParams = server.sslParams

    @Test
    fun `self-signed jks`() = runScript {
        configureClient {
            val keyStore: KeyStore = KeyStore.getInstance(
                sslParams.keyStoreFile,
                sslParams.keystorePass.toCharArray()
            )

            val trustManagerFactory: TrustManagerFactory = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply {
                    init(keyStore)
                }

            val sslContext: SSLContext = SSLContext.getInstance("TLS").apply {
                init(null, trustManagerFactory.trustManagers, null)
            }

            val trustManager: X509TrustManager = trustManagerFactory
                .trustManagers
                .first { it is X509TrustManager } as X509TrustManager

            sslSocketFactory(
                sslContext.socketFactory,
                trustManager
            )
        }

        GET("$hostHttps/foo") then {
            assertEquals("foo", body?.string())
        }
    }.asUnit()

    @Test
    fun `self-signed jks via ext function`() = runScript {
        configureClient {
            addKeyStore(
                sslParams.keyStoreFile,
                sslParams.keystorePass
            )
        }

        GET("$hostHttps/foo") then {
            assertEquals("foo", body?.string())
        }
    }.asUnit()

    @Test
    fun `self-signed pem`() = runScript {
        configureClient {
            // Load certificate
            val certificate: Certificate = sslParams.certPemFile.inputStream()
                .use(CertificateFactory.getInstance("X.509")::generateCertificate)

            // Create a KeyStore containing the trusted certificate
            val keyStore: KeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null) // Initialize empty keystore
                setCertificateEntry("trusted_cert", certificate)
            }

            // Create a TrustManager that trusts the certificate
            val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply {
                    init(keyStore)
                }
                .trustManagers
                .first() as X509TrustManager

            // Initialize SSLContext with the TrustManager
            val sslContext: SSLContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustManager), SecureRandom())
            }

            sslSocketFactory(sslContext.socketFactory, trustManager)
        }

        GET("$hostHttps/foo") then {
            assert(body!!.string() == "foo")
        }
    }.asUnit()

    @Test
    fun `self-signed pem via ext function`() = runScript {
        configureClient {
            addX509Certificate(sslParams.certPemFile)
        }

        GET("$hostHttps/foo") then {
            assertEquals("foo", body?.string())
        }
    }.asUnit()
}