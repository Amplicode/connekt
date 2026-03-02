@file:Import("second_level_import.connekt.kts")

fun assertBodyEquals(actual: String, expected: String) {
    assert(actual == expected) { "Expected body '$expected' but got '$actual'" }
}

fun buildEchoUrl(host: String, key: String, value: String): String {
    return "$host/echo-query-params?${encodeQueryParam(key, value)}"
}
