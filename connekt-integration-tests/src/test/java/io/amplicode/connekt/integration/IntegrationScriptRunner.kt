package io.amplicode.connekt.integration

import io.amplicode.connekt.BaseNonColorPrinter
import io.amplicode.connekt.ConnektAuthExtensionsImpl
import io.amplicode.connekt.ConnektInterceptor
import io.amplicode.connekt.SystemOutPrinter
import io.amplicode.connekt.auth.OAuthRunner
import io.amplicode.connekt.context.*
import io.amplicode.connekt.context.ConnektBuilderFactory
import io.amplicode.connekt.context.ConnektBuilderFactoryImpl
import io.amplicode.connekt.context.execution.CurlExecutionStrategy
import io.amplicode.connekt.context.execution.ExecutionScenario
import io.amplicode.connekt.context.execution.hasCoordinates
import io.amplicode.connekt.context.persistence.InMemoryStorage
import io.amplicode.connekt.context.persistence.Storage
import io.amplicode.connekt.dsl.AuthExtensions
import io.amplicode.connekt.execution.ConnektScriptingHost
import io.amplicode.connekt.execution.returnValueAsError
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.isError
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.jvm.util.isError
import kotlin.test.fail
import java.io.File

/**
 * Evaluates a `.connekt.kts` script file and executes all registered requests,
 * mirroring the behaviour of [ConnektScript.run] in the CLI.
 */
fun runScriptFile(
    file: File,
    context: ConnektContext,
    executionScenario: ExecutionScenario = ExecutionScenario.File
): ResultWithDiagnostics<EvaluationResult> {
    val host = ConnektScriptingHost(
        useCompilationCache = false,
        enablePowerAssert = false
    )
    return context.use {
        val result = host.evalScript(context, FileScriptSource(file))
        context.executionContext.execute(executionScenario)
        result
    }
}

/**
 * Compiles a `.connekt.kts` script file without executing it.
 */
fun compileScriptFile(
    file: File
): ResultWithDiagnostics<CompiledScript> {
    val host = ConnektScriptingHost(
        useCompilationCache = false,
        enablePowerAssert = false
    )
    return host.compileScript(FileScriptSource(file))
}

/**
 * Runs a `.connekt.kts` script file in curl mode — no real HTTP requests are made.
 * Instead, curl commands are captured and returned as a string.
 *
 * @param scenario which request(s) to generate curl for; defaults to all requests in the file
 */
fun runScriptFileCurl(
    file: File,
    environmentStore: EnvironmentStore = NoopEnvironmentStore,
    scenario: ExecutionScenario = ExecutionScenario.File,
    builderFactory: ((ConnektContext) -> ConnektBuilderFactory)? = null,
): String {
    val output = StringBuilder()
    val capturingPrinter = object : BaseNonColorPrinter() {
        override fun print(s: String) = output.append(s).let {}
    }
    val context = createConnektContext(
        storage = InMemoryStorage(),
        environmentStore = environmentStore,
        cookiesContext = NoopCookiesContext,
        clientContext = ClientContextImpl(ConnektInterceptor(capturingPrinter, null)),
        printer = capturingPrinter,
        builderFactory = builderFactory ?: { ctx -> ConnektBuilderFactoryImpl(ctx) },
    )
    context.executionContext.addRegistrationCustomizer { registration ->
        val shouldApply = when (scenario) {
            is ExecutionScenario.SingleExecution -> registration.hasCoordinates(scenario.declarationCoordinates)
            ExecutionScenario.File -> true
        }
        if (shouldApply) registration.also { it.executionStrategy = CurlExecutionStrategy() }
        else registration
    }
    val host = ConnektScriptingHost(useCompilationCache = false, enablePowerAssert = false)
    context.use {
        host.evalScript(context, FileScriptSource(file))
        context.executionContext.execute(scenario)
    }
    return output.toString()
}

/**
 * Asserts that the script compiled without errors.
 */
@JvmName("assertCompilationSuccess")
fun ResultWithDiagnostics<CompiledScript>.assertSuccess() {
    if (isError()) {
        val details = reports
            .filter { it.isError() }
            .joinToString("\n") { it.message }
        fail("Script compilation failed:\n$details")
    }
}

/**
 * Asserts that the script evaluation completed without compilation or runtime errors.
 */
fun ResultWithDiagnostics<EvaluationResult>.assertSuccess() {
    val runtimeError = returnValueAsError?.error
    if (runtimeError != null) {
        fail("Script produced a runtime error: ${runtimeError.message}", runtimeError)
    }
    if (isError()) {
        val details = reports
            .filter { it.isError() }
            .joinToString("\n") { it.message }
        fail("Script compilation/evaluation failed:\n$details")
    }
}

/**
 * Creates a [ConnektContext] suitable for integration tests.
 *
 * @param environmentStore an [EnvironmentStore] to inject; defaults to [NoopEnvironmentStore]
 *   so tests that need to supply a `host` variable can pass a custom store.
 */
fun createIntegrationContext(
    environmentStore: EnvironmentStore = NoopEnvironmentStore,
    storage: Storage = InMemoryStorage(),
    builderFactory: ((ConnektContext) -> ConnektBuilderFactory)? = null,
    configure: ConnektContext.() -> Unit = {}
): ConnektContext {
    val printer = SystemOutPrinter
    return createConnektContext(
        storage = storage,
        environmentStore = environmentStore,
        cookiesContext = NoopCookiesContext,
        clientContext = ClientContextImpl(ConnektInterceptor(printer, null)),
        printer = printer,
        builderFactory = builderFactory ?: { ctx -> ConnektBuilderFactoryImpl(ctx) },
    ).apply(configure)
}

/**
 * Creates a [ConnektBuilderFactory] that wraps [AuthExtensions] for both the main and imported
 * script contexts with [UserlessOAuthExtensions], so OAuth flows complete automatically during tests
 * (the test server's /oauth/auth handler immediately redirects, simulating browser authorization).
 */
fun oauthAwareBuilderFactory(context: ConnektContext): ConnektBuilderFactory =
    ConnektBuilderFactoryImpl(
        context,
        importAuthExtensionsFactory = { importCtx -> UserlessOAuthExtensions(ConnektAuthExtensionsImpl(importCtx)) }
    )

/**
 * Wraps [AuthExtensions] to automatically complete the OAuth browser step during tests.
 * The test server's `/oauth/auth` handler immediately calls `redirect_uri?code=42`,
 * simulating the user authorizing in the browser.
 */
class UserlessOAuthExtensions(
    private val delegate: AuthExtensions
) : AuthExtensions by delegate {

    override fun oauth(
        authorizeEndpoint: String,
        clientId: String,
        clientSecret: String?,
        scope: String,
        tokenEndpoint: String,
        redirectUri: String
    ): OAuthRunner {
        val runner = delegate.oauth(authorizeEndpoint, clientId, clientSecret, scope, tokenEndpoint, redirectUri)
        runner.addListener(object : OAuthRunner.Listener {
            override fun onWaitAuthCode(authUrl: String) {
                java.net.URI(authUrl).toURL().openStream().close()
            }
        })
        return runner
    }
}
