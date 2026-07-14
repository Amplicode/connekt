val host: String by env
val counterId: String by env

val token by POST("$host/counter/$counterId/inc") then {
    body!!.string()
}

val echoed by GET("$host/echo-query-params") {
    queryParam("token", token)
}
