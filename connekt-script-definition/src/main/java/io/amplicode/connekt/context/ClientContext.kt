package io.amplicode.connekt.context

import io.amplicode.connekt.CallLogger
import io.amplicode.connekt.Printer
import okhttp3.OkHttpClient
import java.io.Closeable
import java.util.concurrent.TimeUnit

interface ClientContext : Closeable {
    fun getClient(configure: ClientConfigurer): OkHttpClient
    var globalConfigurer: ClientConfigurer
}

class ClientContextImpl(
    printer: Printer,
    override var globalConfigurer: ClientConfigurer = NoopClientConfigurer
) : ClientContext {

    private val clients = mutableSetOf<OkHttpClient>()

    private val defaultClient = OkHttpClient.Builder()
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .addNetworkInterceptor(CallLogger(printer))
        .buildAndRegister()

    override fun getClient(configure: ClientConfigurer): OkHttpClient {
        val client = defaultClient.newBuilder()
        client.globalConfigurer()
        client.configure()
        return client.buildAndRegister()
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

typealias ClientConfigurer = OkHttpClient.Builder.() -> Unit

val NoopClientConfigurer: ClientConfigurer = { }