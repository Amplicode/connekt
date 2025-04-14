package io.amplicode.connekt.test.utils.components

import io.amplicode.connekt.BaseNonColorPrinter
import io.amplicode.connekt.Printer
import io.amplicode.connekt.SystemOutPrinter
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.context.ConnektLifeCycleCallbacks
import io.amplicode.connekt.context.ConnektLifeCycleCallbacksImpl
import io.amplicode.connekt.context.CookiesContext
import io.amplicode.connekt.context.createConnektContext
import io.amplicode.connekt.context.EnvironmentStore
import io.amplicode.connekt.context.NoopEnvironmentStore
import io.amplicode.connekt.context.NoopCookiesContext
import io.amplicode.connekt.context.onClose
import org.mapdb.DB
import org.mapdb.DBMaker

fun testConnektContext(
    db: DB = DBMaker.memoryDB().make(),
    environmentStore: EnvironmentStore = NoopEnvironmentStore,
    cookiesContextFactory: (ConnektLifeCycleCallbacks) -> CookiesContext = { NoopCookiesContext },
    printer: Printer = TestPrinter()
): ConnektContext {
    val lifeCycleCallbacksImpl = ConnektLifeCycleCallbacksImpl()
    return createConnektContext(
        db,
        environmentStore,
        cookiesContextFactory(lifeCycleCallbacksImpl),
        printer
    ).onClose {
        lifeCycleCallbacksImpl.fireClosed()
    }
}

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