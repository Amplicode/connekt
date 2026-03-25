val host: String by env

GET("$host/foo") then {
    val text = body!!.string()
    assert(text == "not_foo") { "Expected 'not_foo' but got '$text'" }
}
