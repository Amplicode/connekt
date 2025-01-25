val baseUrl: String by env
val clientSecret: String by env

flow {
    val accessToken by POST("http://localhost:9081/realms/conferences/protocol/openid-connect/token") {
        formData {
            field("grant_type", "password")
            field("client_id", "default")
            field("client_secret", clientSecret)
            field("username", "admin")
            field("password", "admin")
        }

        noCookies()
    } then  {
        jsonPath().read<String>("access_token")
    }

    val createdConferenceId by POST("$baseUrl/rest/conference") {
        headers(
            HttpHeaders.CONTENT_TYPE to "application/json",
            "Authorization" to "Bearer $accessToken"
        )
        body(
            """
        {
            "name": "Joker",
            "description": "The best Java Conference",
            "beginDateTime": "2024-10-09T15:00:00",
            "endDateTime": "2024-10-09T15:45:00"
        }
        """.trimIndent()
        )
    } then  {
        jsonPath().readInt("id")
    }

    data class Conference(
        val id: Int,
        val name: String,
        val description: String,
    )

    val conferences by GET("$baseUrl/rest/conference") then  {
        jsonPath().readList("content", Conference::class.java)
    }

    val ids: List<Int> = conferences.map { it.id }

    assert(createdConferenceId in ids)
}
