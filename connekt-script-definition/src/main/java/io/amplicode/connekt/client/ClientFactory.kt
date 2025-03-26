package io.amplicode.connekt.client

import io.amplicode.connekt.CallLogger
import io.amplicode.connekt.console.Printer
import okhttp3.OkHttpClient
import java.io.Closeable
import java.util.concurrent.TimeUnit

interface ClientFactory : Closeable {
    fun getClient(configure: ClientConfigurer): OkHttpClient
}

class ClientFactoryImpl(printer: Printer) : ClientFactory {

    private val clients = mutableSetOf<OkHttpClient>()

    private val defaultClient = OkHttpClient.Builder()
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .addNetworkInterceptor(CallLogger(printer))
        .buildAndRegister()

    override fun getClient(configure: ClientConfigurer): OkHttpClient {
        val client = defaultClient.newBuilder()
        return client.configure().buildAndRegister()
    }

    override fun close() {
        for (client in clients) {
            runCatching {
                client.connectionPool.evictAll()
            }
        }
    }

    private fun OkHttpClient.Builder.buildAndRegister(): OkHttpClient =
        build().also(clients::add)
}

typealias ClientConfigurer = OkHttpClient.Builder.() -> OkHttpClient.Builder

val NoopClientConfigurer: ClientConfigurer = { this }