@file:Import("transitive_helper.connekt.kts")

val host: String by env

// Uses urlSuffix directly from transitive_const (level 3, not level 2)
GET("$host/$urlSuffix") then {
    val body = body!!.string()
    assert(body == "foo") { "Expected 'foo' but got '$body'" }
}