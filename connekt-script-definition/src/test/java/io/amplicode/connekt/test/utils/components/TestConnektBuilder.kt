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
    configureContext: (ConnektContextImpl) -> Unit = { }
): ConnektContext {
    val context = createConnektContext(
        storage,
        NoopEnvironmentStore,
        NoopCookiesContext,
        ClientContextImpl(
            ConnektInterceptor(
                printer,
                responseStorageDir
            )
        ),
        printer
    )
    configureContext(context)
    return context
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