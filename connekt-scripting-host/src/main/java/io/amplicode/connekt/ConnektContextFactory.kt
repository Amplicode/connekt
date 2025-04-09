package io.amplicode.connekt

import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.context.createConnektContext
import io.amplicode.connekt.context.EnvironmentStore
import io.amplicode.connekt.context.FileEnvironmentStore
import io.amplicode.connekt.context.NoOpEnvironmentStore
import org.mapdb.DBMaker

interface ConnektContextFactory {
    fun createContext(command: AbstractConnektCommand): ConnektContext
}

class DefaultContextFactory : ConnektContextFactory {
    override fun createContext(command: AbstractConnektCommand): ConnektContext {
        val storageFile = command.storageFile

        // DBMaker can't create file in non-existent folder
        // so ensure it exists
        storageFile.parentFile.mkdirs()

        val db = DBMaker.fileDB(storageFile)
            .closeOnJvmShutdown()
            .fileChannelEnable()
            .checksumHeaderBypass()
            .make()

        return createConnektContext(db, createEnvStore(command))
    }

    private fun createEnvStore(command: AbstractConnektCommand): EnvironmentStore {
        val envName = command.envName
        if (envName != null) {
            val envFile = command.envFile ?: defaultEnvFile(command)
            if (envFile?.exists() == true) {
                return FileEnvironmentStore(envFile, envName)
            }
        }

        return NoOpEnvironmentStore
    }

    private fun defaultEnvFile(command: AbstractConnektCommand) = command
        .script
        ?.parentFile
        ?.resolve("connekt.env.json")
}

class CompileOnlyContextFactory : ConnektContextFactory {
    override fun createContext(command: AbstractConnektCommand): ConnektContext {
        val db = DBMaker.memoryDB().make()
        val context = createConnektContext(
            db,
            NoOpEnvironmentStore
        )
        return context
    }
}