package io.amplicode.connekt.integration

import io.amplicode.connekt.context.ValuesEnvironmentStore
import org.junit.jupiter.api.Test

class DelegatedVarsIntegrationTest : IntegrationTest() {

    @Test
    fun `delegated vars uses response from first request in second request`() {
        val context = createIntegrationContext(ValuesEnvironmentStore(mapOf("host" to host)))
        runScriptFile(scriptFile("delegated_vars/delegated_vars.connekt.kts"), context).assertSuccess()
    }
}
