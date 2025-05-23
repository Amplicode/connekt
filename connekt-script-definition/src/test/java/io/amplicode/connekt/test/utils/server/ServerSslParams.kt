package io.amplicode.connekt.test.utils.server

import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.saveToFile
import io.ktor.util.toCharArray
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.File
import java.io.StringWriter
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.spec.PKCS8EncodedKeySpec

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