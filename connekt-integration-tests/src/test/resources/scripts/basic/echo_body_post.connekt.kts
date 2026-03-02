val host: String by env

val requestBody = """{"message": "hello from connekt"}"""

POST("$host/echo-body") {
    header("Content-Type", "application/json")
    body(requestBody)
} then {
    val responseText = body!!.string()
    assert(responseText == requestBody) { "Expected echoed body '$requestBody' but got '$responseText'" }
}
