@file:Import("helper_utils.connekt.kts")

val host: String by env

GET("$host/foo") then {
    assertBodyEquals(body!!.string(), "foo")
}

GET(buildEchoUrl(host, "ping", "pong")) then {
    val responseText = body!!.string()
    assert(responseText.contains("ping")) { "Expected 'ping' in echo response, got: $responseText" }
}
