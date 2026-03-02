package io.amplicode.connekt.test.utils.components

import io.amplicode.connekt.BaseNonColorPrinter
import io.amplicode.connekt.ConnektInterceptor
import io.amplicode.connekt.Printer
import io.amplicode.connekt.SystemOutPrinter
import io.amplicode.connekt.context.*
import io.amplicode.connekt.context.persistence.InMemoryStorage
import io.amplicode.connekt.context.persistence.Storage
import java.nio.file.Path

fun testConnektContext(
    storage: Storage = InMemoryStorage(),
    printer: Printer = TestPrinter(),
    responseStorageDir: Path? = null,
    environmentStore: EnvironmentStore = NoopEnvironmentStore,
    cookiesContext: CookiesContext = NoopCookiesContext,
    configure: ConnektContext.() -> Unit = {},
): ConnektContext = createConnektContext(
    storage,
    environmentStore,
    cookiesContext,
    ClientContextImpl(ConnektInterceptor(printer, responseStorageDir)),
    printer,
).apply(configure)

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