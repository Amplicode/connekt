package io.amplicode.connekt.dsl

import okhttp3.OkHttpClient
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Adds a trusted X.509 certificate to the [OkHttpClient.Builder] for use in HTTPS connections.
 *
 * This function loads a certificate from the specified file, creates a temporary in-memory
 * [KeyStore] containing it, and configures the [OkHttpClient.Builder] to trust it. This is useful
 * when dealing with self-signed certificates or custom certificate authorities.
 *
 * @param certFile The [File] containing the X.509 certificate to be trusted.
 */
fun OkHttpClient.Builder.addX509Certificate(certFile: File) {
    // Load certificate
    val certificate: Certificate = certFile.inputStream()
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

/**
 * Configures the [OkHttpClient.Builder] to trust certificates from the provided [KeyStore] file.
 *
 * This function loads a [KeyStore] from the specified file using the given password, initializes a
 * [TrustManagerFactory] with it, and sets up an [SSLContext] using the trust managers derived from
 * that keystore. It then applies the resulting SSL socket factory and [X509TrustManager] to the client builder.
 *
 * This is useful when you want the HTTP client to trust a custom set of certificates, such as those
 * from a corporate or private certificate authority.
 *
 * @param keyStoreFile The file containing the keystore in a supported format (e.g., JKS, PKCS12).
 * @param keystorePassword The password used to unlock the keystore.
 */
fun OkHttpClient.Builder.addKeyStore(keyStoreFile: File, keystorePassword: String) {
    val keyStore: KeyStore = KeyStore.getInstance(
        keyStoreFile,
        keystorePassword.toCharArray()
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