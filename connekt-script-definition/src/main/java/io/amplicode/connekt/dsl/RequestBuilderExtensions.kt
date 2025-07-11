package io.amplicode.connekt.dsl

import io.amplicode.connekt.HeaderValue
import java.util.Base64


fun RequestBuilder.basicAuth(username: String, password: String) {
    val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
    header("Authorization", "Basic $token")
}

fun RequestBuilder.bearerAuth(token: String) {
    header("Authorization", "Bearer $token")
}

fun RequestBuilder.contentType(@HeaderValue("Content-Type") contentType: String) {
    header("Content-Type", contentType)
}

fun RequestBuilder.accept(@HeaderValue("Accept") contentType: String) {
    header("Accept", contentType)
}
