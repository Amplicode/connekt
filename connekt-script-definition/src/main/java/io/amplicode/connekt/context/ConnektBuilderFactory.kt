package io.amplicode.connekt.context

import io.amplicode.connekt.ConnektAuthExtensionsImpl
import io.amplicode.connekt.ConnektBuilderImpl
import io.amplicode.connekt.context.execution.ExecutionContext
import io.amplicode.connekt.dsl.AuthExtensions
import io.amplicode.connekt.dsl.ConnektBuilder

interface ConnektBuilderFactory {
    fun createConnektBuilder(): ConnektBuilder
    fun createForImportedScript(): ConnektBuilder
}

class ConnektBuilderFactoryImpl(
    private val context: ConnektContext,
    private val importAuthExtensionsFactory: (ConnektContext) -> AuthExtensions = ::ConnektAuthExtensionsImpl,
) : ConnektBuilderFactory {
    override fun createConnektBuilder(): ConnektBuilder = ConnektBuilderImpl(context)

    override fun createForImportedScript(): ConnektBuilder {
        val importContext = context.fork {
            register(ExecutionContext::class) { ExecutionContext() }
            register(AuthExtensions::class) { importAuthExtensionsFactory(this) }
        }
        return ConnektBuilderImpl(importContext)
    }
}
