package io.amplicode.connekt.integration

import io.amplicode.connekt.context.ValuesEnvironmentStore
import io.amplicode.connekt.context.execution.ExecutionScenario
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CurlIntegrationTest : IntegrationTest() {

    @Test
    fun `curl mode prints correct curl command for GET request`() {
        val output = runScriptFileCurl(
            scriptFile("basic/get_foo.connekt.kts"),
            environmentStore = ValuesEnvironmentStore(mapOf("host" to host))
        )
        assertEquals("curl -X GET -H \"User-Agent:connekt/0.0.1\" \"$host/foo\"\n", output)
    }

    @Test
    fun `curl mode makes no real http request`() {
        val counterId = UUID.randomUUID().toString()
        val counterUrl = "$host/counter/$counterId"

        runScriptFileCurl(
            scriptFile("curl/counter_inc.connekt.kts"),
            environmentStore = ValuesEnvironmentStore(mapOf("host" to host, "counterId" to counterId))
        )

        val counterValue = URI(counterUrl).toURL().readText()
        assertEquals("0", counterValue, "Expected counter to remain 0 — curl mode must not make real HTTP requests")
    }

    @Test
    fun `curl mode for single request prints only that request`() {
        val output = runScriptFileCurl(
            scriptFile("curl/multi_request.connekt.kts"),
            environmentStore = ValuesEnvironmentStore(mapOf("host" to host)),
            scenario = ExecutionScenario.SingleExecution(0)
        )
        assertTrue(output.contains("$host/foo"), "Expected curl command for /foo")
        assertTrue(!output.contains("$host/bar"), "Expected no curl command for /bar when selecting request 0")
    }
}
