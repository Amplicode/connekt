/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import io.amplicode.connekt.dsl.BaseRequestBuilder
import io.amplicode.connekt.dsl.toClientConfigurer
import okhttp3.Response

class ConnektRequest(
    internal val context: ConnektContext,
    private val requestBuilderSupplier: () -> BaseRequestBuilder
) {
    fun execute(): Response {
        val requestBuilder = requestBuilderSupplier()
        val request = requestBuilder.build()
        if (!requestBuilder.requestHints.noCookies) {
            context.cookies.forEach<String, String> {
                requestBuilder.header("Cookie", it)
            }
        }
        val clientConfigurer = requestBuilder.requestHints.toClientConfigurer()
        val client = context.getClient(clientConfigurer)
        val response: Response = client
            .newCall(request)
            .execute()
        return response
    }
}