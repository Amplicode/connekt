import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val host: String by env

val fixed by POST("$host/auth/token") {
    ttl(5.minutes)
} then {
    decode<String>("$.access_token")
}

val fromBody by POST("$host/auth/token") {
    ttl { decode<Long>("$.expires_in").seconds }
} then {
    decode<String>("$.access_token")
}

val fromHeader by POST("$host/auth/token") {
    ttl { header("X-Token-Expires-In")!!.toLong().seconds }
} then {
    decode<String>("$.access_token")
}

val useCaseValue by useCase("token via useCase") {
    ttl(5.minutes)
    val response by POST("$host/auth/token")
    response.decode<String>("$.access_token")
}
