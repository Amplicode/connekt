val host: String by env

POST("$host/echo-form-params") {
    formData {
        field("id", "1")
        field("name", "connekt")
        field("active", "true")
    }
} then {
    val params = decode<Map<String, List<String>>>()
    assert(params["id"] == listOf("1")) { "Expected id=[1] but got: ${params["id"]}" }
    assert(params["name"] == listOf("connekt")) { "Expected name=[connekt] but got: ${params["name"]}" }
}
