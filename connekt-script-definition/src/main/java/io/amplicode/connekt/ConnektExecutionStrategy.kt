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
interface ConnektExecutionStrategy {
    fun execute(requestBuilder: RequestBuilder): Response
    fun isMappingAllowed(): Boolean
}

/**
 * Makes a real HTTP request according to params described in providing [RequestBuilder] and context
 */
class DefaultExecutionStrategy(private val context: ConnektContext) :
    ConnektExecutionStrategy {

    override fun execute(requestBuilder: RequestBuilder): Response {
        val request = requestBuilder.build()
        val clientConfigurer = requestBuilder.getClientConfigurer()
        val client = context.clientContext.getClient(clientConfigurer)
        val response: Response = client.newCall(request).execute()
        return response
    }

    override fun isMappingAllowed() = true
}

/**
 * Does not make a real HTTP request but builds a `curl` command instead
 */
class CurlExecutionStrategy(private val context: ConnektContext) :
    ConnektExecutionStrategy {

    override fun execute(requestBuilder: RequestBuilder): Response {
        val request = requestBuilder.build()
        val client = context.clientContext.getClient {
            // Log `curl` command
            addInterceptor(
                CurlInterceptor(
                object : Logger {
                    override fun log(message: String) {
                        context.printer.println(message)
                    }
                }
            ))

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

    override fun isMappingAllowed() = false
}