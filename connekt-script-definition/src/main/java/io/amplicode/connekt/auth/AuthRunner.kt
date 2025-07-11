package io.amplicode.connekt.auth

import io.amplicode.connekt.Executable

interface AuthRunner {
    fun refresh(auth: Auth): Auth
    fun authorize(): Auth
}

abstract class BaseAuthRunner : AuthRunner, Executable<Auth>() {
    final override fun execute(): Auth = authorize()
}