/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import com.moczul.ok2curl.CurlInterceptor
import com.moczul.ok2curl.logger.Logger
import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.dsl.RequestBuilder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * A class responsible for [RequestBuilder] execution.
 */
interface RequestExecutionStrategy {
    fun executeRequest(requestBuilder: RequestBuilder): Response
}

/**
 * A class responsible for [UseCase] execution.
 */
interface UseCaseExecutionStrategy {
    fun <T> executeUseCase(useCase: UseCase<T>): T
}

interface ConnektExecutionStrategy : RequestExecutionStrategy, UseCaseExecutionStrategy {
    fun isMappingAllowed(): Boolean
}

/**
 * Makes a real HTTP request according to params described in providing [RequestBuilder] and context
 */
class DefaultExecutionStrategy(private val context: ConnektContext) :
    ConnektExecutionStrategy {

    override fun executeRequest(requestBuilder: RequestBuilder): Response {
        val request = requestBuilder.build()
        val clientConfigurer = requestBuilder.getClientConfigurer()
        val client = context.clientContext.getClient(clientConfigurer)
        val response: Response = client.newCall(request).execute()
        return response
    }

    override fun isMappingAllowed() = true

    override fun <T> executeUseCase(useCase: UseCase<T>) : T {
        val effectiveUseCaseName = useCase.name ?: "Unnamed"
        context.printer.println("Running useCase: $effectiveUseCaseName")
        val useCaseBuilder = UseCaseBuilderImpl(context, this)
        return useCase.perform(useCaseBuilder)
    }
}

/**
 * Does not make a real HTTP request but builds a `curl` command instead
 */
class CurlExecutionStrategy(private val context: ConnektContext) :
    ConnektExecutionStrategy {

    override fun executeRequest(requestBuilder: RequestBuilder): Response {
        return CurlRequestExecutionStrategy(
            context,
            simpleCurlInterceptor { command -> context.printer.println(command) }
        ).executeRequest(requestBuilder)
    }

    override fun isMappingAllowed() = false

    override fun <T> executeUseCase(useCase: UseCase<T>): T {
        val useCaseBuilder = UseCaseBuilderImpl(
            context,
            CurlRequestExecutionStrategy(
                context,
                simpleCurlInterceptor { command ->
                    context.printer.println("$command;")
                }
            )
        )
        return useCase.perform(useCaseBuilder)
    }
}

private class CurlRequestExecutionStrategy(
    private val context: ConnektContext,
    private val curlInterceptor: CurlInterceptor
) : RequestExecutionStrategy {

    override fun executeRequest(requestBuilder: RequestBuilder): Response {
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
