package io.amplicode.connekt.dsl

import io.amplicode.connekt.HeaderValue
import java.util.Base64


/**
 * Sets the `Authorization` header using HTTP Basic authentication.
 *
 * The credentials are combined as `username:password`, encoded using Base64, and sent as the value
 * of the `Authorization: Basic <encoded>` header. This is the standard mechanism for HTTP Basic
 * authentication as defined in RFC 7617.
 *
 * @param username The username to include in the Basic authentication credentials.
 * @param password The password to include in the Basic authentication credentials.
 */
fun RequestBuilder.basicAuth(username: String, password: String) {
    val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
    header("Authorization", "Basic $token")
}

/**
 * Sets the `Authorization` header using Bearer token authentication.
 *
 * Adds the `Authorization: Bearer <token>` header to the request. This is commonly used for
 * OAuth 2.0 and other token-based authentication schemes as defined in RFC 6750.
 *
 * @param token The bearer token to include in the `Authorization` header.
 */
fun RequestBuilder.bearerAuth(token: String) {
    header("Authorization", "Bearer $token")
}

/**
 * Sets the `Content-Type` header on the request.
 *
 * Specifies the media type of the request body, allowing the server to correctly interpret the
 * payload. Common values include `application/json`, `application/x-www-form-urlencoded`, and
 * `multipart/form-data`.
 *
 * @param contentType The MIME type to set as the value of the `Content-Type` header
 *   (e.g. `"application/json"`).
 */
fun RequestBuilder.contentType(@HeaderValue("Content-Type") contentType: String) {
    header("Content-Type", contentType)
}

/**
 * Sets the `Accept` header on the request.
 *
 * Informs the server which media types the client is able to understand in the response. The server
 * will use this to select an appropriate response format through content negotiation.
 *
 * @param contentType The MIME type to set as the value of the `Accept` header
 *   (e.g. `"application/json"`).
 */
fun RequestBuilder.accept(@HeaderValue("Accept") contentType: String) {
    header("Accept", contentType)
}
