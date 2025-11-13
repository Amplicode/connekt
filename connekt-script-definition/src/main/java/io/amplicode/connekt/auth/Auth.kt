package io.amplicode.connekt.auth

data class Auth(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpirationTs: Long,
    val refreshTokenExpirationTs: Long
)