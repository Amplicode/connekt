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
        if (requestNumber == null) {
            requests.forEach { execute(it) }
        } else {
            require(requestNumber in requests.indices) {
                "Invalid request number '$requestNumber': it must be in range '${requests.indices}'"
            }
            val request = requests[requestNumber]
            execute(request)
        }
    }

    fun ignoreOnExecutionPhase(executable: Executable<*>) {
        ignoredExecutables.add(executable)
    }
}