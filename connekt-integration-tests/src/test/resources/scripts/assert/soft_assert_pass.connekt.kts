val host: String by env

GET("$host/foo") then {
    val text = body!!.string()
    assertSoftly {
        assert(text == "foo")
        assert(text.length == 3)
        assert(text.contains("fo"))
    }
}
