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

fun OkHttpClient.Builder.applyCertificate(certFile: File): OkHttpClient.Builder {
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
    return this
}

fun OkHttpClient.Builder.applyKeyStore(keyStoreFile: File, keystorePassword: String): OkHttpClient.Builder {
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

    return this
}