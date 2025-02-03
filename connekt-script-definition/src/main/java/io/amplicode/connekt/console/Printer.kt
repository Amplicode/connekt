package io.amplicode.connekt.console

import java.io.PrintStream

class Printer(private val printStream: PrintStream) {
    fun print(text: String, color: Color? = null) {

        if (color != null) {
            printStream.print(color.ansi())
        }

        printStream.print(text)

        if (color != null) {
            printStream.print(RESET_COLOR)
        }
    }

    fun println(text: String, color: Color? = null) {
        print(text + "\n", color)
    }

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
