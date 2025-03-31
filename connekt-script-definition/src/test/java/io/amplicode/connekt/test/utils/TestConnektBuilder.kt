package io.amplicode.connekt.test.utils

import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.context.EnvironmentStore
import io.amplicode.connekt.context.NoOpEnvironmentStore
import io.amplicode.connekt.context.VariablesStore
import io.amplicode.connekt.BaseNonColorPrinter
import io.amplicode.connekt.Printer
import io.amplicode.connekt.SystemOutPrinter
import org.mapdb.DB
import org.mapdb.DBMaker

@Suppress("TestFunctionName")
fun ConnektContext(
    db: DB = DBMaker.memoryDB().make(),
    environmentStore: EnvironmentStore = NoOpEnvironmentStore
): ConnektContext = ConnektContext(
    db,
    environmentStore,
    VariablesStore(db),
    TestPrinter()
)

class TestPrinter : Printer {
    val stringPrinter = StringBuilderPrinter()

    override fun print(text: String, color: Printer.Color?) {
        sequenceOf(SystemOutPrinter, stringPrinter).forEach { printer ->
            printer.print(text, color)
        }
    }
}

class StringBuilderPrinter : BaseNonColorPrinter() {
    private val sb = StringBuilder()
    override fun print(s: String) {
        sb.append(s)
    }

    fun asString(): String = sb.toString()
}