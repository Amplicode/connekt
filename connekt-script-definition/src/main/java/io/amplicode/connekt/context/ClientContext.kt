package io.amplicode.connekt.context

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.toJavaDuration

interface ClientContext : Closeable {
    fun getClient(configure: ClientConfigurer): OkHttpClient
    var globalConfigurer: ClientConfigurer
}

class ClientContextImpl(
    connektInterceptor: Interceptor,
    override var globalConfigurer: ClientConfigurer = NoopClientConfigurer
) : ClientContext {

    private val clients = mutableSetOf<OkHttpClient>()

    private val defaultClient = OkHttpClient.Builder()
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .addNetworkInterceptor(connektInterceptor)
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

internal data class TimeoutSettings(
    val connect: Duration? = null,
    val read: Duration? = null,
    val write: Duration? = null,
)

internal fun TimeoutSettings.toClientConfigurer(): ClientConfigurer = {
    connect?.let { connectTimeout(it.toJavaDuration()) }
    read?.let { readTimeout(it.toJavaDuration()) }
    write?.let { writeTimeout(it.toJavaDuration()) }
}