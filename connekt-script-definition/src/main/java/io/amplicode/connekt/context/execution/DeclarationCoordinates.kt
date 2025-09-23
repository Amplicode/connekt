package io.amplicode.connekt.context.execution

/**
 * Represents a request identifier.
 *
 * Implementations must implement [equals] and [hashCode].
 */
sealed interface DeclarationCoordinates {

    fun asString(): String

    data class Number(val number: Int) : DeclarationCoordinates {
        override fun asString(): String = "# $number"
    }

    data class Name(val name: String) : DeclarationCoordinates {
        override fun asString(): String = name
    }
}

fun DeclarationCoordinates(number: Int) = DeclarationCoordinates.Number(number)
fun DeclarationCoordinates(name: String) = DeclarationCoordinates.Name(name)