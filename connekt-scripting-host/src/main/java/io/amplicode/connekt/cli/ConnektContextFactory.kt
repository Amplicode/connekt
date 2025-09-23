package io.amplicode.connekt.cli

import io.amplicode.connekt.ConnektInterceptor
import io.amplicode.connekt.SystemOutPrinter
import io.amplicode.connekt.context.*
import io.amplicode.connekt.context.execution.CurlExecutionStrategy
import io.amplicode.connekt.context.execution.ExecutionScenario
import io.amplicode.connekt.context.execution.hasCoordinates
import io.amplicode.connekt.context.persistence.InMemoryStorage
import io.amplicode.connekt.context.persistence.defaultStorage
import io.amplicode.connekt.execution.ExecutionMode
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.notExists


fun AbstractConnektCommand.createContext(): ConnektContext {
    if (compileOnly) {
        val printer = SystemOutPrinter
        return createConnektContext(
            InMemoryStorage(),
            NoopEnvironmentStore,
            NoopCookiesContext,
            ClientContextImpl(ConnektInterceptor(printer, null)),
            printer
        )
    }

    val storageFile = storageFile

    // Ensure the parent directory exists
    storageFile.createParentDirectories()

    val cookiesFile = cookiesFile
    if (cookiesFile.notExists()) {
        cookiesFile.createFile()
    }

    val connektLifeCycleCallbacks = ConnektLifeCycleCallbacksImpl()
    val cookiesContext = CookiesContextImpl(cookiesFile, connektLifeCycleCallbacks)

    val printer = SystemOutPrinter
    val context = createConnektContext(
        defaultStorage(storageFile),
        createEnvStore(this),
        cookiesContext,
        ClientContextImpl(ConnektInterceptor(printer, responseDir)),
        printer
    ).onClose {
        connektLifeCycleCallbacks.fireClosed()
    }

    if (executionMode == ExecutionMode.CURL) {
        val executionScenario = executionScenario
        require(executionScenario is ExecutionScenario.SingleExecution)
        context.executionContext.addRegistrationCustomizer { registration ->
            if (registration.hasCoordinates(executionScenario.declarationCoordinates)) {
                registration.executionStrategy = CurlExecutionStrategy()
            }
            registration
        }
    }

    return context
}

private fun createEnvStore(command: AbstractConnektCommand): EnvironmentStore {
    val overriddenValues = ValuesEnvironmentStore(command.envParams.toMap())

    val envName = command.envName
    if (envName != null) {
        val envFile = command.envFile ?: defaultEnvFile(command)
        if (envFile?.exists() == true) {
            return CompoundEnvironmentStore(
                listOf(
                    overriddenValues,
                    FileEnvironmentStore(envFile, envName)
                )
            )
        }
    }

    return overriddenValues
}

private fun defaultEnvFile(command: AbstractConnektCommand) = command
    .script
    ?.parentFile
    ?.resolve("connekt.env.json")
