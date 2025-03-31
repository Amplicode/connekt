/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import io.amplicode.connekt.context.ConnektContext
import io.amplicode.connekt.dsl.RequestBuilder
import okhttp3.Response

class ConnektRequest(
    internal val context: ConnektContext,
    private val requestBuilderSupplier: () -> RequestBuilder
) {
    fun execute(): Response {
        val requestBuilder = requestBuilderSupplier()
        val request = requestBuilder.build()
        val clientConfigurer = requestBuilder.getClientConfigurer()
        val client = context.clientContext.getClient(clientConfigurer)
        val response: Response = client
            .newCall(request)
            .execute()
        return response
    }
}