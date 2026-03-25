package io.amplicode.connekt.dsl

/**
 * Receiver interface for soft assertion blocks.
 *
 * When used with the Kotlin Power Assert compiler plugin, calls to [assert]
 * are transformed to include rich expression diagrams in failure messages.
 */
interface AssertScope {
    /**
     * Records an assertion. In a soft-assertion context, failures are
     * collected rather than thrown immediately.
     */
    fun assert(assertion: Boolean, message: (() -> String)? = null)
}

/**
 * Executes [block] and collects all assertion failures, reporting them
 * together at the end instead of failing on the first one.
 *
 * Example:
 * ```kotlin
 * GET("$host/api/user/1") then { response ->
 *     val user = response.decode<User>()
 *     assertSoftly {
 *         assert(user.name == "Alice")
 *         assert(user.age > 0)
 *         assert(user.email.contains("@"))
 *     }
 * }
 * ```
 *
 * @return the result of [block] if all assertions pass
 * @throws AssertionError if any assertion in the block failed,
 *   with all failure messages concatenated
 */
fun <R> assertSoftly(block: AssertScope.() -> R): R {
    val scope = AssertScopeImpl()
    val result = scope.block()
    if (scope.errors.isNotEmpty()) {
        throw AssertionError(scope.errors.joinToString("\n\n"))
    }
    return result
}

private class AssertScopeImpl : AssertScope {
    val errors = mutableListOf<String>()

    override fun assert(assertion: Boolean, message: (() -> String)?) {
        if (!assertion) {
            errors.add(message?.invoke() ?: "Assertion failed")
        }
    }
}
