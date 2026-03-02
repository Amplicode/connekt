val host: String by env

val fooBody by GET("$host/foo") then { body!!.string() }

GET("$host/echo-query-params") {
    queryParam("response", fooBody)
} then {
    val responseText = body!!.string()
    assert(responseText.contains("response")) { "Expected 'response' key in echo params, got: $responseText" }
}
