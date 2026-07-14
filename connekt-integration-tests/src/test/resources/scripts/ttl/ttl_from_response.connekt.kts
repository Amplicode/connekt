import kotlin.time.Duration.Companion.seconds

val host: String by env
val expiresIn: Long by env

val token by POST("$host/token") {
    queryParam("expires_in", expiresIn)
    ttl { decode<Long>("$.expires_in").seconds }
} then {
    decode<String>("$.access_token")
}

val echoed by GET("$host/echo-query-params") {
    queryParam("token", token)
}
