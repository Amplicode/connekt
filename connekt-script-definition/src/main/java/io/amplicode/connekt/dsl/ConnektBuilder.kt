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

/**
 * Root DSL receiver available in every Connekt script.
 *
 * Every `.connekt.kts` script is executed with an implicit `this` of type [ConnektBuilder]. The
 * interface exposes all top-level DSL capabilities: access to environment variables and persistent
 * storage, HTTP request registration, use-case grouping, and global OkHttp client configuration.
 *
 * In addition to the members declared here, [ConnektBuilder] inherits HTTP request factory
 * functions from [RequestRegistrator] (e.g. `GET(...)`, `POST(...)`), JSON-path utilities from
 * [JsonPathExtensionsProvider], and authentication helpers from [AuthExtensions].
 *
 * Typical script structure:
 * ```kotlin
 * val token by env  // read an environment variable
 *
 * configureClient {
 *     insecure()    // skip SSL verification for local development
 * }
 *
 * val pets by GET("$baseUrl/pets")
 *
 * val enriched by useCase("Fetch and enrich") {
 *     val response by GET("$baseUrl/pets")
 *     response.decode<List<Pet>>("$.pets")
 * }
 * ```
 */
@ConnektDsl
interface ConnektBuilder :
    RequestRegistrator<RequestHolder>,
    JsonPathExtensionsProvider,
    AuthExtensions {

    /**
     * Read-only access to environment variables defined in the env file (e.g. `connekt.env.json`).
     *
     * Variables are retrieved via Kotlin property delegation. The property name is used as the
     * lookup key, and the value is automatically converted to the declared property type:
     *
     * ```kotlin
     * val baseUrl: String by env
     * val port: Int by env
     * ```
     *
     * Throws [io.amplicode.connekt.context.NoEnvironmentPropertyException] if the key is not
     * present in the active environment.
     */
    val env: EnvironmentStore

    /**
     * Persistent variable storage that survives across script runs.
     *
     * Use [vars] to read and write named values that are persisted between individual executions.
     * Values survive across script runs and are restored automatically from the underlying storage
     * on the next execution.
     */
    val vars: VariablesStore

    /**
     * Creates a [StoredVariableDelegate] for direct read-write property delegation backed by
     * persistent storage.
     *
     * This is a convenience shortcut for [VariablesStore.variable] and supports the standard
     * Kotlin `var` delegation syntax. The property name is used as the storage key, and the value
     * type is inferred at compile time:
     *
     * ```kotlin
     * var executionCount by variable<Int>()
     * executionCount = (executionCount ?: 0) + 1
     * println("Run #$executionCount")
     * ```
     *
     * @return A [StoredVariableDelegate] suitable for use with `var` property delegation.
     */
    fun variable(): StoredVariableDelegate

    /**
     * Applies global configuration to the OkHttp client used for all HTTP requests in the script.
     *
     * The [configure] lambda receives an [okhttp3.OkHttpClient.Builder] and can be used to
     * customize SSL settings, timeouts, interceptors, or any other OkHttp option. The configuration
     * is applied once before any requests are executed and affects every request in the script
     * unless a request overrides it locally.
     *
     * Common use-cases include trusting self-signed certificates or disabling SSL verification in
     * local development environments:
     *
     * ```kotlin
     * configureClient {
     *     addX509Certificate(File("certs/my-ca.crt"))
     * }
     *
     * // Or, for local development only:
     * configureClient {
     *     insecure()
     * }
     * ```
     *
     * @param configure A lambda applied to [okhttp3.OkHttpClient.Builder] to configure the client.
     * @see okhttp3.OkHttpClient.Builder
     */
    fun configureClient(configure: ClientConfigurer)

    /**
     * Groups multiple HTTP requests into a named unit that can be executed together and delegated
     * to a variable.
     *
     * The [runUseCase] lambda is evaluated with a [UseCaseBuilder] receiver, which provides the
     * same HTTP request helpers as the top-level script scope. The return value of the lambda
     * becomes the value of the resulting [UseCaseExecutable], which can be delegated to a `val`
     * using the `by` keyword:
     *
     * ```kotlin
     * val petNames by useCase("Load pet names") {
     *     val response by GET("$baseUrl/pets")
     *     response.decode<List<String>>("$.name")
     * }
     * ```
     *
     * @param name An optional human-readable label for the use case shown in logs and tooling.
     *   Defaults to `null`, in which case no label is displayed.
     * @param runUseCase The lambda executed inside the use-case scope. Its return value becomes
     *   the result of the [UseCaseExecutable].
     * @return A [UseCaseExecutable] that can be delegated to a variable or executed directly.
     * @see UseCaseBuilder
     */
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
