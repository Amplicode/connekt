import com.jayway.jsonpath.TypeRef
import org.assertj.core.api.Assertions
import java.io.File

val baseUrl: String by env
val clientSecret: String by env

val accessToken: String by POST("http://localhost:9081/realms/conferences/protocol/openid-connect/token") {
    contentType("application/x-www-form-urlencoded")

    formData {
        field("grant_type", "password")
        field("client_id", "default")
        field("client_secret", clientSecret)
        field("username", "admin")
        field("password", "admin")
    }

    noCookies()
    noRedirect()
    http2()
} then {
    jsonPath().readString("access_token")
}

// тип - результат запроса (then)
val createdConferenceId by POST("$baseUrl/rest/conference") {
    contentType("application/json")
    bearerAuth(accessToken)

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

    noCookies()
} then {
    jsonPath().readInt("id")
}

PUT("$baseUrl/rest/conference/57") {
    multipart {
        part(name = "data", contentType = "application/json") {
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
        }

        file("file", "http.connekt.kts", File("src/http.connekt.kts"))
    }

    noCookies()
}

//get unauthorized
val `get unauthorized` by GET("$baseUrl/rest/conference/{id}") {
    pathParams("id", createdConferenceId)
} then {
    Assertions.assertThat(code).isEqualTo(200)
}

val `list all conferences` by GET("$baseUrl/conference") {
    bearerAuth(accessToken)

    noRedirect()
    noCookies()
} then {
    Assertions.assertThat(code).isEqualTo(200)
}

val `update conference` by PUT("$baseUrl/conference/2") {
    contentType("application/json")
    body(
        """
            {
                "personIds": [1]
            }
        """.trimIndent()
    )
}

val createdPersonId by POST("$baseUrl/person") {
    contentType("application/json")
    body(
        """
        {
            "firstName": "Alex",
            "lastName": "Shustanov",
            "organization": "Haulmont"
        }
    """.trimIndent()
    )
} then {
    jsonPath().read<Int>("id")
}

val conferencePersons by GET(
    "$baseUrl/conference/2"
) then {
    jsonPath().read("personIds", object : TypeRef<List<Long>>() {})
}

PUT("$baseUrl/conference/2") {
    headers(HttpHeaders.CONTENT_TYPE to "application/json")
    body(
        """
            {
                "personIds": [${(conferencePersons + createdPersonId).joinToString()}]
            }
        """.trimIndent()
    )
}

//
POST("$baseUrl/rest/conference") {
    contentType("application/json")
    bearerAuth(accessToken)

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
} then {
    jsonPath().read<Int>("id")
}

scenario(name = "dummy test") {
    data class Conference(val id: Int)

    val createdConferenceId: Int by POST("$baseUrl/rest/conference") {
        contentType("application/json")
        bearerAuth(accessToken)

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

        http2()
    } then {
        jsonPath().read("id")
    }

    for (id in 1..10) {
        val conference by GET("$baseUrl/rest/conference/{id}") {
            pathParams("id", id)

            contentType("application/json")
            bearerAuth(accessToken)
        } then {
            jsonPath().read("$", Conference::class.java)
        }

        Assertions.assertThat(conference.id).isEqualTo(id)
    }
}
