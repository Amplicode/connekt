package io.amplicode.connekt

import io.amplicode.connekt.dsl.ConnektBuilder

object RequestExecutor {
    fun execute(executable: Executable<*>) {
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
}