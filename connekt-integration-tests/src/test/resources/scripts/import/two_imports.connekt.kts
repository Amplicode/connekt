@file:Import("helper_utils.connekt.kts")
@file:Import("extra_assert_utils.connekt.kts")

val host: String by env

// assertBodyEquals is from helper_utils (which itself imports second_level_import)
GET("$host/foo") then {
    assertBodyEquals(body!!.string(), "foo")
}

// assertContains is from extra_assert_utils
GET(buildEchoUrl(host, "greeting", "hello")) then {
    assertContains(body!!.string(), "greeting")
}
