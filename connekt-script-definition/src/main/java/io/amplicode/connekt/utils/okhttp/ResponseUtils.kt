package io.amplicode.connekt.utils.okhttp

import io.amplicode.connekt.utils.ContentDisposition
import okhttp3.Response

fun Response.contentDisposition(): ContentDisposition? {
    val textValue = headers["Content-Disposition"] ?: return null
    return ContentDisposition.parseFromHeader(textValue)
}
