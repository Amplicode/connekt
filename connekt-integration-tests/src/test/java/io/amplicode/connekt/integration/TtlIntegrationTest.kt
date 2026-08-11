package io.amplicode.connekt.integration

import io.amplicode.connekt.BaseNonColorPrinter
import io.amplicode.connekt.context.ValuesEnvironmentStore
import io.amplicode.connekt.context.execution.ExecutionScenario
import io.amplicode.connekt.context.persistence.InMemoryStorage
import io.amplicode.connekt.context.persistence.Storage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.typeOf

class TtlIntegrationTest : IntegrationTest() {

    @Test
    fun `cached value is reused while ttl is valid`() {
        val storage = InMemoryStorage()

        runTtlScript(storage, counterId = "ttl-valid", ttlMillis = 3_600_000)
        val first = storage.token()

        runTtlScript(storage, counterId = "ttl-valid", ttlMillis = 3_600_000)
        val second = storage.token()

        assertEquals(first, second)
    }

    @Test
    fun `cached value is refreshed after ttl expires`() {
        val storage = InMemoryStorage()

        runTtlScript(storage, counterId = "ttl-expired", ttlMillis = 0)
        val first = storage.token()

        runTtlScript(storage, counterId = "ttl-expired", ttlMillis = 0)
        val second = storage.token()

        assertNotEquals(first, second)
    }

    @Test
    fun `ttl derived from response is reused while valid`() {
        val storage = InMemoryStorage()

        runFromResponseScript(storage, expiresIn = 3600)
        val first = storage.token()

        runFromResponseScript(storage, expiresIn = 3600)
        val second = storage.token()

        assertEquals(first, second)
    }

    @Test
    fun `ttl derived from response refreshes when expired`() {
        val storage = InMemoryStorage()

        runFromResponseScript(storage, expiresIn = 0)
        val first = storage.token()

        runFromResponseScript(storage, expiresIn = 0)
        val second = storage.token()

        assertNotEquals(first, second)
    }

    @Test
    fun `ttl derived from header is reused while valid`() {
        val storage = InMemoryStorage()

        runFromHeaderScript(storage, expiresIn = 3600)
        val first = storage.token()

        runFromHeaderScript(storage, expiresIn = 3600)
        val second = storage.token()

        assertEquals(first, second)
    }

    @Test
    fun `ttl derived from header refreshes when expired`() {
        val storage = InMemoryStorage()

        runFromHeaderScript(storage, expiresIn = 0)
        val first = storage.token()

        runFromHeaderScript(storage, expiresIn = 0)
        val second = storage.token()

        assertNotEquals(first, second)
    }

    @Test
    fun `useCase value is reused while ttl is valid`() {
        val storage = InMemoryStorage()

        runUseCaseScript(storage, counterId = "uc-valid", ttlMillis = 3_600_000)
        val first = storage.token()

        runUseCaseScript(storage, counterId = "uc-valid", ttlMillis = 3_600_000)
        val second = storage.token()

        assertEquals(first, second)
    }

    @Test
    fun `useCase value is refreshed after ttl expires`() {
        val storage = InMemoryStorage()

        runUseCaseScript(storage, counterId = "uc-expired", ttlMillis = 0)
        val first = storage.token()

        runUseCaseScript(storage, counterId = "uc-expired", ttlMillis = 0)
        val second = storage.token()

        assertNotEquals(first, second)
    }

    @Test
    fun `value without ttl is cached indefinitely`() {
        val storage = InMemoryStorage()

        runAbsentTtlScript(storage, counterId = "ttl-absent")
        val first = storage.token()

        runAbsentTtlScript(storage, counterId = "ttl-absent")
        val second = storage.token()

        assertEquals(first, second)
    }

    private fun runFromResponseScript(storage: Storage, expiresIn: Long) {
        val env = ValuesEnvironmentStore(
            mapOf(
                "host" to host,
                "expiresIn" to expiresIn.toString()
            )
        )
        runScriptFile(
            scriptFile("ttl/ttl_from_response.connekt.kts"),
            createIntegrationContext(env, storage),
            ExecutionScenario.SingleExecution("echoed")
        ).assertSuccess()
    }

    private fun runFromHeaderScript(storage: Storage, expiresIn: Long) {
        val env = ValuesEnvironmentStore(
            mapOf(
                "host" to host,
                "expiresIn" to expiresIn.toString()
            )
        )
        runScriptFile(
            scriptFile("ttl/ttl_from_header.connekt.kts"),
            createIntegrationContext(env, storage),
            ExecutionScenario.SingleExecution("echoed")
        ).assertSuccess()
    }

    private fun runUseCaseScript(storage: Storage, counterId: String, ttlMillis: Long) {
        val env = ValuesEnvironmentStore(
            mapOf(
                "host" to host,
                "counterId" to counterId,
                "ttlMillis" to ttlMillis.toString()
            )
        )
        runScriptFile(
            scriptFile("ttl/ttl_usecase.connekt.kts"),
            createIntegrationContext(env, storage),
            ExecutionScenario.SingleExecution("echoed")
        ).assertSuccess()
    }

    private fun runAbsentTtlScript(storage: Storage, counterId: String) {
        val env = ValuesEnvironmentStore(mapOf("host" to host, "counterId" to counterId))
        runScriptFile(
            scriptFile("ttl/ttl_absent.connekt.kts"),
            createIntegrationContext(env, storage),
            ExecutionScenario.SingleExecution("echoed")
        ).assertSuccess()
    }

    @Test
    fun `expired cache refresh is reported in output`() {
        val storage = InMemoryStorage()
        runTtlScript(storage, counterId = "ttl-log", ttlMillis = 0)

        val output = StringBuilder()
        val capturingPrinter = object : BaseNonColorPrinter() {
            override fun print(s: String) {
                output.append(s)
            }
        }
        val env = ValuesEnvironmentStore(
            mapOf("host" to host, "counterId" to "ttl-log", "ttlMillis" to "0")
        )
        runScriptFile(
            scriptFile("ttl/ttl.connekt.kts"),
            createIntegrationContext(env, storage, printer = capturingPrinter),
            ExecutionScenario.SingleExecution("echoed")
        ).assertSuccess()

        assertTrue(
            output.contains("has expired, re-executing"),
            "Expected TTL-expiry message in output, got:\n$output"
        )
    }

    @Test
    fun `expired useCase refresh is reported in output`() {
        val storage = InMemoryStorage()
        runUseCaseScript(storage, counterId = "uc-log", ttlMillis = 0)

        val output = StringBuilder()
        val capturingPrinter = object : BaseNonColorPrinter() {
            override fun print(s: String) {
                output.append(s)
            }
        }
        val env = ValuesEnvironmentStore(
            mapOf("host" to host, "counterId" to "uc-log", "ttlMillis" to "0")
        )
        runScriptFile(
            scriptFile("ttl/ttl_usecase.connekt.kts"),
            createIntegrationContext(env, storage, printer = capturingPrinter),
            ExecutionScenario.SingleExecution("echoed")
        ).assertSuccess()

        assertTrue(
            output.contains("has expired, re-executing useCase"),
            "Expected useCase TTL-expiry message in output, got:\n$output"
        )
    }

    private fun runTtlScript(storage: Storage, counterId: String, ttlMillis: Long) {
        val env = ValuesEnvironmentStore(
            mapOf(
                "host" to host,
                "counterId" to counterId,
                "ttlMillis" to ttlMillis.toString()
            )
        )
        runScriptFile(
            scriptFile("ttl/ttl.connekt.kts"),
            createIntegrationContext(env, storage),
            ExecutionScenario.SingleExecution("echoed")
        ).assertSuccess()
    }

    private fun Storage.token(): String? = getValue("token", typeOf<String>())
}
