package io.amplicode.connekt.integration

import io.amplicode.connekt.context.ValuesEnvironmentStore
import org.junit.jupiter.api.Test

class BasicRequestsIntegrationTest : IntegrationTest() {

    @Test
    fun `get foo returns foo`() {
        val context = createIntegrationContext(ValuesEnvironmentStore(mapOf("host" to host)))
        runScriptFile(scriptFile("basic/get_foo.connekt.kts"), context).assertSuccess()
    }

    @Test
    fun `echo query params returns key and value`() {
        val context = createIntegrationContext(ValuesEnvironmentStore(mapOf("host" to host)))
        runScriptFile(scriptFile("basic/echo_query_params.connekt.kts"), context).assertSuccess()
    }

    @Test
    fun `echo body post echoes request body`() {
        val context = createIntegrationContext(ValuesEnvironmentStore(mapOf("host" to host)))
        runScriptFile(scriptFile("basic/echo_body_post.connekt.kts"), context).assertSuccess()
    }
}
