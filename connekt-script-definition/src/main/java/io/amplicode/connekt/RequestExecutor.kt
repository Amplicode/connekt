package io.amplicode.connekt

import io.amplicode.connekt.context.ConnektContext

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
        val requests = context.requestsContext.requests
        when {
            requestNumber == null -> requests.forEach { execute(it) }
            requestNumber >= 0 -> {
                require(requestNumber in requests.indices) {
                    "Invalid request number: should be in range [0 .. ${requests.lastIndex}], got '$requestNumber'"
                }
                val request = requests[requestNumber]
                execute(request)
            }

            else -> {
                // Negative request number, do nothing
            }
        }
    }

    fun ignoreOnExecutionPhase(executable: Executable<*>) {
        ignoredExecutables.add(executable)
    }
}