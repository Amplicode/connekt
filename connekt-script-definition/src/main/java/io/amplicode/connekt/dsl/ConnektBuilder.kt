/*
 * Copyright (c) Haulmont 2025. All Rights Reserved.
 * Use is subject to license terms.
 */

@file:Suppress("FunctionName")

package io.amplicode.connekt.dsl

import io.amplicode.connekt.*
import io.amplicode.connekt.context.ClientConfigurer
import io.amplicode.connekt.context.EnvironmentStore
import io.amplicode.connekt.context.StoredVariableDelegate
import io.amplicode.connekt.context.VariablesStore
import kotlin.reflect.KProperty

@ConnektDsl
interface ConnektBuilder :
    RequestRegistrator<RequestHolder>,
    JsonPathExtensionsProvider,
    AuthExtensions {

    val env: EnvironmentStore
    val vars: VariablesStore

    fun variable(): StoredVariableDelegate

    fun configureClient(configure: ClientConfigurer)

    @RequestBuilderCall
    fun <T> useCase(
        @RequestName name: String? = null,
        runUseCase: UseCaseBuilder.() -> T
    ): UseCaseExecutable<T>

    operator fun <R> ExecutableWithResult<R>.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<R>

    operator fun <R> UseCaseExecutable<R>.provideDelegate(
        @Suppress("unused")
        receiver: Any?,
        prop: KProperty<*>
    ): ValueDelegate<R>
}
