package io.amplicode.connekt.context

import io.amplicode.connekt.Executable

class RequestsContext {
    private val _requests: MutableList<Executable<*>> = mutableListOf()

    val requests: List<Executable<*>> = _requests

    fun addRequest(executable: Executable<*>) {
        _requests.add(executable)
    }
}