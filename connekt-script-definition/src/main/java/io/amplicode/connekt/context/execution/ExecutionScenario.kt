package io.amplicode.connekt.context.execution

/**
 * Represents a scenario in which the script is executed.
 */
sealed interface ExecutionScenario {
    /**
     * The script is executed once for each request.
     */
    object File : ExecutionScenario

    /**
     * The script is executed once for the specified request.
     */
    data class SingleExecution(val declarationCoordinates: DeclarationCoordinates) : ExecutionScenario

    companion object {
        fun SingleExecution(number: Int) = SingleExecution(DeclarationCoordinates(number))
        fun SingleExecution(name: String) = SingleExecution(DeclarationCoordinates(name))
    }
}