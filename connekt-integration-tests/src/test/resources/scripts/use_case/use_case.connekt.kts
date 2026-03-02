val host: String by env

useCase {
    GET("$host/foo") then {
        assert(body!!.string() == "foo") { "Expected 'foo'" }
    }

    GET("$host/bar") then {
        assert(body!!.string() == "bar") { "Expected 'bar'" }
    }
}
