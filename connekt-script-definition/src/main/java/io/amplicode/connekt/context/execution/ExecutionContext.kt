package io.amplicode.connekt.context.execution

/**
 * Manages executables that must be runed after the script is compiled.
 */
class ExecutionContext {
    private val _registrations: MutableList<ExecutionRegistration> = mutableListOf()
    private val executionRegistrationCustomizers: MutableList<ExecutionRegistrationCustomizer> = mutableListOf()
    private var registrationOrderCounter = generateSequence(0) { it + 1 }.iterator()

    val registrations get() = _registrations.toList()

    /**
     * Registers a new executable that can be then called by registration number.
     */
    fun registerExecutable(
        executable: Executable<*>,
        name: String?
    ) {
        val coordinates = buildSet {
            add(DeclarationCoordinates(registrationOrderCounter.next()))
            if (name != null) {
                add(DeclarationCoordinates(name))
            }
        }
        val executionRegistration = ExecutionRegistration(
            coordinates.toMutableSet(),
            executable,
            DefaultExecutionStrategy()
        )
        val finalRegistration =
            executionRegistrationCustomizers.fold(executionRegistration) { registration, customizer ->
                customizer.customize(registration)
            }
        _registrations.add(finalRegistration)
    }

    fun getExecutionStrategy(executable: Executable<*>): ConnektExecutionStrategy {
        return executable.findRegistration().executionStrategy
    }

    fun execute(scenario: ExecutionScenario) {
        when (scenario) {
            is ExecutionScenario.File -> {
                _registrations.forEach { it.execute() }
            }

            is ExecutionScenario.SingleExecution -> {
                val registration = findRegistration(scenario.declarationCoordinates)
                registration.execute()
            }
        }
    }

    fun ignoreOnExecutionPhase(executable: Executable<*>) {
        executable.findRegistration().isIgnored = true
    }

    fun addRegistrationCustomizer(executionRegistrationCustomizer: ExecutionRegistrationCustomizer) {
        executionRegistrationCustomizers.add(executionRegistrationCustomizer)
    }

    fun findRegistration(coordinates: DeclarationCoordinates): ExecutionRegistration {
        val registration = _registrations.find { registration ->
            coordinates in registration.coordinates
        }
        requireNotNull(registration) {
            "No registration found for coordinates '$coordinates'. " +
                    "Available registrations: ${_registrations.map { it.coordinates }}"
        }
        return registration
    }

    private fun Executable<*>.findRegistration(): ExecutionRegistration {
        val registration = _registrations.find { it.executable == this }
        return requireNotNull(registration) {
            "Executable '$this' is not registered"
        }
    }

    fun addCoordinatesForExecutable(
        coordinates: DeclarationCoordinates,
        executable: Executable<*>
    ) {
        executable.findRegistration().coordinates.add(coordinates)
    }

    private fun ExecutionRegistration.execute() {
        if (isIgnored) {
            return
        }
        executable.execute()
    }
}

fun interface ExecutionRegistrationCustomizer {
    fun customize(registration: ExecutionRegistration): ExecutionRegistration
}

data class ExecutionRegistration(
    val coordinates: MutableSet<DeclarationCoordinates>,
    val executable: Executable<*>,
    var executionStrategy: ConnektExecutionStrategy,
    var isIgnored: Boolean = false
)

fun ExecutionRegistration.hasCoordinates(vararg coordinates: DeclarationCoordinates): Boolean =
    coordinates.any { it in this.coordinates }