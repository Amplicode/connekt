package io.amplicode.connekt

import io.amplicode.connekt.context.NoopClientConfigurer
import io.amplicode.connekt.context.TimeoutSettings
import io.amplicode.connekt.context.toClientConfigurer
import io.amplicode.connekt.dsl.RequestBuilder
import io.amplicode.connekt.test.utils.components.testConnektContext
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class TimeoutTest {

    // Helper: build an OkHttpClient applying TimeoutSettings on top of a fresh builder
    private fun buildClientWithSettings(settings: TimeoutSettings): OkHttpClient {
        val builder = OkHttpClient.Builder()
        settings.toClientConfigurer().invoke(builder)
        return builder.build()
    }

    // ---------------------------------------------------------------------------
    // TimeoutSettings / toClientConfigurer unit tests
    // ---------------------------------------------------------------------------

    @Test
    fun `readTimeout zero seconds results in readTimeoutMillis == 0`() {
        val client = buildClientWithSettings(TimeoutSettings(read = 0.seconds))
        assertEquals(0, client.readTimeoutMillis)
    }

    @Test
    fun `writeTimeout zero seconds results in writeTimeoutMillis == 0`() {
        val client = buildClientWithSettings(TimeoutSettings(write = 0.seconds))
        assertEquals(0, client.writeTimeoutMillis)
    }

    @Test
    fun `connectTimeout zero seconds results in connectTimeoutMillis == 0`() {
        val client = buildClientWithSettings(TimeoutSettings(connect = 0.seconds))
        assertEquals(0, client.connectTimeoutMillis)
    }

    @Test
    fun `timeout shorthand sets connect read and write all to the same duration`() {
        val client = buildClientWithSettings(
            TimeoutSettings(connect = 30.seconds, read = 30.seconds, write = 30.seconds)
        )
        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
    }

    @Test
    fun `null timeout fields leave the builder unchanged`() {
        // A builder without any explicit timeouts keeps OkHttp defaults (10 s connect, 10 s read, 10 s write)
        val defaultClient = OkHttpClient.Builder().build()
        val clientFromEmptySettings = buildClientWithSettings(TimeoutSettings())
        assertEquals(defaultClient.connectTimeoutMillis, clientFromEmptySettings.connectTimeoutMillis)
        assertEquals(defaultClient.readTimeoutMillis, clientFromEmptySettings.readTimeoutMillis)
        assertEquals(defaultClient.writeTimeoutMillis, clientFromEmptySettings.writeTimeoutMillis)
    }

    // ---------------------------------------------------------------------------
    // Precedence tests using ClientContextImpl
    // ---------------------------------------------------------------------------

    @Test
    fun `hardcoded default is 1-minute read and write timeout`() {
        val context = testConnektContext()
        val client = context.clientContext.getClient(NoopClientConfigurer)
        // Default set in ClientContextImpl: 1 minute = 60_000 ms
        assertEquals(60_000, client.readTimeoutMillis)
        assertEquals(60_000, client.writeTimeoutMillis)
        context.close()
    }

    @Test
    fun `script-level readTimeout overrides the hardcoded default`() {
        val context = testConnektContext()
        // Simulate script-level call by setting globalConfigurer directly (same as ConnektBuilderImpl.readTimeout)
        context.clientContext.globalConfigurer = TimeoutSettings(read = 5.seconds).toClientConfigurer()

        val client = context.clientContext.getClient(NoopClientConfigurer)
        assertEquals(5_000, client.readTimeoutMillis)
        context.close()
    }

    @Test
    fun `per-request readTimeout overrides script-level timeout`() {
        val context = testConnektContext()
        // Script-level: 5 s read
        context.clientContext.globalConfigurer = TimeoutSettings(read = 5.seconds).toClientConfigurer()

        // Per-request configurer overrides to 3 s
        val perRequestConfigurer = TimeoutSettings(read = 3.seconds).toClientConfigurer()
        val client = context.clientContext.getClient(perRequestConfigurer)
        assertEquals(3_000, client.readTimeoutMillis)
        context.close()
    }

    @Test
    fun `per-request timeout does not affect other requests`() {
        val context = testConnektContext()
        // Script-level: 5 s read
        context.clientContext.globalConfigurer = TimeoutSettings(read = 5.seconds).toClientConfigurer()

        // Request A uses 3 s
        val clientA = context.clientContext.getClient(TimeoutSettings(read = 3.seconds).toClientConfigurer())
        assertEquals(3_000, clientA.readTimeoutMillis)

        // Request B uses no per-request override → sees script-level 5 s
        val clientB = context.clientContext.getClient(NoopClientConfigurer)
        assertEquals(5_000, clientB.readTimeoutMillis)

        context.close()
    }

    @Test
    fun `precedence chain per-request overrides script-level overrides hardcoded default`() {
        val context = testConnektContext()

        // No overrides: hardcoded default (60 s)
        val clientDefault = context.clientContext.getClient(NoopClientConfigurer)
        assertEquals(60_000, clientDefault.readTimeoutMillis)

        // Script-level override: 10 s
        context.clientContext.globalConfigurer = TimeoutSettings(read = 10.seconds).toClientConfigurer()
        val clientScriptLevel = context.clientContext.getClient(NoopClientConfigurer)
        assertEquals(10_000, clientScriptLevel.readTimeoutMillis)

        // Per-request override: 2 s
        val clientPerRequest = context.clientContext.getClient(
            TimeoutSettings(read = 2.seconds).toClientConfigurer()
        )
        assertEquals(2_000, clientPerRequest.readTimeoutMillis)

        context.close()
    }

    // ---------------------------------------------------------------------------
    // RequestBuilder unit tests
    // ---------------------------------------------------------------------------

    @Test
    fun `RequestBuilder readTimeout sets timeout for that request`() {
        val rb = RequestBuilder("GET", "http://localhost/test", null)
        rb.readTimeout(7.seconds)

        val context = testConnektContext()
        val client = context.clientContext.getClient(rb.getClientConfigurer())
        assertEquals(7_000, client.readTimeoutMillis)
        context.close()
    }

    @Test
    fun `RequestBuilder timeout shorthand sets all three timeouts`() {
        val rb = RequestBuilder("GET", "http://localhost/test", null)
        rb.timeout(15.seconds)

        val context = testConnektContext()
        val client = context.clientContext.getClient(rb.getClientConfigurer())
        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(15_000, client.readTimeoutMillis)
        assertEquals(15_000, client.writeTimeoutMillis)
        context.close()
    }

    @Test
    fun `RequestBuilder readTimeout zero means no timeout`() {
        val rb = RequestBuilder("GET", "http://localhost/test", null)
        rb.readTimeout(0.seconds)

        val context = testConnektContext()
        val client = context.clientContext.getClient(rb.getClientConfigurer())
        assertEquals(0, client.readTimeoutMillis)
        context.close()
    }

    // ---------------------------------------------------------------------------
    // Script-level DSL integration tests (via runScript / ConnektBuilderImpl)
    // ---------------------------------------------------------------------------

    @Test
    fun `script-level readTimeout DSL is applied`() {
        val context = testConnektContext()
        val statement = io.amplicode.connekt.test.utils.ScriptStatement(context)
        statement.applyScript {
            readTimeout(20.seconds)
        }
        val client = context.clientContext.getClient(NoopClientConfigurer)
        assertEquals(20_000, client.readTimeoutMillis)
        context.close()
    }

    @Test
    fun `script-level timeout shorthand DSL sets all three timeouts`() {
        val context = testConnektContext()
        val statement = io.amplicode.connekt.test.utils.ScriptStatement(context)
        statement.applyScript {
            timeout(45.seconds)
        }
        val client = context.clientContext.getClient(NoopClientConfigurer)
        assertEquals(45_000, client.connectTimeoutMillis)
        assertEquals(45_000, client.readTimeoutMillis)
        assertEquals(45_000, client.writeTimeoutMillis)
        context.close()
    }

    @Test
    fun `configureClient still works after readTimeout call`() {
        var configureCalled = false
        val context = testConnektContext()
        val statement = io.amplicode.connekt.test.utils.ScriptStatement(context)
        statement.applyScript {
            readTimeout(10.seconds)
            configureClient {
                configureCalled = true
            }
        }
        context.clientContext.getClient(NoopClientConfigurer)
        assertEquals(true, configureCalled)
        context.close()
    }
}
