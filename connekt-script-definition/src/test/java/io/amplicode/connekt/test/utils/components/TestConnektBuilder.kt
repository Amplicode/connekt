package io.amplicode.connekt.test.utils.components

import io.amplicode.connekt.BaseNonColorPrinter
import io.amplicode.connekt.ConnektInterceptor
import io.amplicode.connekt.Printer
import io.amplicode.connekt.SystemOutPrinter
import io.amplicode.connekt.context.*
import io.amplicode.connekt.context.InMemoryPersistenceStore
import java.nio.file.Path

fun testConnektContext(
    persistenceStore: PersistenceStore = InMemoryPersistenceStore(),
    environmentStore: EnvironmentStore = NoopEnvironmentStore,
    cookiesContextFactory: (ConnektLifeCycleCallbacks) -> CookiesContext = { NoopCookiesContext },
    printer: Printer = TestPrinter(),
    responseStorageDir: Path? = null
): ConnektContext {
    val lifeCycleCallbacksImpl = ConnektLifeCycleCallbacksImpl()
    val context = createConnektContext(
        persistenceStore,
        environmentStore,
        cookiesContextFactory(lifeCycleCallbacksImpl),
        ClientContextImpl(
            ConnektInterceptor(
                printer,
                responseStorageDir
            )
        ),
        printer
    ).onClose {
        lifeCycleCallbacksImpl.fireClosed()
    }

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