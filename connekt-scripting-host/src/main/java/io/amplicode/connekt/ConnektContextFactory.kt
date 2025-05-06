package io.amplicode.connekt

import io.amplicode.connekt.context.*
import org.mapdb.DBMaker
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.notExists

interface ConnektContextFactory {
    fun createContext(command: AbstractConnektCommand): ConnektContext
}

class DefaultContextFactory : ConnektContextFactory {
    override fun createContext(command: AbstractConnektCommand): ConnektContext {
        val storageFile = command.storageFile

        // DBMaker can't create a file in a non-existent folder
        // so ensure it exists
        storageFile.createParentDirectories()

        val db = DBMaker.fileDB(storageFile.toFile())
            .closeOnJvmShutdown()
            .fileChannelEnable()
            .checksumHeaderBypass()
            .make()

        val cookiesFile = command.cookiesFile
        if (cookiesFile.notExists()) {
            cookiesFile.createFile()
        }

        val connektLifeCycleCallbacks = ConnektLifeCycleCallbacksImpl()
        val cookiesContext = CookiesContextImpl(cookiesFile, connektLifeCycleCallbacks)
        val context = createConnektContext(
            db,
            createEnvStore(command),
            cookiesContext
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
                return DelegateEnvironmentStore(listOf(overriddenValues, FileEnvironmentStore(envFile, envName)))
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
        val db = DBMaker.memoryDB().make()
        return createConnektContext(
            db,
            NoopEnvironmentStore,
            NoopCookiesContext
        )
    }
}
