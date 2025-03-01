/*
 * Copyright (c) Haulmont 2024. All Rights Reserved.
 * Use is subject to license terms.
 */

package io.amplicode.connekt

import io.amplicode.connekt.ConnektContext.Companion
import io.amplicode.connekt.dsl.BaseRequestBuilder
import okhttp3.Response

class ConnektRequest(
    internal val context: ConnektContext,
    private val requestBuilderSupplier: () -> BaseRequestBuilder
) {

    fun execute(): Response {
        val requestBuilder = requestBuilderSupplier()
        if (!requestBuilder.requestHints.noCookies) {
            context.cookies.forEach<String, String> {
                requestBuilder.header("Cookie", it)
            }
        }
        val request = requestBuilder.build()
        val client = context.getClient(
            Companion.ClientConfig(
                !requestBuilder.requestHints.noCookies,
                !requestBuilder.requestHints.noRedirect,
                requestBuilder.requestHints.http2
            )
        )
        val response: Response = client
            .newCall(request)
            .execute()
        return response
    }
}