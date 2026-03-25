val host: String by env

GET("$host/foo") then {
    val text = body!!.string()
    assertSoftly {
        assert(text == "bar")
        assert(text.length == 10)
        assert(text == "baz")
    }
}
