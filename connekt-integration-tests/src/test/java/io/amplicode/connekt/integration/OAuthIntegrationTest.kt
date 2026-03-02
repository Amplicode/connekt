package io.amplicode.connekt.integration

import io.amplicode.connekt.ConnektAuthExtensionsImpl
import io.amplicode.connekt.context.ValuesEnvironmentStore
import io.amplicode.connekt.context.execution.ExecutionScenario
import io.amplicode.connekt.context.persistence.InMemoryStorage
import io.amplicode.connekt.dsl.AuthExtensions
import org.junit.jupiter.api.Test

class OAuthIntegrationTest : IntegrationTest() {

    @Test
    fun `oauth flow - acquires token and sends authorized request`() {
        val context = createIntegrationContext(
            environmentStore = ValuesEnvironmentStore(mapOf("host" to host)),
            configure = { register(AuthExtensions::class) { UserlessOAuthExtensions(ConnektAuthExtensionsImpl(this)) } }
        )
        runScriptFile(
            scriptFile("oauth/oauth.connekt.kts"),
            context,
            ExecutionScenario.SingleExecution(1)
        ).assertSuccess()
    }

    @Test
    fun `oauth flow - reuses stored token across runs`() {
        val storage = InMemoryStorage()
        val envStore = ValuesEnvironmentStore(mapOf("host" to host))

        fun run() = runScriptFile(
            scriptFile("oauth/oauth.connekt.kts"),
            createIntegrationContext(
                environmentStore = envStore,
                storage = storage,
                configure = { register(AuthExtensions::class) { UserlessOAuthExtensions(ConnektAuthExtensionsImpl(this)) } }
            )
        ).assertSuccess()

        run() // first run: performs full OAuth, stores token
        run() // second run: reuses stored token, no browser auth
    }
}
