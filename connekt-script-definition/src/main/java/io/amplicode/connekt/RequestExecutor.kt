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

    fun execute(context: ConnektContext, requestNumber: Int?) {
        context.use {
            if (requestNumber != null) {
                val request = context.requests[requestNumber]
                execute(request)
            } else {
                context.requests.forEach { execute(it) }
            }
        }
    }

    fun ignoreOnExecutionPhase(executable: Executable<*>) {
        ignoredExecutables.add(executable)
    }
}