/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.context.EnvironmentStore
import io.amplicode.connekt.context.FileEnvironmentStore
import io.amplicode.connekt.context.NoOpEnvironmentStore
import io.amplicode.connekt.context.VariablesStore
import org.mapdb.DBMaker
import kotlin.script.experimental.host.FileScriptSource

internal class ConnektCommand : AbstractConnektCommand() {

    override fun run() {
        // DBMaker can't create file in non-existent folder
        // so ensure it exists
        globalEnvFile.parentFile.mkdirs()

        val db = DBMaker.fileDB(globalEnvFile)
            .closeOnJvmShutdown()
            .fileChannelEnable()
            .checksumHeaderBypass()
            .make()

        val context = ConnektContext(
            db,
            createEnvStore(),
            VariablesStore(db)
        )
        context.use { context ->
            runScript(
                context,
                FileScriptSource(script),
                requestNumber?.minus(1),
                useCompilationCache
            )
        }
    }

    private fun createEnvStore(): EnvironmentStore {
        val envName = envName
        return if (envFile.exists() && !envName.isNullOrBlank())
            FileEnvironmentStore(envFile, envName) else
            NoOpEnvironmentStore
    }
}