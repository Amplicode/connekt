package io.amplicode.connekt.context

import io.amplicode.connekt.ConnektBuilderImpl
import io.amplicode.connekt.dsl.ConnektBuilder

interface ConnektBuilderFactory {
    fun createConnektBuilder(): ConnektBuilder
}

class ConnektBuilderFactoryImpl(private val context: ConnektContext) : ConnektBuilderFactory {
    override fun createConnektBuilder(): ConnektBuilder = ConnektBuilderImpl(context)
}