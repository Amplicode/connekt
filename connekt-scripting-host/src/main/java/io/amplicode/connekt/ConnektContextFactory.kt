package io.amplicode.connekt

import io.amplicode.connekt.context.*
import io.amplicode.connekt.context.persistence.InMemoryStorage
import io.amplicode.connekt.context.persistence.defaultStorage
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.notExists

interface ConnektContextFactory {
    fun createContext(command: AbstractConnektCommand): ConnektContext
}

class DefaultContextFactory : ConnektContextFactory {
    override fun createContext(command: AbstractConnektCommand): ConnektContext {
        val storageFile = command.storageFile

        // Ensure the parent directory exists
        storageFile.createParentDirectories()

        val cookiesFile = command.cookiesFile
        if (cookiesFile.notExists()) {
            cookiesFile.createFile()
        }

        val connektLifeCycleCallbacks = ConnektLifeCycleCallbacksImpl()
        val cookiesContext = CookiesContextImpl(cookiesFile, connektLifeCycleCallbacks)

        val printer = SystemOutPrinter
        val context = createConnektContext(
            defaultStorage(storageFile),
            createEnvStore(command),
            cookiesContext,
            ClientContextImpl(ConnektInterceptor(printer, command.responseDir)),
            printer
        ).onClose {
            connektLifeCycleCallbacks.fireClosed()
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
}

class CompileOnlyContextFactory : ConnektContextFactory {
    override fun createContext(command: AbstractConnektCommand): ConnektContext {
        val printer = SystemOutPrinter

        return createConnektContext(
            InMemoryStorage(),
            NoopEnvironmentStore,
            NoopCookiesContext,
            ClientContextImpl(ConnektInterceptor(printer, null)),
            printer
        )
    }
}
