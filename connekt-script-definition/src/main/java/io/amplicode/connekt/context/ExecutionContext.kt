package io.amplicode.connekt.context

import io.amplicode.connekt.ConnektExecutionStrategy
import io.amplicode.connekt.DefaultExecutionStrategy
import io.amplicode.connekt.Executable

/**
 * Manages executables that must be runed after the script is compiled.
 */
class ExecutionContext {
    private val _executables: MutableList<Executable<*>> = mutableListOf()
    private val ignoredExecutables = mutableSetOf<Executable<*>>()
    private val executionStrategies: MutableMap<Int, ConnektExecutionStrategy> = mutableMapOf()

    /**
     * Registers a new executable that can be then called by registration number.
     */
    fun registerExecutable(executable: Executable<*>) {
        _executables.add(executable)
    }

    /**
     * Makes able to set a custom execution strategy for the request.
     * [DefaultExecutionStrategy] is used by default.
     */
    fun registerExecutionStrategyForRequest(requestNumber: Int, strategy: ConnektExecutionStrategy) {
        executionStrategies[requestNumber] = strategy
    }

    fun getExecutionStrategy(executable: Executable<*>, context: ConnektContext): ConnektExecutionStrategy {
        val executableIndex = findExecutableIndex(executable)
        return executionStrategies.getOrDefault(
            executableIndex,
            DefaultExecutionStrategy(context)
        )
    }

    private fun execute(executable: Executable<*>) {
        if (executable in ignoredExecutables) {
            return
        }
        executable.execute()
    }

    fun execute(requestNumber: Int?) {
        val executables = _executables
        if (requestNumber == null) {
            executables.forEach { execute(it) }
        } else {
            require(requestNumber in executables.indices) {
                "Invalid request number '$requestNumber': it must be in range '${executables.indices}'"
            }
            val request = executables[requestNumber]
            execute(request)
        }
    }

    fun ignoreOnExecutionPhase(executable: Executable<*>) {
        ignoredExecutables.add(executable)
    }

    private fun findExecutableIndex(executable: Executable<*>): Int {
        val index = _executables.indexOf(executable)
        assert(index != -1)
        return index
    }
}