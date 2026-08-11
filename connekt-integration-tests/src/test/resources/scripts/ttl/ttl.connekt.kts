import kotlin.time.Duration.Companion.milliseconds

val host: String by env
val counterId: String by env
val ttlMillis: Long by env

val token by POST("$host/counter/$counterId/inc") {
    ttl(ttlMillis.milliseconds)
} then {
    body!!.string()
}

val echoed by GET("$host/echo-query-params") {
    queryParam("token", token)
}
