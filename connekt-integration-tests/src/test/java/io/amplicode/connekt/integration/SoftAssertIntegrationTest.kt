package io.amplicode.connekt.integration

import io.amplicode.connekt.context.ValuesEnvironmentStore
import io.amplicode.connekt.context.execution.ExecutionScenario
import io.amplicode.connekt.execution.ConnektScriptingHost
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.host.FileScriptSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertTrue

class SoftAssertIntegrationTest : IntegrationTest() {

    /**
     * Evaluates a script file with Power Assert enabled.
     * Returns the eval result. The execution (HTTP calls + then-blocks) may throw.
     */
    private fun runScriptFileWithPowerAssert(
        file: File,
        executionScenario: ExecutionScenario = ExecutionScenario.File
    ): ResultWithDiagnostics<EvaluationResult> {
        val context = createIntegrationContext(ValuesEnvironmentStore(mapOf("host" to host)))
        val scriptHost = ConnektScriptingHost(
            useCompilationCache = false,
            enablePowerAssert = true
        )
        return context.use {
            val result = scriptHost.evalScript(context, FileScriptSource(file))
            context.executionContext.execute(executionScenario)
            result
        }
    }

    @Test
    fun `assertSoftly with all passing assertions succeeds`() {
        assertDoesNotThrow {
            val result = runScriptFileWithPowerAssert(scriptFile("assert/soft_assert_pass.connekt.kts"))
            result.assertSuccess()
        }
    }

    @Test
    fun `assertSoftly collects multiple failures into one error`() {
        val error = assertThrows<AssertionError> {
            runScriptFileWithPowerAssert(scriptFile("assert/soft_assert_fail.connekt.kts"))
        }

        val message = error.message ?: ""

        // Verify all three assertion failures are collected (not just the first one)
        assertContains(message, "text == \"bar\"", message = "Should contain first failure")
        assertContains(message, "text.length == 10", message = "Should contain second failure")
        assertContains(message, "text == \"baz\"", message = "Should contain third failure")

        // Verify Power Assert diagnostics are present (expression trees with intermediate values)
        assertContains(message, "foo", message = "Should contain the actual value in diagnostics")
        assertContains(message, "false", message = "Should contain comparison result in diagnostics")
    }

    @Test
    fun `regular assert outside assertSoftly fails immediately`() {
        val error = assertThrows<AssertionError> {
            runScriptFileWithPowerAssert(scriptFile("assert/regular_assert_fail.connekt.kts"))
        }

        // Power Assert transforms the message to include expression diagram
        val message = error.message ?: ""
        assertContains(message, "not_foo", message = "Should contain the assertion failure context")
        // Verify Power Assert diagnostic is present
        assertContains(message, "text == \"not_foo\"", message = "Should contain the Power Assert expression")
        assertContains(message, "false", message = "Should contain comparison result")
    }
}
