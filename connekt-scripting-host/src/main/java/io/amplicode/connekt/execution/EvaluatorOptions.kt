package io.amplicode.connekt.execution

import io.amplicode.connekt.context.execution.ExecutionScenario

data class EvaluatorOptions(
    val executionScenario: ExecutionScenario,
    val debugLog: Boolean,
    val compilationCache: Boolean,
    val executionMode: ExecutionMode
)