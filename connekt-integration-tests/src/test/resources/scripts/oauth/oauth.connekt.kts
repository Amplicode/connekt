val host: String by env

val token by oauth(
    "$host/oauth/auth",
    "test-client",
    null,
    "openid",
    "$host/oauth/token",
    "http://localhost:8080/callback"
)

GET("$host/foo") {
    bearerAuth(token.accessToken)
}
