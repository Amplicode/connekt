@file:Import("auth_config.connekt.kts")

val host: String by env


GET("$host/protected") {  // request 0
    bearerAuth(token.accessToken)
} then {
    assert(code == 200) { "Request 0: expected 200 from /protected but got $code" }
}

GET("$host/protected") then {  // request 1
    assert(code == 401) { "Request 1: expected 401 from /protected but got $code" }
}
