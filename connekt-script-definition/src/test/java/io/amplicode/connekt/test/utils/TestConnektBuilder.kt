package io.amplicode.connekt.test.utils

import io.amplicode.connekt.ConnektBuilder
import io.amplicode.connekt.ConnektContext
import io.amplicode.connekt.EnvironmentStore
import io.amplicode.connekt.NoOpEnvironmentStore
import io.amplicode.connekt.VariablesStore
import io.amplicode.connekt.console.BaseNonColorPrinter
import io.amplicode.connekt.console.Printer
import io.amplicode.connekt.console.SystemOutPrinter
import io.amplicode.connekt.ConnektBuilderImpl
import io.amplicode.connekt.dsl.ConnektBuilder
import org.mapdb.DB
import org.mapdb.DBMaker

fun createConnektBuilder(
    db: DB = DBMaker.memoryDB().make(),
    environmentStore: EnvironmentStore = NoOpEnvironmentStore,
): ConnektBuilder {
    val connektContext = ConnektContext(
        db,
        environmentStore,
        VariablesStore(db),
        TestPrinter()
    )
    val connektBuilder = ConnektBuilder(connektContext)
    return connektBuilder
}

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