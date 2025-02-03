package io.amplicode.connekt.dsl

import okhttp3.MediaType

internal sealed interface RequestBody

internal class ByteArrayBody(val body: ByteArray) : RequestBody
internal class FormDataBody(val body: List<Pair<String, String>>) : RequestBody
internal class MultipartBody(val parts: List<Part>, val boundary: String) : RequestBody {
    class Part(val body: RequestBody, val headers: List<Pair<String, Any>> = listOf(), val contentType: MediaType?)
}

internal class StringBody(val body: String) : RequestBody


