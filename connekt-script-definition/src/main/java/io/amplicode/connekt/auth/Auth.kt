package io.amplicode.connekt.auth

class Auth(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpirationTs: Long,
    val refreshTokenExpirationTs: Long
)