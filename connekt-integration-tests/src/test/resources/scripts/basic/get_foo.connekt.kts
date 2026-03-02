val host: String by env

GET("$host/foo") then {
    assert(body!!.string() == "foo") { "Expected 'foo'" }
}
