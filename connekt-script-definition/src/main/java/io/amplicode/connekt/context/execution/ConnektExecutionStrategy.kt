/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt.context.execution

import com.moczul.ok2curl.CurlInterceptor
import com.moczul.ok2curl.logger.Logger
import io.amplicode.connekt.ExecutableWithResult
import io.amplicode.connekt.UseCase
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.dsl.RequestBuilder
import io.amplicode.connekt.println
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * A class responsible for [RequestBuilder] execution.
 */
interface RequestExecutionStrategy {
    fun executeRequest(context: ConnektContext, requestBuilder: RequestBuilder): Response
}

/**
 * A class responsible for [UseCase] execution.
 */
interface UseCaseExecutionStrategy {
    fun <T> executeUseCase(context: ConnektContext, useCase: UseCase<T>): T
}

interface ConnektExecutionStrategy : RequestExecutionStrategy, UseCaseExecutionStrategy {
    fun <R> mapRequestExecutable(
        context: ConnektContext,
        requestExecutable: ExecutableWithResult<Response>,
        mapFunction: io.amplicode.connekt.MapFunction<Response, R>
    ): io.amplicode.connekt.MappedRequestHolder<R>
}

/**
 * Makes a real HTTP request according to params described in providing [RequestBuilder] and context
 */
class DefaultExecutionStrategy : ConnektExecutionStrategy {

    override fun executeRequest(context: ConnektContext, requestBuilder: RequestBuilder): Response {
        val request = requestBuilder.build()
        val clientConfigurer = requestBuilder.getClientConfigurer()
        val client = context.clientContext.getClient(clientConfigurer)
        val response: Response = client.newCall(request).execute()
        return response
    }

    override fun <R> mapRequestExecutable(
        context: ConnektContext,
        requestExecutable: ExecutableWithResult<Response>,
        mapFunction: io.amplicode.connekt.MapFunction<Response, R>
    ): io.amplicode.connekt.MappedRequestHolder<R> {
        return _root_ide_package_.io.amplicode.connekt.MappedRequestHolder(context, requestExecutable, mapFunction)
    }

    override fun <T> executeUseCase(context: ConnektContext, useCase: UseCase<T>): T {
        val effectiveUseCaseName = useCase.name ?: "Unnamed"
        context.printer.println("Running useCase: $effectiveUseCaseName")
        val useCaseBuilder = _root_ide_package_.io.amplicode.connekt.UseCaseBuilderImpl(context, this)
        return useCase.perform(useCaseBuilder)
    }
}

/**
 * Does not make a real HTTP request but builds a `curl` command instead
 */
class CurlExecutionStrategy : ConnektExecutionStrategy {

    override fun executeRequest(context: ConnektContext, requestBuilder: RequestBuilder): Response {
        val interceptor = simpleCurlInterceptor { command ->
            context.printer.println(command)
        }
        val executionStrategy = CurlRequestExecutionStrategy(interceptor)
        return executionStrategy.executeRequest(context, requestBuilder)
    }

    override fun <R> mapRequestExecutable(
        context: ConnektContext,
        requestExecutable: ExecutableWithResult<Response>,
        mapFunction: io.amplicode.connekt.MapFunction<Response, R>
    ): io.amplicode.connekt.MappedRequestHolder<R> {
        // Use a fake request holder to prevent a real http call.
        return _root_ide_package_.io.amplicode.connekt.MappedRequestHolder(
            context,
            DummyRequestHolder,
            mapFunction
        )
    }

    override fun <T> executeUseCase(context: ConnektContext, useCase: UseCase<T>): T {
        val useCaseBuilder = _root_ide_package_.io.amplicode.connekt.UseCaseBuilderImpl(
            context,
            CurlRequestExecutionStrategy(
                simpleCurlInterceptor { command ->
                    context.printer.println("$command;")
                }
            )
        )
        return useCase.perform(useCaseBuilder)
    }

    private object DummyRequestHolder : ExecutableWithResult<Response>() {

        override val originalExecutable: Executable<*>
            get() = throw UnsupportedOperationException()

        override fun doExecute() = throw UnsupportedOperationException()
    }
}

private class CurlRequestExecutionStrategy(
    private val curlInterceptor: CurlInterceptor
) : RequestExecutionStrategy {

    override fun executeRequest(context: ConnektContext, requestBuilder: RequestBuilder): Response {
        val request = requestBuilder.build()
        val client = context.clientContext.getClient {
            // Log `curl` command
            addInterceptor(curlInterceptor)

            // Return a fake response to prevent a real http call
            addInterceptor {
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .message("")
                    .code(200)
                    .build()
            }
        }

        return client.newCall(request).execute()
    }
}

private fun simpleCurlInterceptor(doLog: (String) -> Unit) = CurlInterceptor(
    object : Logger {
        override fun log(message: String) = doLog(message)
    }
)
