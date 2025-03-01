package io.amplicode.connekt

import io.amplicode.connekt.dsl.ConnektBuilder

// TODO make non-singleton and move into ConnektContext?
object RequestExecutor {
    private val ignoredExecutables = mutableSetOf<Executable<*>>()

    fun execute(executable: Executable<*>) {
        if (executable in ignoredExecutables) {
            return
        }
        executable.execute()
    }

    fun execute(connektBuilder: ConnektBuilder, requestNumber: Int?) {
        connektBuilder.connektContext.use {
            if (requestNumber != null) {
                val request = connektBuilder.requests[requestNumber]
                execute(request)
            } else {
                connektBuilder.requests
                    .forEach { execute(it) }
            }
        }
    }

    fun ignoreOnExecutionPhase(executable: Executable<*>) {
        ignoredExecutables.add(executable)
    }
}