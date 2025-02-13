package io.amplicode.connekt.console

import io.amplicode.connekt.console.Printer.Color
import io.amplicode.connekt.console.Printer.Companion.RESET_COLOR

interface Printer {

    fun print(text: String, color: Color? = null)

    enum class Color(val code: String) {
        BLUE("34"),
        GREEN("32"),
        RED("31");

        fun ansi(): String {
            return "\u001B[${code}m"
        }
    }

    companion object {
        const val RESET_COLOR = "\u001B[0m"
    }
}

fun Printer.println(text: String, color: Color? = null) {
    print(text + "\n", color)
}

abstract class BaseColorPrinter : Printer {

    final override fun print(text: String, color: Color?) {
        if (color != null) {
            print(color.ansi())
        }

        print(text)

        if (color != null) {
            print(RESET_COLOR)
        }
    }

    protected abstract fun print(s: String)
}

/**
 * Implementation that ignores color symbols.
 * Used for tests.
 */
abstract class BaseNonColorPrinter : Printer {

    final override fun print(text: String, color: Color?) {
        print(text)
    }

    protected abstract fun print(s: String)
}

object SystemOutPrinter : BaseColorPrinter() {
    override fun print(s: String) {
        kotlin.io.print(s)
    }
}
