package io.amplicode.connekt.integration

import io.amplicode.connekt.context.ValuesEnvironmentStore
import org.junit.jupiter.api.Test

class FormDataIntegrationTest : IntegrationTest() {

    @Test
    fun `echo form params returns posted fields`() {
        val context = createIntegrationContext(ValuesEnvironmentStore(mapOf("host" to host)))
        runScriptFile(scriptFile("form/echo_form_params.connekt.kts"), context).assertSuccess()
    }
}
