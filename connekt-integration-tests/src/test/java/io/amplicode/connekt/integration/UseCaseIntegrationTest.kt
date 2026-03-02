package io.amplicode.connekt.integration

import io.amplicode.connekt.context.ValuesEnvironmentStore
import org.junit.jupiter.api.Test

class UseCaseIntegrationTest : IntegrationTest() {

    @Test
    fun `use case runs multiple requests successfully`() {
        val context = createIntegrationContext(ValuesEnvironmentStore(mapOf("host" to host)))
        runScriptFile(scriptFile("use_case/use_case.connekt.kts"), context).assertSuccess()
    }
}
