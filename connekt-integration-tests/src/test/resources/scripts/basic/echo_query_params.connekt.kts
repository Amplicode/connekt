val host: String by env

GET("$host/echo-query-params") {
    queryParam("key", "value")
    queryParam("count", 42)
} then {
    val responseText = body!!.string()
    assert(responseText.contains("key")) { "Expected 'key' in response, got: $responseText" }
    assert(responseText.contains("value")) { "Expected 'value' in response, got: $responseText" }
}
