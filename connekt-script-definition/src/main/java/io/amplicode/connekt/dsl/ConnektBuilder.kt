/*
 * Copyright (c) Haulmont 2025. All Rights Reserved.
 * Use is subject to license terms.
 */

@file:Suppress("FunctionName")

package io.amplicode.connekt.dsl

import io.amplicode.connekt.*
import io.amplicode.connekt.context.ClientConfigurer
import io.amplicode.connekt.context.DelegateProvider
import io.amplicode.connekt.context.EnvironmentStore
import io.amplicode.connekt.context.VariablesStore
import kotlin.reflect.KProperty

@ConnektDsl
interface ConnektBuilder : RequestRegistrator<RequestHolder>, JsonPathExtensionsProvider {
    val env: EnvironmentStore
    val vars: VariablesStore

    fun <T> variable(): DelegateProvider<T>
    fun configureClient(configure: ClientConfigurer)

    @RequestBuilderCall
    fun useCase(
        name: String? = null,
        runUseCase: UseCaseBuilder.() -> Unit = {}
    )

    operator fun <R> ConnektRequestExecutable<R>.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<R>
}

interface ValueDelegate<T> {
    operator fun getValue(
        @Suppress("unused") thisRef: Nothing?,
        @Suppress("unused") property: KProperty<*>
    ): T

    operator fun getValue(
        @Suppress("unused") thisRef: Any?,
        @Suppress("unused") property: KProperty<*>
    ): T
}